package io.goji.repy.handler

import io.vertx.core.Vertx
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetSocket
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class TcpHandler(
    private val vertx: Vertx,
    private val bindPort: Int,
    private val clientSocket: NetSocket
) {
    private val logger = LoggerFactory.getLogger(TcpHandler::class.java)
    private lateinit var tcpServer: NetServer

    suspend fun start() {
        tcpServer = vertx.createNetServer()

        tcpServer.connectHandler { socket ->
            handleTcpConnection(socket)
        }

        tcpServer.listen(bindPort, "0.0.0.0").await()
        logger.info("TCP handler started on port $bindPort")
    }

    private fun handleTcpConnection(socket: NetSocket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Forward data from local socket to client
                socket.handler { buffer ->
                    clientSocket.write(buffer)
                }

                // Forward data from client to local socket
                clientSocket.handler { buffer ->
                    socket.write(buffer)
                }

                // Handle connection close
                socket.closeHandler {
                    clientSocket.close()
                }

                clientSocket.closeHandler {
                    socket.close()
                }
            } catch (e: Exception) {
                logger.error("Error handling TCP connection", e)
                socket.close()
                clientSocket.close()
            }
        }
    }

    fun stop() {
        tcpServer.close()
    }
}
