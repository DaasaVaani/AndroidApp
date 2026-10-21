package `in`.daasavaani.core

fun uiLanguage(deviceLanguages: List<String>, explicit: String = "device"): String {
    require(explicit in setOf("device", "en", "kn"))
    if (explicit != "device") return explicit
    return deviceLanguages.map { it.substringBefore('-').lowercase() }
        .firstOrNull { it == "kn" || it == "en" } ?: "en"
}
