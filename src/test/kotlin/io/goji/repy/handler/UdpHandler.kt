package io.goji.repy.handler

import io.goji.repy.common.MessageCodec
import io.vertx.core.Vertx
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.net.NetSocket
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class UdpHandler(
    private val vertx: Vertx,
    private val bindPort: Int,
    private val clientSocket: NetSocket
) {
    private val logger = LoggerFactory.getLogger(UdpHandler::class.java)
    private lateinit var udpSocket: DatagramSocket
    private var handlerJob: Job? = null

    suspend fun start() {


        udpSocket = vertx.createDatagramSocket()
        udpSocket.listen(bindPort, "0.0.0.0").coAwait()

        handlerJob = CoroutineScope(Dispatchers.IO).launch {
            handleUdpTraffic()
        }

        logger.info("UDP handler started on port $bindPort")
    }

    private suspend fun handleUdpTraffic() {
        // Handle incoming UDP packets
        udpSocket.handler { packet ->
            val buffer = MessageCodec.encodeMessage(
                packet.data().bytes,
                InetSocketAddress(packet.sender().host(), packet.sender().port())
            )
            clientSocket.write(buffer)
        }

        // Handle incoming QUIC packets
        clientSocket.handler { buffer ->
            try {
                val (data, address) = MessageCodec.decodeMessage(buffer)
                udpSocket.send(
                    io.vertx.core.buffer.Buffer.buffer(data),
                    address.port,
                    address.address.hostAddress
                )
            } catch (e: Exception) {
                logger.error("Error handling QUIC packet", e)
            }
        }
    }

    fun stop() {
        handlerJob?.cancel()
        udpSocket.close()
    }
}
