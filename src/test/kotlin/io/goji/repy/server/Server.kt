package io.goji.repy.server

import io.goji.repy.common.Constants
import io.goji.repy.config.InitConfig
import io.goji.repy.config.ServerConfig
import io.goji.repy.handler.TcpHandler
import io.goji.repy.handler.UdpHandler
import io.vertx.core.Vertx
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.NetSocket
import io.vertx.core.net.PfxOptions
import io.vertx.kotlin.core.cli.optionOf
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

class Server(private val config: ServerConfig) {
    private val logger = LoggerFactory.getLogger(Server::class.java)
    private val vertx = Vertx.vertx()
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
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    handleConnection(socket)
                } catch (e: Exception) {
                    logger.error("Error handling connection", e)
                }
            }
        }

        server.listen(config.bindAddr.split(":")[1].toInt(), config.bindAddr.split(":")[0])
            .coAwait()

        logger.info("Server started on ${config.bindAddr}")
    }




    private suspend fun handleConnection(socket: NetSocket) {

        try {

            // Read init config
            //val initBuffer = socket.receive().await()
            var initBuffer = io.vertx.core.buffer.Buffer.buffer()
            socket.handler { buffer ->
                initBuffer = buffer
            }

            val configLength = initBuffer.getUnsignedShort(0)
            val configData = initBuffer.getBytes(2, configLength + 2)


            val initConfig = Json.decodeFromString<InitConfig>(configData.toString(Charsets.UTF_8))

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

            // Handle heartbeat
            startHeartbeatMonitor(socket)
        } catch (e: Exception) {
            logger.error("Error handling connection", e)
            socket.close()
        }
    }

    private fun startHeartbeatMonitor(socket: NetSocket) {
        socket.handler { buffer ->
            if (buffer.getByte(0) != Constants.HEARTBEAT) {
                socket.close()
            }
        }
    }
}
