package io.goji.repy

import io.goji.repy.client.Client
import io.goji.repy.config.ClientConfig
import io.goji.repy.config.ServerConfig
import io.goji.repy.server.Server
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) = runBlocking {
    val logger = LoggerFactory.getLogger("Main")

    if (args.size != 2) {
        logger.error("Usage: <mode> <config-path>")
        return@runBlocking
    }

    val mode = args[0]
    val configPath = args[1]
    val configContent = File(configPath).readText()

    when (mode) {
        "server" -> {
            val config = Json.decodeFromString<ServerConfig>(configContent)
            Server(config).start()
        }
        "client" -> {
            val config = Json.decodeFromString<ClientConfig>(configContent)
            Client(config).start()
        }
        else -> logger.error("Invalid mode: $mode")
    }
}
