package `in`.daasavaani.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.ListEntry
import `in`.daasavaani.core.ListSnapshot
import `in`.daasavaani.core.PersonalNames
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalNamesTest {
    @Test fun packagedTablesWorkOnDeviceAndNeverRewritePreviewOrReexportNames() {
        val codec = transferCodec(InstrumentationRegistry.getInstrumentation().targetContext.resources)
        val original = ListSnapshot("Straße E\u0301 \uD801\uDC00 \u1c89", listOf(ListEntry("placeholder", text = "literal")))
        val equal = ListSnapshot("STRASSE É \uD801\uDC28 \u1c8a", original.entries)
        assertEquals(PersonalNames.PROFILE, original.nameKey.profile)
        assertEquals(equal.nameKey, original.nameKey)
        assertEquals("ss\u03c3\u13a0\uac01", PersonalNames.key("\u00df\u03c2\uab70\u1100\u1161\u11a8").value)
        val decoded = codec.decodeFile(ByteArrayInputStream(codec.encodeFile(original)))
        assertEquals(original, decoded)
        assertEquals(original, codec.decodeLink(codec.encodeLink(decoded)))
        assertNotEquals(original.name, equal.name)
    }
}
