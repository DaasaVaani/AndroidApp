package `in`.daasavaani.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class ListInteropTest {
    @Test fun mixedV2PinnedVectorOrActualPeerOutputUsesNativeCodecWithoutPersonalWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val codec = transferCodec(context.resources)
        val id = "00000000-0000-4000-8000-000000000001"
        val expected = ListSnapshot("  Synthetic e\u0301 😀  ", listOf(
            PersonalReference.lyric(id).entry("  User label  "), PersonalReference.legacy(id).entry(),
            ListEntry("placeholder", text = "<b>Plain synthetic text</b>"),
            PersonalReference.lyric(id).entry(), PersonalReference.legacy("fixture_ref").entry("e\u0301")), 2)
        val keys = listOf("webGzipBase64", "webLinkBase64", "webJsonBase64")
        val provided = keys.map { args.getString(it) }
        require(provided.all { it == null } || provided.all { it != null }) { "Supply all peer-output arguments or none" }
        fun decode(index: Int) = Base64.getDecoder().decode(requireNotNull(provided[index]))
        val gzip = if (provided[0] == null) codec.encodeFile(expected) else decode(0)
        val link = if (provided[1] == null) codec.encodeLink(expected) else {
            val fileText = decode(1).toString(Charsets.UTF_8)
            // Evidence text files may end in one line delimiter, which is not part of the wire URL.
            when {
                fileText.endsWith("\r\n") -> fileText.dropLast(2)
                fileText.endsWith("\n") -> fileText.dropLast(1)
                else -> fileText
            }
        }
        val json = if (provided[2] == null) codec.json(expected) else decode(2)
        assertEquals(expected, codec.decodeJson(json))
        val fromFile = codec.decodeFile(gzip.inputStream())
        val fromLink = codec.decodeLink(link)
        assertEquals(expected, fromFile)
        assertEquals(expected, fromLink)
        assertNotEquals(fromFile.entries[0].reference(), fromFile.entries[1].reference())
        assertEquals(fromFile.entries[0].reference(), fromFile.entries[3].reference())
        val exportedFile = codec.encodeFile(fromFile)
        val exportedLink = codec.encodeLink(fromLink)
        assertEquals(expected, codec.decodeFile(exportedFile.inputStream()))
        assertEquals(expected, codec.decodeLink(exportedLink))
        context.openFileOutput("android-typed-v2-actual.gz", Context.MODE_PRIVATE).use { it.write(exportedFile) }
        context.openFileOutput("android-typed-v2-actual.url", Context.MODE_PRIVATE).use { it.write(exportedLink.toByteArray(Charsets.UTF_8)) }
    }
}
