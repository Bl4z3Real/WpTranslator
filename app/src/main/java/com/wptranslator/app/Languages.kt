package com.wptranslator.app

data class Lang(val name: String, val google: String, val bing: String)

object Languages {
    val all: List<Lang> = listOf(
        Lang("Afrikaans", "af", "af"),
        Lang("Arabic", "ar", "ar"),
        Lang("Bulgarian", "bg", "bg"),
        Lang("Catalan", "ca", "ca"),
        Lang("Chinese (Simplified)", "zh-CN", "zh-Hans"),
        Lang("Chinese (Traditional)", "zh-TW", "zh-Hant"),
        Lang("Croatian", "hr", "hr"),
        Lang("Czech", "cs", "cs"),
        Lang("Danish", "da", "da"),
        Lang("Dutch", "nl", "nl"),
        Lang("English", "en", "en"),
        Lang("Estonian", "et", "et"),
        Lang("Finnish", "fi", "fi"),
        Lang("French", "fr", "fr"),
        Lang("German", "de", "de"),
        Lang("Greek", "el", "el"),
        Lang("Hebrew", "iw", "he"),
        Lang("Hindi", "hi", "hi"),
        Lang("Hungarian", "hu", "hu"),
        Lang("Indonesian", "id", "id"),
        Lang("Italian", "it", "it"),
        Lang("Japanese", "ja", "ja"),
        Lang("Korean", "ko", "ko"),
        Lang("Latvian", "lv", "lv"),
        Lang("Lithuanian", "lt", "lt"),
        Lang("Malay", "ms", "ms"),
        Lang("Norwegian", "no", "nb"),
        Lang("Persian", "fa", "fa"),
        Lang("Polish", "pl", "pl"),
        Lang("Portuguese", "pt", "pt"),
        Lang("Romanian", "ro", "ro"),
        Lang("Russian", "ru", "ru"),
        Lang("Serbian (Cyrillic)", "sr", "sr-Cyrl"),
        Lang("Slovak", "sk", "sk"),
        Lang("Slovenian", "sl", "sl"),
        Lang("Spanish", "es", "es"),
        Lang("Swedish", "sv", "sv"),
        Lang("Thai", "th", "th"),
        Lang("Turkish", "tr", "tr"),
        Lang("Ukrainian", "uk", "uk"),
        Lang("Urdu", "ur", "ur"),
        Lang("Vietnamese", "vi", "vi"),
        Lang("Welsh", "cy", "cy")
    )

    fun find(code: String): Lang? = all.firstOrNull { it.google == code }

    fun nameFor(code: String?): String? {
        if (code.isNullOrBlank()) return null
        return all.firstOrNull { it.google.equals(code, true) || it.bing.equals(code, true) }?.name ?: code
    }
}
