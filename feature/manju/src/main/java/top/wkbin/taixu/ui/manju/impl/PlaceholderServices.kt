package top.wkbin.taixu.ui.manju.impl

import top.wkbin.taixu.ui.manju.ImageGenerator
import top.wkbin.taixu.ui.manju.VoiceSynthesizer

/**
 * 占位出图实现：生成一张纯色占位图，用于在未接入真实供应商时跑通整条流水线。
 *
 * 刻意不引入额外依赖——只写一个合法的最小 PNG 文件头，足够让 FFmpeg 处理。
 * 接入真实服务时替换此绑定即可，无需改动编排逻辑。
 */
class PlaceholderImageGenerator : ImageGenerator {
    override suspend fun generate(
        prompt: String,
        referenceImage: String?,
        outputPath: String,
    ): Result<String> = runCatching {
        java.io.File(outputPath).writeBytes(MINIMAL_PNG)
        outputPath
    }

    private companion object {
        /** 1x1 透明 PNG，最小合法载荷。 */
        val MINIMAL_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }
}

/**
 * 占位配音实现：生成静音音频。
 *
 * 真实接入建议走 minimax / 火山 TTS，并支持音色克隆（传 [voiceId]）。
 */
class PlaceholderVoiceSynthesizer : VoiceSynthesizer {
    override suspend fun synthesize(
        text: String,
        outputPath: String,
        voiceId: String?,
    ): Result<String> = Result.failure(
        UnsupportedOperationException("尚未接入配音服务，请在 KoinModule 中替换 VoiceSynthesizer 绑定"),
    )
}
