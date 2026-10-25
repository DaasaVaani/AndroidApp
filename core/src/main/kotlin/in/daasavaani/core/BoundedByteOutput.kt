package `in`.daasavaani.core

import java.io.OutputStream

/** Fixed-size chunks avoid a geometric backing-buffer allocation beyond the accepted byte ceiling. */
internal class BoundedByteOutput(private val limit: Int) : OutputStream() {
    private val chunks = mutableListOf<ByteArray>()
    private var used = 0
    private var count = 0
    fun size(): Int = count
    override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        if (length > limit - count) throw TransferFailure("size")
        var at = offset
        var remaining = length
        while (remaining > 0) {
            if (chunks.isEmpty() || used == chunks.last().size) {
                chunks.add(ByteArray(minOf(8192, limit - count)))
                used = 0
            }
            val next = minOf(remaining, chunks.last().size - used)
            bytes.copyInto(chunks.last(), used, at, at + next)
            at += next; remaining -= next; used += next; count += next
        }
    }
    fun toByteArray(): ByteArray {
        val result = ByteArray(count)
        var at = 0
        chunks.forEachIndexed { index, bytes ->
            val length = if (index == chunks.lastIndex) used else bytes.size
            bytes.copyInto(result, at, 0, length); at += length
        }
        chunks.clear()
        return result
    }
}
