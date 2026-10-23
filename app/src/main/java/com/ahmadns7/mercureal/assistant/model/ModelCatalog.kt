package com.ahmadns7.mercureal.assistant.model

enum class ModelKind { STT, LLM }

/** Chat template used to wrap the prompt for an instruct LLM. */
enum class PromptFormat { CHATML, LLAMA3 }

/**
 * One curated, downloadable model. The URL is pinned to a Hugging Face commit (not "main")
 * and sha256 is the LFS object id published by that repo. Sizes are the exact pinned byte counts.
 */
data class ModelSpec(
    val id: String,
    val kind: ModelKind,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
    val licenseUrl: String,
    val source: String,
    val promptFormat: PromptFormat? = null,
)

object ModelCatalog {

    const val DEFAULT_STT = "whisper-tiny.en"
    const val DEFAULT_LLM = "qwen2.5-0.5b-instruct-q4_k_m"

    private const val WHISPER_REV = "5359861c739e955e79d9a303bcbc70fb988958b1"
    private const val QWEN_REV = "9217f5db79a29953eb74d5343926648285ec7e67"
    private const val LLAMA_REV = "067b946cf014b7c697f3654f621d577a3e3afd1c"

    private const val WHISPER_REPO = "https://huggingface.co/ggerganov/whisper.cpp"
    private const val QWEN_REPO = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF"
    private const val LLAMA_REPO = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF"

    private const val WHISPER_SOURCE = "ggerganov/whisper.cpp on Hugging Face"

    val all: List<ModelSpec> = listOf(
        ModelSpec(
            id = "whisper-tiny.en",
            kind = ModelKind.STT,
            displayName = "Whisper tiny.en (ggml)",
            fileName = "ggml-tiny.en.bin",
            url = WHISPER_REPO + "/resolve/" + WHISPER_REV + "/ggml-tiny.en.bin",
            sizeBytes = 77_704_715L,
            sha256 = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f",
            license = "MIT",
            licenseUrl = WHISPER_REPO,
            source = WHISPER_SOURCE,
        ),
        ModelSpec(
            id = "whisper-base.en",
            kind = ModelKind.STT,
            displayName = "Whisper base.en (ggml)",
            fileName = "ggml-base.en.bin",
            url = WHISPER_REPO + "/resolve/" + WHISPER_REV + "/ggml-base.en.bin",
            sizeBytes = 147_964_211L,
            sha256 = "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002",
            license = "MIT",
            licenseUrl = WHISPER_REPO,
            source = WHISPER_SOURCE,
        ),
        ModelSpec(
            id = "whisper-small.en",
            kind = ModelKind.STT,
            displayName = "Whisper small.en (ggml)",
            fileName = "ggml-small.en.bin",
            url = WHISPER_REPO + "/resolve/" + WHISPER_REV + "/ggml-small.en.bin",
            sizeBytes = 487_614_201L,
            sha256 = "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d",
            license = "MIT",
            licenseUrl = WHISPER_REPO,
            source = WHISPER_SOURCE,
        ),
        ModelSpec(
            id = "qwen2.5-0.5b-instruct-q4_k_m",
            kind = ModelKind.LLM,
            displayName = "Qwen2.5 0.5B Instruct (Q4_K_M)",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            url = QWEN_REPO + "/resolve/" + QWEN_REV + "/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeBytes = 491_400_032L,
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            license = "Apache-2.0",
            licenseUrl = QWEN_REPO,
            source = "Qwen/Qwen2.5-0.5B-Instruct-GGUF (official Qwen repo)",
            promptFormat = PromptFormat.CHATML,
        ),
        ModelSpec(
            id = "llama-3.2-1b-instruct-q4_k_m",
            kind = ModelKind.LLM,
            displayName = "Llama 3.2 1B Instruct (Q4_K_M)",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            url = LLAMA_REPO + "/resolve/" + LLAMA_REV + "/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes = 807_694_464L,
            sha256 = "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
            license = "Llama 3.2 Community License",
            licenseUrl = LLAMA_REPO,
            source = "bartowski/Llama-3.2-1B-Instruct-GGUF (quantized from meta-llama/Llama-3.2-1B-Instruct)",
            promptFormat = PromptFormat.LLAMA3,
        ),
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    fun ofKind(kind: ModelKind): List<ModelSpec> = all.filter { it.kind == kind }
}
