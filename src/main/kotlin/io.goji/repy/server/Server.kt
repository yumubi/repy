package io.goji.repy.server

import io.goji.repy.common.Constants
import io.goji.repy.config.InitConfig
import io.goji.repy.config.ServerConfig
import io.goji.repy.handler.TcpHandler
import io.goji.repy.handler.UdpHandler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.NetSocket
import io.vertx.core.net.PfxOptions
import io.vertx.kotlin.core.cli.optionOf
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlin.math.log

class Server(private val config: ServerConfig) {
    private val logger = LoggerFactory.getLogger(Server::class.java)
    private val vertx = Vertx.vertx()
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var server: NetServer

    suspend fun start() {


        val options = NetServerOptions()
 //           .setSsl(true)
//            .setKeyCertOptions(
//                PfxOptions()
//                    .setPath(config.certPath)
//                    .setPassword(File(config.privKeyPath).readText())
//            )
//            .setPfxKeyCertOptions(
//                PfxOptions()
//                    .setPath(config.certPath)
//                    .setPassword(File(config.privKeyPath).readText())
//            )



        try {
            server = vertx.createNetServer(options)
        } catch (e: Exception) {
            logger.error("Error creating server", e)
            return
        }

        server.connectHandler { socket ->
            scope.launch {
                try {
                    handleInitialConnection(socket)
                } catch (e: Exception) {
                    logger.error("Error handling connection", e)
                }
            }
        }

        server.listen(config.bindAddr.split(":")[1].toInt(), config.bindAddr.split(":")[0])
            .coAwait()

        logger.info("Server started on ${config.bindAddr}")
    }


    private suspend fun handleInitialConnection(socket: io.vertx.core.net.NetSocket) {
        // Wait for initialization message
        var initBuffer = Buffer.buffer()
        var initialized = false
        logger.info("Waiting for initialization message from ${socket.remoteAddress()}")
        socket.handler { buffer ->
            if (!initialized) {
                initBuffer.appendBuffer(buffer)
                if (initBuffer.length() >= 2) {
                    val length = initBuffer.getUnsignedShort(0)
                    if (initBuffer.length() >= length + 2) {
                        val configData = initBuffer.slice(2, length + 2)
                        handleProtocolInit(socket, configData)
                        initialized = true
                    }
                }
            }
        }
        socket.closeHandler {
            logger.info("Connection closed from ${socket.remoteAddress()}")
        }
    }


    private fun handleProtocolInit(socket: io.vertx.core.net.NetSocket, configData: Buffer) {
        try {
            val initConfig = Json.decodeFromString<InitConfig>(configData.toString(Charsets.UTF_8))

            scope.launch {
                // Start appropriate handler based on protocol
                when (initConfig.protocol) {
                    Constants.TCP -> {
                        TcpHandler(vertx, initConfig.bindPort, socket).start()
                    }
                    Constants.UDP -> {
                        UdpHandler(vertx, initConfig.bindPort, socket).start()
                    }
                    else -> {
                        logger.error("Unknown protocol: ${initConfig.protocol}")
                        socket.close()
                    }
                }
            }
            // Handle heartbeat
            startHeartbeatMonitor(socket)
        } catch (e: Exception) {
            logger.error("Failed to handle protocol initialization", e)
            socket.close()
        }
    }



//    private suspend fun handleConnection(socket: NetSocket) {
//
//
//        try {
//
//            // Read init config
//            //val initBuffer = socket.receive().await()
//
//            var initBuffer = io.vertx.core.buffer.Buffer.buffer()
//            socket.handler { buffer ->
//                logger.info("Received buffer, length: ${buffer.length()}")
//                initBuffer = buffer
//            }
//            logger.info("Received init buffer, length: ${initBuffer.length()}")
//
//            val configLength = initBuffer.getUnsignedShort(0)
//            val configData = initBuffer.getBytes(2, configLength + 2)
//
//
//            val initConfig = Json.decodeFromString<InitConfig>(configData.toString(Charsets.UTF_8))
//
//            // Start appropriate handler based on protocol
//            when (initConfig.protocol) {
//                Constants.TCP -> {
//                    TcpHandler(vertx, initConfig.bindPort, socket).start()
//                }
//                Constants.UDP -> {
//                    UdpHandler(vertx, initConfig.bindPort, socket).start()
//                }
//                else -> {
//                    logger.error("Unknown protocol: ${initConfig.protocol}")
//                    socket.close()
//                }
//            }
//
//            // Handle heartbeat
//            startHeartbeatMonitor(socket)
//        } catch (e: Exception) {
//            logger.error("Error handling connection", e)
//            socket.close()
//        }
//    }

    private suspend fun handleTcpConnection(socket: io.vertx.core.net.NetSocket, bindPort: Int) {
        val server = vertx.createNetServer()

        server.connectHandler { localSocket ->
            // Forward data between sockets
            localSocket.handler { buffer ->
                socket.write(buffer)
            }

            socket.handler { buffer ->
                localSocket.write(buffer)
            }

            // Handle closing
            localSocket.closeHandler {
                socket.close()
            }

            socket.closeHandler {
                localSocket.close()
            }
        }

        server.listen(bindPort, "0.0.0.0").coAwait()
    }

    private fun startHeartbeatMonitor(socket: NetSocket) {
        socket.handler { buffer ->
            if (buffer.getByte(0) != Constants.HEARTBEAT) {
                socket.close()
            }
        }
    }
}
