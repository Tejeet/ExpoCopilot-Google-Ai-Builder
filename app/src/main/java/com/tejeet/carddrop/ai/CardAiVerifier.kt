package com.tejeet.carddrop.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.util.ImageUtils
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Double-checks OCR guesses with a small open-weight model running fully on the phone
 * (LiteRT-LM, Google AI Edge). Which model is used is a Settings choice (see AiModel): the
 * default reads only the OCR text, while Gemma 4 also looks at the card photo. Either returns
 * corrected fields as JSON;
 * the app then sanity-checks every value before using it, so a model mistake can't turn a
 * valid email into an invalid one.
 *
 * One card at a time: the engine is loaded once (several seconds, ~1-2 GB RAM) on a dedicated
 * thread and reused for every check until release() is called.
 */
class CardAiVerifier private constructor(private val appContext: Context) {

    data class Result(val corrected: Contact, val summary: String)

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "carddrop-gemma") }
        .asCoroutineDispatcher()
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedModel: AiModel? = null

    /** Throws if the model isn't downloaded, can't be loaded, or doesn't return usable JSON. */
    suspend fun check(card: Contact): Result = mutex.withLock {
        withContext(dispatcher) {
            val engine = ensureEngine()
            val model = AiModelFiles.selectedModel(appContext)
            val contents = buildContents(card, model)
            // Constrained JSON output first; if this runtime/model rejects it, ask plainly and parse leniently.
            val json = runCatching { ask(engine, contents, model, constrainToJson = true) }.getOrNull()?.let(::extractJson)
                ?: ask(engine, contents, model, constrainToJson = false).let(::extractJson)
                ?: error("The AI's answer wasn't valid JSON")
            buildResult(card, json)
        }
    }

    /** True when the selected model can listen to a recording (Gemma 4 today). */
    fun canSummariseAudio(): Boolean = AiModelFiles.selectedModel(appContext).readsAudio

    /**
     * Summarises a recorded conversation on the phone. Needs an audio-capable model; the recording
     * never leaves the device for this.
     */
    suspend fun summariseConversation(audioPath: String): String = mutex.withLock {
        withContext(dispatcher) {
            val model = AiModelFiles.selectedModel(appContext)
            check(model.readsAudio) { "${model.displayName} can't listen to recordings - pick Gemma 4 in Settings" }
            check(File(audioPath).exists()) { "That recording is missing" }
            val engine = ensureEngine()
            val contents = Contents.of(
                listOf(
                    Content.AudioFile(audioPath),
                    Content.Text(
                        "The audio is a conversation at a trade show stand between our rep and a visitor. " +
                            "Summarise it in at most six short bullet points: what the visitor needs, products or " +
                            "specifications mentioned, quantities or timelines, any objection, and the agreed next step. " +
                            "Use only what is said. Reply with the bullet points only."
                    )
                )
            )
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    "You write short, factual meeting notes for a B2B sales team. Never invent details."
                ),
                samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.2),
                maxOutputToken = 400,
                thinkingConfig = ThinkingConfig(enableThinking = false)
            )
            engine.createConversation(config).use { conversation ->
                conversation.sendMessage(contents)
                    .contents.contents.filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                    .trim()
            }
        }
    }

    /** Loads the model ahead of the first check. Safe to call repeatedly; failures surface on the real check. */
    suspend fun warmUp() {
        mutex.withLock { withContext(dispatcher) { runCatching { ensureEngine() } } }
    }

    /** Frees the model's memory, e.g. before deleting the model file. */
    suspend fun release() = mutex.withLock {
        withContext(dispatcher) {
            runCatching { engine?.close() }
            engine = null
            loadedModel = null
        }
    }

    private fun ensureEngine(): Engine {
        val model = AiModelFiles.selectedModel(appContext)
        engine?.let {
            if (loadedModel == model) return it
            // Switched model in Settings: drop the old one before loading the new.
            runCatching { it.close() }
            engine = null
        }
        check(AiModelFiles.isReady(appContext, model)) { "The ${model.displayName} model isn't downloaded yet" }
        val path = AiModelFiles.file(appContext, model).absolutePath
        // GPU is several times faster; fall back to CPU on phones where the GPU delegate won't load.
        val created = createEngine(path, model, useGpu = true) ?: createEngine(path, model, useGpu = false)!!
        engine = created
        loadedModel = model
        return created
    }

    private fun createEngine(path: String, model: AiModel, useGpu: Boolean): Engine? {
        var created: Engine? = null
        return try {
            created = Engine(
                EngineConfig(
                    modelPath = path,
                    backend = if (useGpu) Backend.GPU() else Backend.CPU(),
                    visionBackend = if (useGpu) Backend.GPU() else Backend.CPU(),
                    maxNumTokens = 2048,
                    maxNumImages = if (model.readsPhoto) 2 else null,
                    cacheDir = appContext.cacheDir.absolutePath
                )
            )
            created.initialize()
            created
        } catch (t: Throwable) {
            runCatching { created?.close() }
            if (useGpu) null else throw IllegalStateException("Couldn't load the AI model: ${t.message}", t)
        }
    }

    private fun ask(engine: Engine, contents: Contents, model: AiModel, constrainToJson: Boolean): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(if (model.readsPhoto) SYSTEM_PROMPT_WITH_PHOTO else SYSTEM_PROMPT_TEXT_ONLY),
            samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.1),
            maxOutputToken = 300,
            // Small reasoning models (Qwen3) otherwise spend their budget "thinking" before the JSON.
            thinkingConfig = ThinkingConfig(enableThinking = false),
            enableResponseFormat = constrainToJson
        )
        return engine.createConversation(config).use { conversation ->
            val reply = if (constrainToJson) {
                conversation.sendMessage(contents, responseFormat = ResponseFormat.json(RESPONSE_SCHEMA))
            } else {
                conversation.sendMessage(contents)
            }
            reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        }
    }

    private fun buildContents(card: Contact, model: AiModel): Contents {
        val images = if (model.readsPhoto) {
            listOf(card.photoPath, card.backPhotoPath).filter { it.isNotBlank() && File(it).exists() }
        } else {
            emptyList()
        }
        val fields = JSONObject().apply {
            put("name", card.name)
            put("title", card.title)
            put("company", card.company)
            put("email", card.email)
            put("phone", card.phone)
            put("website", card.website)
        }
        val prompt = buildString {
            append(
                when {
                    !model.readsPhoto -> "Text read from a business card by OCR (no photo available)."
                    images.isEmpty() -> "No photo is available for this card."
                    images.size == 1 -> "The image is the business card."
                    else -> "The first image is the front of the business card, the second is the back."
                }
            )
            append("\n\nOCR text:\n")
            append(card.rawOcrText.ifBlank { "(none)" })
            append("\n\nFields guessed from the OCR text:\n")
            append(fields.toString(2))
            append(
                if (model.readsPhoto) {
                    "\n\nCheck every field against the card and return the corrected fields as JSON."
                } else {
                    "\n\nCheck every field against the OCR text and return the corrected fields as JSON."
                }
            )
        }
        // The vision encoder works at well under 1024px, so sending smaller images saves decode time.
        val imageParts: List<Content> = images.map { path ->
            ImageUtils.jpegForUpload(path, maxDimension = AI_IMAGE_MAX_DIMENSION, quality = 90)
                ?.let { Content.ImageBytes(it) }
                ?: Content.ImageFile(path)
        }
        return Contents.of(imageParts + Content.Text(prompt))
    }

    private fun buildResult(card: Contact, json: JSONObject): Result {
        fun pick(key: String, current: String, isValid: (String) -> Boolean = { true }): String {
            val value = json.optString(key, "").trim()
            // A blank or implausible answer keeps the OCR value - the user reviews it either way.
            return if (value.isNotBlank() && isValid(value)) value else current
        }

        val corrected = card.copy(
            name = pick("name", card.name),
            title = pick("title", card.title),
            company = pick("company", card.company),
            email = pick("email", card.email) { EMAIL_REGEX.matches(it) },
            phone = pick("phone", card.phone) { it.count(Char::isDigit) in 7..15 },
            website = pick("website", card.website) { it.contains('.') && ' ' !in it }
        )

        val changes = listOf(
            Triple("Name", card.name, corrected.name),
            Triple("Title", card.title, corrected.title),
            Triple("Company", card.company, corrected.company),
            Triple("Email", card.email, corrected.email),
            Triple("Phone", card.phone, corrected.phone),
            Triple("Website", card.website, corrected.website)
        ).filter { (_, before, after) -> before != after }
            .map { (label, before, after) -> if (before.isBlank()) "$label added: $after" else "$label: $before → $after" }

        val summary = buildString {
            append(
                if (changes.isEmpty()) "Checked against the card photo - no corrections needed."
                else "Corrected ${changes.joinToString("; ")}"
            )
            if (json.optString("confidence").equals("low", ignoreCase = true)) {
                append(". The AI wasn't confident about this card - check the details carefully.")
            }
        }
        return Result(corrected, summary)
    }

    private fun extractJson(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    companion object {
        private const val AI_IMAGE_MAX_DIMENSION = 1024
        private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

        private val SYSTEM_PROMPT_TEXT_ONLY = """
            You check contact details that OCR read from a business card. You see only the OCR text, not the card. Fix what is clearly wrong and return corrected values.
            Rules:
            - Fix OCR mistakes such as O/0, l/1/I, S/5, rn/m, missing or extra characters, and broken email addresses or website domains.
            - Put each value in the right field: name is the person, title is their job title, company is the organisation.
            - Use only text that appears in the OCR output. If a field isn't there, return an empty string. Never invent details.
            - Keep phone numbers as printed, including any country code. If there are several, prefer the mobile number.
            - confidence is how sure you are that the fields now match the card: high, medium or low.
            Reply with JSON only.
        """.trimIndent()

        private val SYSTEM_PROMPT_WITH_PHOTO = """
            You check contact details that OCR read from a business card. Compare them with the card image and the OCR text, and return corrected values.
            Rules:
            - Fix OCR mistakes such as O/0, l/1/I, S/5, rn/m, missing or extra characters, and broken email addresses or website domains.
            - Put each value in the right field: name is the person, title is their job title, company is the organisation.
            - Use only information printed on the card. If a field is not on the card, return an empty string. Never invent details.
            - Keep phone numbers as printed, including any country code. If there are several, prefer the mobile number.
            - confidence is how sure you are that the fields now match the card: high, medium or low.
            Reply with JSON only.
        """.trimIndent()

        private val RESPONSE_SCHEMA = """
            {"type":"object","properties":{
              "name":{"type":"string"},"title":{"type":"string"},"company":{"type":"string"},
              "email":{"type":"string"},"phone":{"type":"string"},"website":{"type":"string"},
              "confidence":{"type":"string","enum":["high","medium","low"]}},
             "required":["name","title","company","email","phone","website","confidence"]}
        """.trimIndent()

        @Volatile
        private var instance: CardAiVerifier? = null

        fun get(context: Context): CardAiVerifier =
            instance ?: synchronized(this) {
                instance ?: CardAiVerifier(context.applicationContext).also { instance = it }
            }
    }
}
