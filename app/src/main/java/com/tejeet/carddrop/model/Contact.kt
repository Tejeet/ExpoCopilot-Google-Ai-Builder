package com.tejeet.carddrop.model

import org.json.JSONObject
import java.util.UUID

enum class SendStatus { PENDING, SENDING, SENT, FAILED }

/** Background text-recognition state for the card photos. */
enum class OcrStatus { PROCESSING, DONE, FAILED }

/** On-device AI (Gemma) double-check of the OCR guesses. */
enum class AiStatus { NONE, CHECKING, DONE, FAILED }

enum class LeadPriority(val label: String) { NONE("None"), HOT("Hot"), WARM("Warm"), COLD("Cold") }

/**
 * Fully immutable on purpose: Contact instances live inside a Compose
 * SnapshotStateList (see ContactStore). Mutating a field in place would not
 * reliably trigger recomposition, so every "edit" is a `.copy()` that produces
 * a new instance, which ContactStore then swaps into the list.
 */
data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val company: String = "",
    val title: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
    val linkedin: String = "",
    val department: String = "",
    /** Expo notes typed by the user - never auto-filled by OCR. */
    val notes: String = "",
    val eventName: String = "",
    val stallNumber: String = "",
    val hallName: String = "",
    val city: String = "",
    val country: String = "",
    /** Where the card was scanned, filled in from GPS when location is allowed. */
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val productInterest: String = "",
    /** ISO date (yyyy-MM-dd) or empty. */
    val followUpDate: String = "",
    val assignedTo: String = "",
    val nextAction: String = "",
    /** Recording of the conversation at the stand, kept for context later. */
    val voiceNotePath: String = "",
    val voiceNoteDurationMs: Long = 0L,
    /** What Gemma made of the recording, on the phone. */
    val voiceSummary: String = "",
    /** What they want to use apiHW for; worked into the follow-up messages. */
    val useCase: String = "",
    val consentEmail: Boolean = true,
    val consentWhatsapp: Boolean = true,
    /** 0-100 confidence that the extracted fields are right; heuristic, see CardScanProcessor. */
    val ocrConfidence: Int = 0,
    /** True once the user has changed a field that OCR (or the AI) filled in. */
    val manuallyEdited: Boolean = false,
    val priority: LeadPriority = LeadPriority.NONE,
    val rawOcrText: String = "",
    val photoPath: String = "",
    val backPhotoPath: String = "",
    /** Photos of the person's stall / product, for later reference. */
    val stallPhotoPaths: List<String> = emptyList(),
    /** Free-form labels such as "distributor" or "VIP", for grouping and search. */
    val tags: List<String> = emptyList(),
    val ocrStatus: OcrStatus = OcrStatus.DONE,
    /** True once the user has checked the OCR guesses on the review screen; nothing sends before that. */
    val reviewed: Boolean = false,
    val aiStatus: AiStatus = AiStatus.NONE,
    /** What the AI check changed (or that it found nothing to fix), shown on the review screen. */
    val aiSummary: String = "",
    val status: SendStatus = SendStatus.PENDING,
    val lastError: String = "",
    val emailSent: Boolean = false,
    val whatsappSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** When this card was last uploaded to the backend (sync or send); 0 = never. */
    val syncedAt: Long = 0L,
    val sentAt: Long = 0L
) {
    val hasEmail: Boolean get() = email.isNotBlank()
    val hasPhone: Boolean get() = phone.isNotBlank()

    /** Channels this contact can receive but hasn't yet; a retry only asks the backend for these. */
    fun pendingChannels(): List<String> = buildList {
        if (hasEmail && !emailSent) add(CHANNEL_EMAIL)
        if (hasPhone && !whatsappSent) add(CHANNEL_WHATSAPP)
    }

    /**
     * Search: every word must appear somewhere in the card (name, title, company, email, phone,
     * website, expo notes, expo name, tags). Phone numbers also match on digits alone, so "98765"
     * finds "+91 98765 43210".
     */
    fun matches(query: String): Boolean {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = (
            listOf(
                name, title, company, email, phone, website, linkedin, department, notes, eventName,
                stallNumber, hallName, city, country, productInterest, assignedTo, nextAction, useCase, voiceSummary
            ) + tags
            )
            .joinToString(" ")
            .lowercase()
        val phoneDigits = phone.filter(Char::isDigit)
        return terms.all { term ->
            val termDigits = term.filter(Char::isDigit)
            haystack.contains(term) || (termDigits.length >= 3 && termDigits.length == term.length && phoneDigits.contains(termDigits))
        }
    }

    /** Fields the backend requires; a card can be saved without them but not sent until they're filled. */
    fun missingRequired(): List<String> = buildList {
        if (company.isBlank()) add("company")
        if (email.isBlank()) add("email")
        if (phone.isBlank()) add("phone")
    }

    val isReadyToSend: Boolean
        get() = reviewed &&
            missingRequired().isEmpty() &&
            ocrStatus != OcrStatus.PROCESSING &&
            aiStatus != AiStatus.CHECKING &&
            (status == SendStatus.PENDING || status == SendStatus.FAILED) &&
            pendingChannels().isNotEmpty()

    companion object {
        const val CHANNEL_EMAIL = "email"
        const val CHANNEL_WHATSAPP = "whatsapp"

        /** Reads a contact from the old contacts.json queue (pre-SQLite), for one-time migration. */
        fun fromLegacyJson(json: JSONObject): Contact {
            val status = runCatching { SendStatus.valueOf(json.optString("status", "PENDING")) }
                .getOrDefault(SendStatus.PENDING)
            return Contact(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", ""),
                company = json.optString("company", ""),
                title = json.optString("title", ""),
                email = json.optString("email", ""),
                phone = json.optString("phone", ""),
                website = json.optString("website", ""),
                notes = json.optString("notes", ""),
                rawOcrText = json.optString("rawOcrText", ""),
                photoPath = json.optString("photoPath", ""),
                // Old cards could only be queued from the review screen.
                reviewed = true,
                status = status,
                lastError = json.optString("lastError", ""),
                // The old backend only ever sent email.
                emailSent = status == SendStatus.SENT
            )
        }
    }
}
