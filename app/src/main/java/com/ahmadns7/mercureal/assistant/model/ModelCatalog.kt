package com.ahmadns7.mercureal.assistant.model

enum class ModelKind { STT, LLM, TTS }

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
    const val DEFAULT_TTS = "vits-ljs-int8"

    private const val WHISPER_REV = "5359861c739e955e79d9a303bcbc70fb988958b1"
    private const val QWEN_REV = "9217f5db79a29953eb74d5343926648285ec7e67"
    private const val LLAMA_REV = "067b946cf014b7c697f3654f621d577a3e3afd1c"
    private const val QWEN_Q3_REV = "41ba88dbac95fed2528c92514c131d73eb5a174b"
    private const val SMOLLM2_REV = "7be6f65f1db715fe5dc5a4634c0d459b4eed42ec"
    private const val VITS_LJS_REV = "7ac337c834f318e45a34037cb3371cc3929187ff"

    private const val WHISPER_REPO = "https://huggingface.co/ggerganov/whisper.cpp"
    private const val QWEN_REPO = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF"
    private const val LLAMA_REPO = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF"
    private const val QWEN_Q3_REPO = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF"
    private const val SMOLLM2_REPO = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF"

    private const val WHISPER_SOURCE = "ggerganov/whisper.cpp on Hugging Face"
    private const val VITS_LJS_REPO = "https://huggingface.co/csukuangfj/vits-ljs"

    val all: List<ModelSpec> = listOf(
        ModelSpec(
            id = "vits-ljs-int8",
            kind = ModelKind.TTS,
            displayName = "VITS LJSpeech, English (int8, sherpa-onnx)",
            fileName = "vits-ljs.int8.onnx",
            url = VITS_LJS_REPO + "/resolve/" + VITS_LJS_REV + "/vits-ljs.int8.onnx",
            sizeBytes = 37_423_560L,
            sha256 = "b3aa88de0202a040d7d75bac9dbf1a59d81d60c7369046f32241afa7dec65cc1",
            license = "Apache-2.0",
            licenseUrl = VITS_LJS_REPO,
            source = "csukuangfj/vits-ljs on Hugging Face (sherpa-onnx VITS, LJSpeech; tokens and lexicon are bundled in the app)",
        ),
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
            id = "qwen2.5-0.5b-instruct-q3_k_s",
            kind = ModelKind.LLM,
            displayName = "Qwen2.5 0.5B Instruct (Q3_K_S)",
            fileName = "Qwen2.5-0.5B-Instruct-Q3_K_S.gguf",
            url = QWEN_Q3_REPO + "/resolve/" + QWEN_Q3_REV + "/Qwen2.5-0.5B-Instruct-Q3_K_S.gguf",
            sizeBytes = 338_263_456L,
            sha256 = "468787b643ef9081a75c6e7bd98da987f46583250248e0a0a7aca173308e8d34",
            license = "Apache-2.0",
            licenseUrl = QWEN_Q3_REPO,
            source = "bartowski/Qwen2.5-0.5B-Instruct-GGUF (quantized from Qwen/Qwen2.5-0.5B-Instruct)",
            promptFormat = PromptFormat.CHATML,
        ),
        ModelSpec(
            id = "smollm2-360m-instruct-q4_0",
            kind = ModelKind.LLM,
            displayName = "SmolLM2 360M Instruct (Q4_0)",
            fileName = "SmolLM2-360M-Instruct-Q4_0.gguf",
            url = SMOLLM2_REPO + "/resolve/" + SMOLLM2_REV + "/SmolLM2-360M-Instruct-Q4_0.gguf",
            sizeBytes = 229_733_280L,
            sha256 = "c3608933eb6e5763b87f769bda40c204dc158333668c7af214644fe39da58627",
            license = "Apache-2.0",
            licenseUrl = SMOLLM2_REPO,
            source = "bartowski/SmolLM2-360M-Instruct-GGUF (quantized from HuggingFaceTB/SmolLM2-360M-Instruct)",
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
