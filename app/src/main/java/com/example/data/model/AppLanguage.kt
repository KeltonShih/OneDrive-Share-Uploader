package com.example.data.model

enum class AppLanguage(val code: String, val localeTag: String?) {
    SYSTEM("system", null),
    ZH_TW("zh-TW", "zh-TW"),
    ZH_CN("zh-CN", "zh-CN"),
    EN("en", "en"),
    JA("ja", "ja"),
    KO("ko", "ko"),
    ES("es", "es");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: SYSTEM
        }
    }
}
