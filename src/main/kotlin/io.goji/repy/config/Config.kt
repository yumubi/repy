package io.goji.repy.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    @SerialName("bind_addr") val bindAddr: String,
    @SerialName("cert_path") val certPath: String,
    @SerialName("priv_key_path") val privKeyPath: String
)

@Serializable
data class ClientConfig(
   @SerialName("server_addr")  val serverAddr: String,
   @SerialName("server_name")  val serverName: String,
   @SerialName("cert_path")  val certPath: String,
   @SerialName("proxy")  val proxy: List<ProxyConfig>
)

@Serializable
data class ProxyConfig(
   @SerialName("protocol") val protocol: String,
   @SerialName("proxy_addr") val proxyAddr: String,
   @SerialName("remote_port") val remotePort: Int
)

@Serializable
data class InitConfig(
    val protocol: Int,
    val bindPort: Int
)
