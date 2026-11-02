package `in`.daasavaani.core

import java.net.URI

/** Website navigation only: no lyric body, metadata or personal state is encoded. */
fun canonicalLyricLink(canonicalBaseUrl: String, lyricId: String): String {
    require(PersonalReference.validLyricId(lyricId))
    val base = URI(canonicalBaseUrl)
    require(canonicalBaseUrl.all { it.code in 33..126 } &&
        base.scheme == "https" && base.host != null && base.rawUserInfo == null &&
        base.rawQuery == null && base.rawFragment == null &&
        (base.port == -1 || base.port in 1..65535) &&
        base.rawPath.matches(Regex("/(?:[A-Za-z0-9._~-]+/)*")) &&
        base.rawPath.split('/').none { it == "." || it == ".." })
    return canonicalBaseUrl + "#/lyrics/" + lyricId
}
