package io.goji.repy.client
import io.goji.repy.config.ClientConfig
import io.goji.repy.config.ProxyConfig
import io.vertx.core.Vertx
import io.vertx.core.net.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class Client(private val config: ClientConfig) {
    private val logger = LoggerFactory.getLogger(Client::class.java)
    private val vertx = Vertx.vertx()
    private lateinit var client: NetClient
    private val connections = mutableListOf<ProxyConnection>()

    suspend fun start() {
        val options = NetClientOptions()
 //           .setSsl(true)
//            .setPemTrustOptions(
//                PemKeyCertOptions()
//                    .setCertPath(config.certPath)
//            )
//            .setPemTrustOptions(
//                PemKeyCertOptions()
//                    .addCertPath(config.certPath)
//
//            )
            .setTrustAll(true)


        client = vertx.createNetClient(options)

        config.proxy.forEach { proxyConfig ->
            startProxy(proxyConfig)
        }
    }

    private fun startProxy(proxyConfig: ProxyConfig) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                connectToServer(proxyConfig)
            } catch (e: Exception) {
                logger.error("Error starting proxy for ${proxyConfig.proxyAddr}", e)
                // Retry connection after delay
                kotlinx.coroutines.delay(5000)
                startProxy(proxyConfig)
            }
        }
    }

    private suspend fun connectToServer(proxyConfig: ProxyConfig) {
        logger.info("Starting proxy connection for ${proxyConfig.proxyAddr}")

        val serverHostPort = config.serverAddr.split(":")
        val serverHost = serverHostPort[0]
        val serverPort = serverHostPort[1].toInt()

        val connection = ProxyConnection(
            vertx = vertx,
            client = client,
            config = proxyConfig,
            serverAddress = serverHost,
            serverPort = serverPort,
            serverName = config.serverName
        )

        connections.add(connection)
        connection.start()
    }

    fun stop() {
        connections.forEach { it.stop() }
        client.close()
        vertx.close()
    }
}
