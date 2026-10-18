package com.tejeet.carddrop.network

import com.tejeet.carddrop.messaging.FollowUpMessages
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.LeadPriority
import com.tejeet.carddrop.util.ImageUtils
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the CardDrop backend (ApiConfig.SEND_CARD_URL). One call = one multipart/form-data
 * POST carrying a single contact (all fields, expo notes, and the photos); the backend sends the
 * email and WhatsApp message and reports back per channel. Field names are exactly the backend's
 * - see "Backend API contract" in DEVELOPMENT.md.
 *
 * Call sendContact() from a background thread (e.g. Dispatchers.IO) - it blocks.
 */
class BackendClient(
    private val backendUrl: String,
    private val sharedSecret: String,
    private val senderName: String,
    /** Expo name from Settings, for cards that don't have their own. */
    private val defaultEventName: String,
    private val deviceId: String,
    private val appVersion: String,
    private val language: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Which of the requested channels the backend confirmed, plus a readable error for any that didn't. */
    data class SendResult(val emailSent: Boolean, val whatsappSent: Boolean, val error: String)

    /**
     * Re-uploads a card (details + photos) without sending anything: `channels` is empty and
     * `syncOnly=1`, so the backend stores or updates its row by `id` and messages nobody.
     * Returns an empty string on success, otherwise the error to show.
     */
    fun syncContact(contact: Contact): String =
        sendContact(contact, emptyList(), syncOnly = true).error

    fun sendContact(contact: Contact, channels: List<String>, syncOnly: Boolean = false): SendResult {
        return try {
            val messages = FollowUpMessages.build(contact, defaultEventName)
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("secret", sharedSecret)
                // Idempotency + who's sending
                .addFormDataPart("id", contact.id)
                .addFormDataPart("senderName", senderName)
                .addFormDataPart("channels", channels.joinToString(","))
                .addFormDataPart("syncOnly", if (syncOnly) "1" else "0")
                // Contact
                .addFormDataPart("name", contact.name)
                .addFormDataPart("title", contact.title)
                .addFormDataPart("company", contact.company)
                .addFormDataPart("email", contact.email)
                .addFormDataPart("phone", contact.phone)
                .addFormDataPart("website", contact.website)
                .addFormDataPart("linkedin", contact.linkedin)
                .addFormDataPart("country", contact.country)
                .addFormDataPart("city", contact.city)
                .addFormDataPart("department", contact.department)
                // Event / location
                .addFormDataPart("eventName", contact.eventName.ifBlank { defaultEventName })
                .addFormDataPart("stallNumber", contact.stallNumber)
                .addFormDataPart("hallName", contact.hallName)
                .addFormDataPart("geoLat", contact.geoLat?.toString().orEmpty())
                .addFormDataPart("geoLng", contact.geoLng?.toString().orEmpty())
                // Sales workflow
                .addFormDataPart("expoNotes", contact.notes)
                .addFormDataPart("productInterest", contact.productInterest)
                .addFormDataPart("followUpDate", contact.followUpDate)
                .addFormDataPart("assignedTo", contact.assignedTo)
                .addFormDataPart("nextAction", contact.nextAction)
                .addFormDataPart("useCase", contact.useCase)
                .addFormDataPart("voiceSummary", contact.voiceSummary)
                .addFormDataPart("priority", contact.priority.toBackendValue())
                .addFormDataPart("tags", contact.tags.joinToString(","))
                // Consent
                .addFormDataPart("consentEmail", if (contact.consentEmail) "1" else "0")
                .addFormDataPart("consentWhatsapp", if (contact.consentWhatsapp) "1" else "0")
                // App metadata
                .addFormDataPart("deviceId", deviceId)
                .addFormDataPart("appVersion", appVersion)
                .addFormDataPart("language", language)
                .addFormDataPart("ocrConfidence", contact.ocrConfidence.toString())
                .addFormDataPart("manuallyEdited", if (contact.manuallyEdited) "1" else "0")
                // Follow-up messages, ready to send as-is
                .addFormDataPart("whatsappMessage", messages.whatsapp)
                .addFormDataPart("emailSubject", messages.emailSubject)
                .addFormDataPart("emailMessage", messages.emailHtml)
                // Timing (ms since epoch)
                .addFormDataPart("scannedAt", contact.createdAt.toString())
                // Photos (each optional)
                .apply {
                    ImageUtils.jpegForUpload(contact.photoPath)?.let { jpeg ->
                        addFormDataPart("cardImage", "card_${contact.id}_front.jpg", jpeg.toRequestBody(JPEG))
                    }
                    ImageUtils.jpegForUpload(contact.backPhotoPath)?.let { jpeg ->
                        addFormDataPart("cardImageBack", "card_${contact.id}_back.jpg", jpeg.toRequestBody(JPEG))
                    }
                    // The conversation recording: the richest context on the card.
                    File(contact.voiceNotePath).takeIf { contact.voiceNotePath.isNotBlank() && it.exists() }?.let { audio ->
                        addFormDataPart(
                            "voiceNote",
                            "visit_${contact.id}.m4a",
                            audio.asRequestBody("audio/mp4".toMediaType())
                        )
                    }
                    contact.stallPhotoPaths.forEachIndexed { index, path ->
                        ImageUtils.jpegForUpload(path)?.let { jpeg ->
                            addFormDataPart("stallImage", "stall_${contact.id}_${index + 1}.jpg", jpeg.toRequestBody(JPEG))
                        }
                    }
                }
                .build()

            val request = Request.Builder()
                .url(backendUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(responseText) }.getOrNull()

                if (!response.isSuccessful) {
                    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: responseText.take(200)
                    return failure("HTTP ${response.code}: $message")
                }
                if (json == null) {
                    return failure("Backend didn't return JSON: ${responseText.take(200)}")
                }
                if (syncOnly) {
                    // Nothing was sent, so there are no channels to confirm - just the row being stored.
                    if (json.optString("status").lowercase() in OK_VALUES) {
                        SendResult(emailSent = false, whatsappSent = false, error = "")
                    } else {
                        failure(json.optString("message").ifBlank { "Sync not confirmed by the backend" })
                    }
                } else {
                    parseResult(json, channels)
                }
            }
        } catch (e: IOException) {
            failure(e.message ?: "Network error — check you're online and the URL is correct")
        } catch (e: Exception) {
            failure(e.message ?: "Unexpected error")
        }
    }

    private fun parseResult(json: JSONObject, channels: List<String>): SendResult {
        val overallOk = json.optString("status").lowercase() in OK_VALUES
        val errors = mutableListOf<String>()

        fun channelResult(channel: String, label: String): Boolean {
            if (channel !in channels) return false
            // A per-channel field wins; without one, the overall status stands for every channel.
            val ok = if (json.has(channel)) isOk(json.opt(channel)) else overallOk
            if (!ok) {
                val detail = json.optJSONObject(channel)?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                errors += if (detail != null) "$label failed: $detail" else "$label not confirmed"
            }
            return ok
        }

        val emailSent = channelResult(Contact.CHANNEL_EMAIL, "Email")
        val whatsappSent = channelResult(Contact.CHANNEL_WHATSAPP, "WhatsApp")
        return SendResult(emailSent, whatsappSent, errors.joinToString("; "))
    }

    private fun isOk(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is JSONObject -> value.optString("status").lowercase() in OK_VALUES
        null -> false
        else -> value.toString().lowercase() in OK_VALUES
    }

    private fun failure(message: String) = SendResult(emailSent = false, whatsappSent = false, error = message)

    /** The app's Hot/Warm/Cold mapped onto the backend's NONE / LOW / MEDIUM / HIGH / HOT. */
    private fun LeadPriority.toBackendValue(): String = when (this) {
        LeadPriority.HOT -> "HOT"
        LeadPriority.WARM -> "MEDIUM"
        LeadPriority.COLD -> "LOW"
        LeadPriority.NONE -> "NONE"
    }

    private companion object {
        val OK_VALUES = setOf("ok", "success", "sent", "delivered", "true")
        val JPEG = "image/jpeg".toMediaType()
    }
}
