package `in`.daasavaani.core

import org.junit.Assert.*
import org.junit.Test

class LyricLinkTest {
    private val id = "12345678-9abc-4def-8123-456789abcdef"

    @Test fun exactConfiguredWebsiteRoutePreservesRootSubpathAndPortWithoutMetadata() {
        for (base in listOf("https://daasavaani.github.io/", "https://example.invalid/reader/",
            "https://example.invalid:8443/a/b/")) {
            assertEquals(base + "#/lyrics/" + id, canonicalLyricLink(base, id))
        }
    }

    @Test fun rejectsLegacyInventedOrNoncanonicalIdentityAndUnsafeBases() {
        for (invalid in listOf("SYNTHETIC_01", "", id.uppercase(), "$id?title=private",
            id.replace("-4def-", "-5def-"), id.replace("-8123-", "-7123-"), "$id/other")) {
            assertThrows(IllegalArgumentException::class.java) { canonicalLyricLink("https://example.invalid/", invalid) }
        }
        for (base in listOf("http://example.invalid/", "https://user@example.invalid/",
            "https://example.invalid/?personal=1", "https://example.invalid/#/list/private",
            "https://example.invalid/path", "https://example.invalid/a/../", "https://example.invalid/%2f/",
            "https://example.invalid//", "https://example.invalid:0/")) {
            assertThrows(IllegalArgumentException::class.java) { canonicalLyricLink(base, id) }
        }
    }
}
