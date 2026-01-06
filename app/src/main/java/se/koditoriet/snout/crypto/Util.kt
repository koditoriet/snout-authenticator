package se.koditoriet.snout.crypto

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.min

/**
 * Reads bits from an array of bytes. Bytes are interpreted big endian.
 * BitReader(toBigEndianBytes(0xff00ff00L)).getBits(0, 32) == 0xff00ff00L
 */
class BitReader(val bytes: ByteArray, val size: Int = bytes.size * 8) {
    fun getBit(offset: Int): Int {
        require(offset < size)
        val byteOffset = offset / 8
        val bitOffset = offset % 8
        return bytes[byteOffset].toInt().shr(7 - bitOffset) and 0x1
    }

    fun getBits(offset: Int, length: Int): Int {
        require(length <= 32)
        require(offset + length <= size) {
            "tried to read $length bits from offset $offset, which is greater than $size"
        }

        var result = 0
        for (i in offset..< offset + length) {
            result = result.shl(1) or getBit(i)
        }
        return result
    }

    fun chunks(bitsPerChunk: Int): Iterable<Int> = object : Iterable<Int> {
        override fun iterator() = BitReaderIterator(this@BitReader, bitsPerChunk)
    }

    private class BitReaderIterator(
        private val reader: BitReader,
        private val bitsPerChunk: Int,
    ) : Iterator<Int> {
        private var offset: Int = 0

        init {
            require(bitsPerChunk <= 32)
        }

        override fun next(): Int {
            val bitsToRead = min(bitsPerChunk, reader.size - offset)
            val result = reader.getBits(offset, bitsToRead)
            offset += bitsToRead
            return result
        }

        override fun hasNext() = offset < reader.size
    }
}

class BitWriter {
    private var bitOffset: Int = 0
    private val bytes: MutableList<Byte> = mutableListOf()

    fun write(data: Byte, numBits: Int) {
        require(numBits <= 8)
        if (this.bitOffset + numBits > 8) {
            val secondWriteBits = (this.bitOffset + numBits) % 8
            val firstWriteBits = numBits - secondWriteBits
            this.write(data.toInt().shr(secondWriteBits).toByte(), firstWriteBits)
            this.write(data, secondWriteBits)
        } else if (this.bitOffset == 0) {
            this.bytes.add(data.toInt().shl(8 - numBits).toByte())
            this.bitOffset = numBits % 8
        } else {
            val byteOffset = this.bytes.size - 1
            val mask = 0xff.shr(8 - numBits).toByte()
            val bitsToWrite = data.and(mask).toInt().shl(8 - numBits - this.bitOffset)
            this.bytes[byteOffset] = this.bytes[byteOffset].or(bitsToWrite.toByte())
            this.bitOffset = (this.bitOffset + numBits) % 8
        }
    }

    fun getBytes(): ByteArray {
        return this.bytes.toByteArray()
    }

    fun wipe() {
        for (i in bytes.indices) {
            bytes[i] = 0
        }
    }
}

fun ByteArray.bitChunks(bitsPerChunk: Int): Iterable<Int> =
    BitReader(this).chunks(bitsPerChunk)
