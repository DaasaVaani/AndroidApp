package `in`.daasavaani.core

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.GZIPOutputStream

/** Shared bounded transport for list files and same-platform personal backups. */
class TransferTransport(private val encodedLimit: Int, private val decodedLimit: Int) {
    init { require(encodedLimit > 0 && decodedLimit > 0) }
    private fun invalid(): Nothing = throw TransferFailure("invalid")
    private fun size(): Nothing = throw TransferFailure("size")

    fun read(input: InputStream, check: () -> Unit = {}): ByteArray {
        val output = BoundedByteOutput(encodedLimit)
        val buffer = ByteArray(8192)
        while (true) {
            check()
            val count = input.read(buffer, 0, minOf(buffer.size, encodedLimit - output.size() + 1))
            if (count < 0) return output.toByteArray()
            if (count > encodedLimit - output.size()) size()
            output.write(buffer, 0, count)
        }
    }

    fun decode(input: InputStream, check: () -> Unit = {}): ByteArray = decode(read(input, check), check)

    fun decode(bytes: ByteArray, check: () -> Unit = {}): ByteArray {
        val output = BoundedByteOutput(decodedLimit)
        decodedStream(bytes, check).use { decoded ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = decoded.read(buffer)
                if (count < 0) return output.toByteArray()
                output.write(buffer, 0, count)
            }
        }
    }

    internal fun reader(input: InputStream, check: () -> Unit): Reader =
        fatalReader(decodedStream(read(input, check), check))

    internal fun jsonReader(bytes: ByteArray): Reader {
        if (bytes.size > decodedLimit) size()
        return fatalReader(bytes.inputStream())
    }

    private fun fatalReader(input: InputStream) = InputStreamReader(input, Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))

    private fun decodedStream(bytes: ByteArray, check: () -> Unit): InputStream {
        if (bytes.size > encodedLimit) size()
        if (bytes.size < 18 || bytes[0] != 31.toByte() || bytes[1] != 139.toByte() || bytes[2] != 8.toByte()) invalid()
        val flags = bytes[3].toInt() and 255
        if (flags and 224 != 0) invalid()
        var at = 10
        fun byte(): Int {
            check()
            if (at >= bytes.size - 8) invalid()
            return bytes[at++].toInt() and 255
        }
        if (flags and 4 != 0) repeat(byte() or (byte() shl 8)) { byte() }
        if (flags and 8 != 0) while (byte() != 0) { /* bounded optional filename */ }
        if (flags and 16 != 0) while (byte() != 0) { /* bounded optional comment */ }
        if (flags and 2 != 0) {
            val crc = CRC32().apply { update(bytes, 0, at) }.value.toInt() and 65535
            if ((byte() or (byte() shl 8)) != crc) invalid()
        }
        val inflater = Inflater(true)
        val crc = CRC32()
        inflater.setInput(bytes, at, bytes.size - at)
        return object : InputStream() {
            private var count = 0
            private var finished = false
            private var closed = false
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
                kotlin.check(!closed)
                if (length == 0) return 0
                if (finished) return -1
                check()
                val produced = try { inflater.inflate(buffer, offset, minOf(length, decodedLimit - count + 1)) }
                    catch (_: java.util.zip.DataFormatException) { invalid() }
                if (produced > decodedLimit - count) size()
                crc.update(buffer, offset, produced)
                count += produced
                if (inflater.finished()) {
                    val trailer = bytes.size - inflater.remaining
                    fun uint(offset: Int) = (0..3).fold(0L) { value, index ->
                        value or ((bytes[offset + index].toLong() and 255) shl (index * 8))
                    }
                    if (bytes.size - trailer != 8 || uint(trailer) != crc.value || uint(trailer + 4) != count.toLong()) invalid()
                    finished = true
                }
                if (produced == 0) {
                    if (!finished) invalid()
                    return -1
                }
                return produced
            }
            override fun close() {
                if (!closed) { closed = true; inflater.end() }
            }
        }
    }

    fun utf8(bytes: ByteArray, check: () -> Unit = {}): String {
        if (bytes.size > decodedLimit) size()
        check()
        return try {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val input = ByteBuffer.wrap(bytes)
            val scratch = CharBuffer.allocate(4096)
            while (true) {
                check()
                val result = decoder.decode(input, scratch, true)
                if (result.isError) result.throwException()
                if (result.isUnderflow) break
                scratch.clear()
            }
            val offset = if (bytes.size >= 3 && bytes[0] == (-17).toByte() && bytes[1] == (-69).toByte() &&
                bytes[2] == (-65).toByte()) 3 else 0
            String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
        } catch (_: java.nio.charset.CharacterCodingException) { invalid() }
    }

    fun encode(bytes: ByteArray, level: Int, check: () -> Unit = {}): ByteArray {
        if (bytes.size > decodedLimit) size()
        require(level in 0..9)
        if (encodedLimit < 18) size()
        val output = BoundedByteOutput(encodedLimit)
        output.write(byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1))
        val deflater = Deflater(level, true)
        try {
            deflater.setInput(bytes); deflater.finish()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                check()
                val count = deflater.deflate(buffer)
                if (count > encodedLimit - output.size() - 8) size()
                output.write(buffer, 0, count)
            }
        } finally { deflater.end() }
        listOf(CRC32().apply { update(bytes) }.value, bytes.size.toLong()).forEach { value ->
            repeat(4) { output.write((value ushr (it * 8)).toInt() and 255) }
        }
        return output.toByteArray()
    }

    internal fun encodeStream(level: Int, write: (java.io.OutputStream) -> Unit): ByteArray {
        require(level in 0..9)
        val output = BoundedByteOutput(encodedLimit)
        object : GZIPOutputStream(output, 8192) {
            init { def.setLevel(level) }
        }.use { write(it) }
        return output.toByteArray()
    }
}
