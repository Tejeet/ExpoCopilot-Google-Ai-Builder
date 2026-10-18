package com.tejeet.carddrop.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tejeet.carddrop.model.Contact
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Turns ML Kit's raw on-device OCR output into a best-effort guess at a contact's fields.
 *
 * Business cards have no fixed layout, so this is heuristic, not exact: it looks for an
 * email/phone/website by pattern, and guesses name/title/company from whatever lines are
 * left, ranked by on-card text height (a proxy for font size, i.e. visual hierarchy).
 * The caller always shows these in an editable form, and the full raw recognized text is
 * kept alongside them so nothing OCR found is ever silently lost.
 */
object CardOcrParser {

    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE_CANDIDATE_REGEX = Regex("[+(]?[0-9][0-9\\s().-]{7,}[0-9]")
    private val WEBSITE_CANDIDATE_REGEX =
        Regex("(https?://)?(www\\.)?[A-Za-z0-9-]{2,}(\\.[A-Za-z0-9-]{2,}){1,3}(/[\\w./-]*)?")
    private val COMPANY_HINTS = listOf(
        "pvt", "ltd", "llp", "inc", "llc", "technolog", "solutions", "systems",
        "enterprises", "industries", "group", "consulting", "labs", "software",
        "corp", "company"
    )

    private data class Line(val content: String, val height: Int)

    /** Runs on-device text recognition. Needs no network once the ML Kit model is cached. */
    /** One recogniser for the whole app; creating one per scan re-initialises ML Kit every time. */
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    fun parse(text: Text): Contact {
        val rawText = text.text

        val lines = mutableListOf<Line>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val trimmed = line.text.trim()
                if (trimmed.isEmpty()) continue
                val height = line.boundingBox?.let { it.bottom - it.top } ?: 0
                lines.add(Line(trimmed, height))
            }
        }

        val email = EMAIL_REGEX.find(rawText)?.value?.trim().orEmpty()

        val phone = lines
            .filter { email.isEmpty() || !it.content.contains(email) }
            .flatMap { line -> PHONE_CANDIDATE_REGEX.findAll(line.content).map { it.value.trim() } }
            .firstOrNull { candidate -> candidate.count(Char::isDigit) in 9..13 }
            .orEmpty()

        val website = lines
            .filter { email.isEmpty() || !it.content.contains(email) }
            .flatMap { line -> WEBSITE_CANDIDATE_REGEX.findAll(line.content).map { it.value } }
            .map { it.trim().trimEnd('.', ',', ';') }
            .firstOrNull { it.contains('.') }
            .orEmpty()

        // Whatever's left, ranked tallest-first as a stand-in for font size.
        val remaining = lines.filter { li ->
            val c = li.content
            (email.isEmpty() || !c.contains(email)) &&
                (phone.isEmpty() || !c.contains(phone)) &&
                (website.isEmpty() || !c.contains(website))
        }.sortedByDescending { it.height }

        val name = remaining.getOrNull(0)?.content.orEmpty()
        val companyLine = remaining.firstOrNull { li ->
            COMPANY_HINTS.any { hint -> li.content.lowercase().contains(hint) }
        }
        val company = companyLine?.content ?: remaining.getOrNull(2)?.content.orEmpty()
        val title = remaining.getOrNull(1)?.content
            ?.takeIf { it.isNotBlank() && it != company }
            .orEmpty()

        return Contact(
            name = name,
            company = company,
            title = title,
            email = email,
            phone = phone,
            website = website,
            rawOcrText = rawText
        )
    }
}
