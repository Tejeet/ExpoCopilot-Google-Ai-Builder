package com.tejeet.carddrop.ai

/**
 * On-device models the app can use for the card check, all open-weight and downloadable from
 * Hugging Face without an account (`litert-community`, Apache-2.0).
 *
 * Small text models only see the OCR text, which is what the check is mostly about: fixing
 * O/0, l/1, broken emails and fields in the wrong place. Gemma 4 additionally looks at the card
 * photo, which catches more, at ~8x the download and much slower answers on mid-range phones.
 *
 * Note: gemma-3-270m is deliberately absent - it's gated on Hugging Face, so the app can't
 * fetch it without the user signing in.
 */
enum class AiModel(
    val id: String,
    val displayName: String,
    val summary: String,
    private val repo: String,
    private val revision: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Whether the model also looks at the card photo, not just the scanned text. */
    val readsPhoto: Boolean,
    /** Whether the model can listen to a recorded conversation. */
    val readsAudio: Boolean,
    val recommendedRamGb: Int
) {
    QWEN3_0_6B(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B",
        summary = "Recommended · fast, checks the scanned text",
        repo = "litert-community/Qwen3-0.6B",
        revision = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76",
        fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        sizeBytes = 344_671_744L,
        readsPhoto = false,
        readsAudio = false,
        recommendedRamGb = 4
    ),
    SMOLLM2_360M(
        id = "smollm2-360m",
        displayName = "SmolLM2 360M",
        summary = "Lightest · for older or low-memory phones",
        repo = "litert-community/SmolLM2-360M-Instruct",
        revision = "507c99cfe6541ba2bcd84818786f7b025935e5e1",
        fileName = "SmolLM2_360M_instruct.litertlm",
        sizeBytes = 373_719_040L,
        readsPhoto = false,
        readsAudio = false,
        recommendedRamGb = 3
    ),
    GEMMA_4_E2B(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 E2B",
        summary = "Most thorough · reads the card photo and listens to voice notes, needs 8 GB RAM",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        // Same revision the app shipped with, so a Gemma file already on the phone still counts as downloaded.
        revision = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_583_085_056L,
        readsPhoto = true,
        readsAudio = true,
        recommendedRamGb = 8
    );

    /** Pinned to a revision so the file (and sizeBytes) can't change underneath the app. */
    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/$revision/$fileName"

    val sizeLabel: String
        get() = if (sizeBytes >= 1_000_000_000L) {
            String.format(java.util.Locale.US, "%.1f GB", sizeBytes / 1_000_000_000.0)
        } else {
            "${sizeBytes / 1_000_000} MB"
        }

    companion object {
        val DEFAULT = QWEN3_0_6B
        fun fromId(id: String?): AiModel = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
