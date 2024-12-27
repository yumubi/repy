package io.goji.repy.common

import io.vertx.core.buffer.Buffer
import java.net.InetAddress
import java.net.InetSocketAddress

object MessageCodec {
    fun encodeMessage(data: ByteArray, dest: InetSocketAddress): Buffer {
        val buffer = Buffer.buffer()

        when (dest.address) {
            is java.net.Inet4Address -> {
                buffer.appendByte(Constants.IPV4)
                buffer.appendBytes(dest.address.address)
                buffer.appendUnsignedShort(dest.port)
            }
            is java.net.Inet6Address -> {
                buffer.appendByte(Constants.IPV6)
                buffer.appendBytes(dest.address.address)
                buffer.appendUnsignedShort(dest.port)
            }
        }

        buffer.appendBytes(data)
        return buffer
    }

    fun decodeMessage(buffer: Buffer): Pair<ByteArray, InetSocketAddress> {
        val type = buffer.getByte(0)

        val (address, nextPos) = when (type) {
            Constants.IPV4 -> {
                val addr = ByteArray(4)
                buffer.getBytes(1, 5, addr)
                InetAddress.getByAddress(addr) to 5
            }
            Constants.IPV6 -> {
                val addr = ByteArray(16)
                buffer.getBytes(1, 17, addr)
                InetAddress.getByAddress(addr) to 17
            }
            else -> throw IllegalArgumentException("Invalid address type: $type")
        }

        val port = buffer.getUnsignedShort(nextPos)
        val data = buffer.getBytes(nextPos + 2, buffer.length())

        return data to InetSocketAddress(address, port)
    }
}
