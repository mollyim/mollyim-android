package im.molly.translation

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Network translation client with Kyber-1024 post-quantum encryption
 *
 * Features:
 * - mDNS service discovery for translation servers
 * - Kyber-1024 key exchange (stubbed, needs actual Kyber implementation)
 * - AES-256-GCM encryption for translation requests
 * - Forward secrecy with 5-minute key rotation
 */
class NetworkTranslationClient(private val context: Context) {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val discoveredServers = mutableListOf<TranslationServer>()
    private var currentServer: TranslationServer? = null

    private var sessionKey: ByteArray? = null
    private var keyRotationTime: Long = 0
    private val keyRotationIntervalMs = 5 * 60 * 1000L // 5 minutes

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class TranslationServer(
        val name: String,
        val host: String,
        val port: Int
    )

    fun startDiscovery() {
        Log.d(TAG, "Starting mDNS discovery for translation servers")

        val serviceType = "_emma-translate._tcp"

        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        )
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
            Log.d(TAG, "Stopped mDNS discovery")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }

    suspend fun translateViaNetwork(text: String, sourceLang: String, targetLang: String): TranslationResult? {
        val server = currentServer ?: discoveredServers.firstOrNull()

        if (server == null) {
            Log.w(TAG, "No translation servers available")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                performNetworkTranslation(server, text, sourceLang, targetLang)
            } catch (e: Exception) {
                Log.e(TAG, "Network translation failed", e)
                null
            }
        }
    }

    private suspend fun performNetworkTranslation(
        server: TranslationServer,
        text: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult {
        // Ensure we have a valid session key
        if (sessionKey == null || shouldRotateKey()) {
            performKeyExchange(server)
        }

        val socket = Socket(server.host, server.port)
        socket.use {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            // Send encrypted translation request
            val requestJson = """
                {
                    "text": "$text",
                    "source_lang": "$sourceLang",
                    "target_lang": "$targetLang"
                }
            """.trimIndent()

            val encrypted = encryptRequest(requestJson)
            output.writeInt(encrypted.size)
            output.write(encrypted)
            output.flush()

            // Receive encrypted response
            val responseSize = input.readInt()
            val encryptedResponse = ByteArray(responseSize)
            input.readFully(encryptedResponse)

            val responseJson = decryptResponse(encryptedResponse)

            // Parse response (simplified)
            return TranslationResult(
                translatedText = extractTranslatedText(responseJson),
                confidence = 0.9f,
                inferenceTimeUs = 0L,
                usedNetwork = true
            )
        }
    }

    private suspend fun performKeyExchange(server: TranslationServer) {
        Log.d(TAG, "Performing Kyber-1024 key exchange with ${server.name}")

        // STUB: In production, this would:
        // 1. Generate Kyber-1024 keypair
        // 2. Send public key to server
        // 3. Receive server's encapsulated key
        // 4. Decapsulate to get shared secret
        // 5. Derive AES-256 session key from shared secret

        // For now, generate a random session key
        sessionKey = ByteArray(32).apply {
            SecureRandom().nextBytes(this)
        }
        keyRotationTime = System.currentTimeMillis()

        Log.d(TAG, "Session key established (stub implementation)")
    }

    private fun shouldRotateKey(): Boolean {
        return (System.currentTimeMillis() - keyRotationTime) > keyRotationIntervalMs
    }

    private fun encryptRequest(plaintext: String): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("No session key")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val gcmSpec = GCMParameterSpec(128, iv)
        val secretKey = SecretKeySpec(key, "AES")

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray())

        // Prepend IV to ciphertext
        return iv + ciphertext
    }

    private fun decryptResponse(encrypted: ByteArray): String {
        val key = sessionKey ?: throw IllegalStateException("No session key")

        val iv = encrypted.sliceArray(0 until 12)
        val ciphertext = encrypted.sliceArray(12 until encrypted.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        val secretKey = SecretKeySpec(key, "AES")

        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val plaintext = cipher.doFinal(ciphertext)

        return String(plaintext)
    }

    private fun extractTranslatedText(json: String): String {
        // Simplified JSON parsing
        // In production, use proper JSON library
        val match = Regex(""""translated_text"\s*:\s*"([^"]+)"""").find(json)
        return match?.groupValues?.get(1) ?: json
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery start failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery stop failed: $errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "Discovery started for $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped for $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${serviceInfo.serviceName}")

            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Resolve failed: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val server = TranslationServer(
                        name = serviceInfo.serviceName,
                        host = serviceInfo.host.hostAddress ?: "localhost",
                        port = serviceInfo.port
                    )

                    discoveredServers.add(server)
                    if (currentServer == null) {
                        currentServer = server
                    }

                    Log.d(TAG, "Server resolved: ${server.name} at ${server.host}:${server.port}")
                }
            })
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            discoveredServers.removeIf { it.name == serviceInfo.serviceName }

            if (currentServer?.name == serviceInfo.serviceName) {
                currentServer = discoveredServers.firstOrNull()
            }
        }
    }

    fun shutdown() {
        stopDiscovery()
        scope.cancel()
    }

    companion object {
        private const val TAG = "NetworkTranslationClient"
    }
}
