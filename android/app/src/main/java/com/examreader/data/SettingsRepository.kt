package com.examreader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局设置：两个 Key
 *   · 千问 API Key：视觉扫描（qwen-vl 系列）+ 翻译兜底（qwen-plus）
 *   · DeepSeek API Key：生成解析（deepseek-chat）
 */
data class AppSettings(
    val fontScale: Float = 1.0f,
    /** 阿里云百炼 · 通义千问：只负责「看图转文字」 */
    val qwenApiKey: String = "",
    /** DeepSeek：负责「生成解析 + 译文」 */
    val deepseekApiKey: String = ""
) {
    val qwenReady: Boolean get() = qwenApiKey.isNotBlank()

    val deepseekReady: Boolean get() = deepseekApiKey.isNotBlank()

    val translateProviderLabel: String get() = if (qwenReady) "千问翻译" else "未配置"
}

/** AI 服务内置参数（老师看不到也不用改） */
object AiConfig {
    // ---------------- 千问（视觉 / 翻译） ----------------
    /** 阿里云百炼 · 国内站（北京） */
    const val QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    /** 阿里云百炼 · 国际站（新加坡） */
    const val QWEN_BASE_URL_INTL = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"

    /** 首选视觉模型（展示用；实际自动探测） */
    const val VISION_MODEL = "qwen3-vl-plus"

    /**
     * 视觉模型候选（站点 + 模型名）：按顺序探测，遇到 model_not_found 自动降级。
     */
    val VISION_CANDIDATES: List<Pair<String, String>> = listOf(
        QWEN_BASE_URL to "qwen3-vl-plus",
        QWEN_BASE_URL to "qwen-vl-plus-latest",
        QWEN_BASE_URL to "qwen-vl-max-latest",
        QWEN_BASE_URL to "qwen-vl-plus",
        QWEN_BASE_URL to "qwen-vl-max",
        QWEN_BASE_URL to "qwen2.5-vl-72b-instruct",
        QWEN_BASE_URL_INTL to "qwen3-vl-plus",
        QWEN_BASE_URL_INTL to "qwen-vl-plus-latest",
        QWEN_BASE_URL_INTL to "qwen-vl-max-latest",
        QWEN_BASE_URL_INTL to "qwen-vl-plus",
        QWEN_BASE_URL_INTL to "qwen-vl-max"
    )

    /** 翻译首选模型 */
    const val TEXT_MODEL = "qwen-plus"

    /** 翻译模型候选 */
    val TEXT_CANDIDATES: List<Pair<String, String>> = listOf(
        QWEN_BASE_URL to "qwen-plus",
        QWEN_BASE_URL to "qwen-turbo",
        QWEN_BASE_URL_INTL to "qwen-plus",
        QWEN_BASE_URL_INTL to "qwen-turbo"
    )

    // ---------------- DeepSeek（解析） ----------------
    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"

    const val DEEPSEEK_MODEL = "deepseek-chat"

    /** 解析模型候选（按顺序尝试） */
    val DEEPSEEK_CANDIDATES: List<String> = listOf("deepseek-chat", "deepseek-reasoner")
}

class SettingsRepository(private val db: AppDatabase) {
    private val dao = db.settings()

    val settings: Flow<AppSettings> = dao.observeAll().map { rows -> rows.toSettings() }

    suspend fun current(): AppSettings = dao.all().toSettings()

    suspend fun setFontScale(value: Float) = dao.put(SettingEntity("fontScale", value.toString()))

    suspend fun setQwenKey(apiKey: String) = dao.put(SettingEntity("qwenApiKey", apiKey.trim()))

    suspend fun setDeepSeekKey(apiKey: String) = dao.put(SettingEntity("deepseekApiKey", apiKey.trim()))

    suspend fun resetAll() = dao.clear()

    private fun List<SettingEntity>.toSettings(): AppSettings {
        val map = associate { it.key to it.value }
        return AppSettings(
            fontScale = map["fontScale"]?.toFloatOrNull()?.coerceIn(0.8f, 1.6f) ?: 1.0f,
            qwenApiKey = map["qwenApiKey"] ?: "",
            deepseekApiKey = map["deepseekApiKey"] ?: ""
        )
    }
}
