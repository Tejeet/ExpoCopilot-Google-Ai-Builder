package com.tejeet.carddrop.ocr

import android.content.Context
import com.tejeet.carddrop.ai.CardAiVerifier
import com.tejeet.carddrop.ai.AiModelFiles
import com.tejeet.carddrop.data.ContactStore
import com.tejeet.carddrop.data.SettingsStore
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.OcrStatus
import com.tejeet.carddrop.util.ImageUtils
import com.tejeet.carddrop.util.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads card photos (front, and back if there is one) in the background so the user can
 * keep scanning, then - if the on-device model is downloaded - has Gemma double-check the
 * result. Uses a process-wide scope rather than a Compose one: leaving a screen must not
 * cancel a scan that's in progress. Unfinished work is picked up by resumePending() on launch.
 */
object CardScanProcessor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inFlight = mutableSetOf<String>()
    private val aiQueued = mutableSetOf<String>()
    /** AI checks run one after another; queued cards already show "AI analyzing" and stay locked until theirs is done. */
    private val aiMutex = Mutex()

    /**
     * Tags the card with where it was scanned (geo_lat / geo_lng, plus the city and country they
     * map to). Silently does nothing when location is switched off, not permitted, or unavailable.
     */
    fun attachLocation(contactId: String, store: ContactStore, context: Context) {
        val appContext = context.applicationContext
        if (!SettingsStore(appContext).attachLocation || !LocationProvider.hasPermission(appContext)) return
        scope.launch {
            val fix = runCatching { LocationProvider.current(appContext) }.getOrNull() ?: return@launch
            val latest = store.find(contactId) ?: return@launch
            if (latest.geoLat != null) return@launch
            store.update(
                latest.copy(
                    geoLat = fix.latitude,
                    geoLng = fix.longitude,
                    city = latest.city.ifBlank { fix.city },
                    country = latest.country.ifBlank { fix.country }
                )
            )
        }
    }

    fun resumePending(store: ContactStore, context: Context) {
        // Load the AI model now, so the first card of the day doesn't also wait ~10s for it to load.
        val appContext = context.applicationContext
        if (SettingsStore(appContext).aiAutoCheck && AiModelFiles.selectedIsReady(appContext)) {
            scope.launch { CardAiVerifier.get(appContext).warmUp() }
        }
        store.contacts.forEach { card ->
            when {
                card.ocrStatus == OcrStatus.PROCESSING -> process(card.id, store, context)
                card.ocrStatus == OcrStatus.DONE && !card.reviewed && card.aiStatus == AiStatus.NONE ->
                    queueAiCheck(card.id, store, context)
            }
        }
    }

    fun process(contactId: String, store: ContactStore, context: Context) {
        if (!inFlight.add(contactId)) return
        scope.launch {
            try {
                val contact = store.find(contactId) ?: return@launch
                // Front and back are read at the same time.
                val (front, back) = coroutineScope {
                    val frontSide = async { readSide(contact.photoPath) }
                    val backSide = async { readSide(contact.backPhotoPath) }
                    frontSide.await() to backSide.await()
                }

                // Re-read: the card may have been deleted or edited while OCR ran.
                val latest = store.find(contactId) ?: return@launch
                if (front == null && back == null) {
                    store.update(latest.copy(ocrStatus = OcrStatus.FAILED, ocrConfidence = 0))
                } else {
                    val merged = merge(latest, front, back)
                    store.update(merged.copy(ocrConfidence = confidenceOf(merged, aiChecked = false)))
                    queueAiCheck(contactId, store, context)
                }
            } catch (e: Exception) {
                store.find(contactId)?.let { store.update(it.copy(ocrStatus = OcrStatus.FAILED)) }
            } finally {
                inFlight.remove(contactId)
            }
        }
    }

    private fun queueAiCheck(contactId: String, store: ContactStore, context: Context) {
        val appContext = context.applicationContext
        if (!SettingsStore(appContext).aiAutoCheck || !AiModelFiles.selectedIsReady(appContext)) return
        if (!aiQueued.add(contactId)) return
        // Show "AI analyzing" (and keep Save locked) from the moment the card is queued, not only while it runs.
        store.find(contactId)
            ?.takeIf { it.aiStatus != AiStatus.CHECKING }
            ?.let { store.update(it.copy(aiStatus = AiStatus.CHECKING)) }
        scope.launch {
            try {
                aiMutex.withLock { runAiCheck(contactId, store, appContext) }
            } finally {
                aiQueued.remove(contactId)
            }
        }
    }

    private suspend fun runAiCheck(contactId: String, store: ContactStore, context: Context) {
        val before = store.find(contactId) ?: return
        // Cards saved before the check ran are still analysed (sending waits for it); hand edits are protected below.
        if (before.ocrStatus != OcrStatus.DONE) {
            if (before.aiStatus == AiStatus.CHECKING) store.update(before.copy(aiStatus = AiStatus.NONE))
            return
        }

        val result = runCatching { CardAiVerifier.get(context).check(before) }
        val latest = store.find(contactId) ?: return
        result
            .onSuccess { r ->
                // Apply a correction only to a field nobody has touched since the check started, and once the
                // user has corrected the card by hand, only fill gaps rather than replace what they typed.
                val handEdited = latest.manuallyEdited
                fun keep(now: String, then: String, ai: String) = when {
                    now != then -> now
                    handEdited && now.isNotBlank() -> now
                    else -> ai
                }
                store.update(
                    latest.copy(
                        name = keep(latest.name, before.name, r.corrected.name),
                        title = keep(latest.title, before.title, r.corrected.title),
                        company = keep(latest.company, before.company, r.corrected.company),
                        email = keep(latest.email, before.email, r.corrected.email),
                        phone = keep(latest.phone, before.phone, r.corrected.phone),
                        website = keep(latest.website, before.website, r.corrected.website),
                        aiStatus = AiStatus.DONE,
                        aiSummary = r.summary
                    ).let { checked -> checked.copy(ocrConfidence = confidenceOf(checked, aiChecked = true)) }
                )
            }
            .onFailure { e ->
                store.update(
                    latest.copy(aiStatus = AiStatus.FAILED, aiSummary = "AI check failed: ${e.message ?: "unknown error"}")
                )
            }
    }

    /**
     * Rough 0-100 score for how complete the extracted card looks, sent as `ocr_confidence` so the
     * backend can triage. Not a model score (ML Kit's Latin recogniser doesn't expose one): it
     * weights the fields that matter for a follow-up, and a card the on-device AI has verified
     * against the photo is rated higher.
     */
    private fun confidenceOf(contact: Contact, aiChecked: Boolean): Int {
        var score = 0
        if (contact.email.isNotBlank()) score += 30
        if (contact.phone.isNotBlank()) score += 25
        if (contact.name.isNotBlank()) score += 20
        if (contact.company.isNotBlank()) score += 15
        if (contact.title.isNotBlank()) score += 5
        if (contact.website.isNotBlank()) score += 5
        if (aiChecked) score = ((score + 100) / 2).coerceAtLeast(score)
        return score.coerceIn(0, 100)
    }

    private suspend fun readSide(path: String): Contact? {
        if (path.isBlank()) return null
        val bitmap = withContext(Dispatchers.IO) {
            ImageUtils.decodeUprightSampledBitmap(File(path), maxDimension = 1600)
        } ?: return null
        return CardOcrParser.parse(CardOcrParser.recognize(bitmap))
    }

    /** Front-side guesses win, the back fills any gaps, and anything already typed by the user is kept. */
    private fun merge(current: Contact, front: Contact?, back: Contact?): Contact {
        fun pick(existing: String, fromFront: String?, fromBack: String?): String =
            existing.ifBlank { fromFront?.takeIf { it.isNotBlank() } ?: fromBack.orEmpty() }

        val rawText = listOfNotNull(
            front?.rawOcrText?.takeIf { it.isNotBlank() }?.let { if (back != null) "FRONT\n$it" else it },
            back?.rawOcrText?.takeIf { it.isNotBlank() }?.let { "BACK\n$it" }
        ).joinToString("\n\n")

        return current.copy(
            name = pick(current.name, front?.name, back?.name),
            title = pick(current.title, front?.title, back?.title),
            company = pick(current.company, front?.company, back?.company),
            email = pick(current.email, front?.email, back?.email),
            phone = pick(current.phone, front?.phone, back?.phone),
            website = pick(current.website, front?.website, back?.website),
            rawOcrText = rawText,
            ocrStatus = OcrStatus.DONE
        )
    }
}
