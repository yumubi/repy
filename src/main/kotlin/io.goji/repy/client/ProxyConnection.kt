package io.goji.repy.client

import io.goji.repy.common.Constants
import io.goji.repy.common.MessageCodec
import io.goji.repy.config.InitConfig
import io.goji.repy.config.ProxyConfig
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetClient
import io.vertx.core.net.NetSocket
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext


class ProxyConnection(
    private val vertx: Vertx,
    private val client: NetClient,
    private val config: ProxyConfig,
    private val serverAddress: String,
    private val serverPort: Int,
    private val serverName: String
) : CoroutineScope {
    private val logger = LoggerFactory.getLogger(ProxyConnection::class.java)
    private var serverSocket: NetSocket? = null
    private var heartbeatJob: Job? = null
    private var proxyJob: Job? = null
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    suspend fun start() {
        while (isActive) {
            try {
                connect()
                // Wait for connection to close before retrying
                proxyJob?.join()
                logger.info("Connection closed, retrying...")
                delay(5000)
            } catch (e: Exception) {
                logger.error("Connection error", e)
                delay(5000)
            }
        }
    }

    private suspend fun connect() {
        logger.info("Connecting to $serverAddress:$serverPort")

        serverSocket = client.connect(serverPort, serverAddress).coAwait()

        // Send init config
        val initConfig = InitConfig(
            protocol = when (config.protocol.uppercase()) {
                "TCP" -> Constants.TCP
                "UDP" -> Constants.UDP
                else -> throw IllegalArgumentException("Unsupported protocol: ${config.protocol}")
            },
            bindPort = config.remotePort
        )

        val initConfigJson = Json.encodeToString(initConfig)
        val initBuffer = Buffer.buffer()
            .appendUnsignedShort(initConfigJson.length)
            .appendString(initConfigJson)

        serverSocket?.write(initBuffer)?.coAwait()

        // Start proxy handling
        proxyJob = launch {
            when (initConfig.protocol) {
                Constants.TCP -> handleTcpProxy()
                Constants.UDP -> handleUdpProxy()
                else -> throw IllegalStateException("Invalid protocol")
            }
        }

        // Start heartbeat
        startHeartbeat()
    }

    private suspend fun handleTcpProxy() {
        val localAddr = config.proxyAddr.split(":")
        val localSocket = vertx.createNetClient().connect(
                localAddr[1].toInt(),
                localAddr[0]
            ).coAwait()

        try {
            val serverSocket = serverSocket ?: throw IllegalStateException("Server socket is null")

            // Forward local -> server
            localSocket.handler { buffer ->
                if (isActive) {
                    serverSocket.write(buffer)
                }
            }

            // Forward server -> local
            serverSocket.handler { buffer ->
                if (isActive) {
                    localSocket.write(buffer)
                }
            }

            // Wait for either connection to close
            suspendCancellableCoroutine<Unit> { cont ->
                val closeHandler = {
                    if (cont.isActive) {
                        cont.resume(Unit) {}
                    }
                }

                localSocket.closeHandler { closeHandler() }
                serverSocket.closeHandler { closeHandler() }
            }
        } finally {
            localSocket.close()
            serverSocket?.close()
        }
    }

    private suspend fun handleUdpProxy() {
        val localAddr = config.proxyAddr.split(":")
        val udpSocket = vertx.createDatagramSocket()

        try {
            val serverSocket = serverSocket ?: throw IllegalStateException("Server socket is null")

            // Handle incoming UDP packets
            udpSocket.handler { packet ->
                if (isActive) {
                    val buffer = MessageCodec.encodeMessage(
                        packet.data().bytes,
                        InetSocketAddress(packet.sender().host(), packet.sender().port())
                    )
                    serverSocket.write(buffer)
                }
            }

            // Handle incoming server packets
            serverSocket.handler { buffer ->
                if (isActive) {
                    try {
                        val (data, address) = MessageCodec.decodeMessage(buffer)
                        udpSocket.send(
                            Buffer.buffer(data),
                            address.port,
                            address.address.hostAddress
                        )
                    } catch (e: Exception) {
                        logger.error("Error handling server packet", e)
                    }
                }
            }

            // Wait for server connection to close
            suspendCancellableCoroutine<Unit> { cont ->
                serverSocket.closeHandler {
                    if (cont.isActive) {
                        cont.resume(Unit) {}
                    }
                }
            }
        } finally {
            udpSocket.close()
            serverSocket?.close()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = launch {
            while (isActive) {
                try {
                    delay(3000)
                    serverSocket?.write(Buffer.buffer().appendByte(Constants.HEARTBEAT))
                } catch (e: Exception) {
                    logger.error("Heartbeat error", e)
                    break
                }
            }
        }
    }

    fun stop() {
        supervisorJob.cancel()
        serverSocket?.close()
        heartbeatJob?.cancel()
        proxyJob?.cancel()
    }
}
