package org.thoughtcrime.securesms.dependencies

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import okhttp3.ConnectionSpec
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import org.signal.core.util.Base64
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.net.Network
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess
import org.thoughtcrime.securesms.push.SignalServiceTrustStore
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.thoughtcrime.securesms.testing.Get
import org.thoughtcrime.securesms.testing.Verb
import org.thoughtcrime.securesms.testing.runSync
import org.thoughtcrime.securesms.testing.success
import org.whispersystems.signalservice.api.SignalServiceDataStore
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.archive.ArchiveApi
import org.whispersystems.signalservice.api.attachment.AttachmentApi
import org.whispersystems.signalservice.api.donations.DonationsApi
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl
import org.whispersystems.signalservice.internal.configuration.SignalSvr2Url
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.net.InetAddress

/**
 * Dependency provider used for instrumentation tests (aka androidTests).
 *
 * Handles setting up a mock web server for API calls, and provides mockable versions of [SignalServiceNetworkAccess].
 */
class InstrumentationApplicationDependencyProvider(val application: Application, private val default: ApplicationDependencyProvider) : AppDependencies.Provider by default {

  private val serviceTrustStore: TrustStore
  private val uncensoredConfiguration: SignalServiceConfiguration
  private val serviceNetworkAccessMock: SignalServiceNetworkAccess
  private val recipientCache: LiveRecipientCache
  private var signalServiceMessageSender: SignalServiceMessageSender? = null
  private var billingApi: BillingApi = mockk()
  private var accountApi: AccountApi = mockk()

  init {
    runSync {
      webServer = MockWebServer()
      webServer.start(InetAddress.getByAddress(byteArrayOf(0x7f, 0x0, 0x0, 0x1)), 8080)

      baseUrl = webServer.url("").toString()

      addMockWebRequestHandlers(
        Get("/v1/websocket/?login=") {
          MockResponse().success().withWebSocketUpgrade(mockIdentifiedWebSocket)
        },
        Get("/v1/websocket", {
          val path = it.path
          return@Get path == null || !path.contains("login")
        }) {
          MockResponse().success().withWebSocketUpgrade(object : WebSocketListener() {})
        }
      )
    }

    webServer.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val handler = handlers.firstOrNull { it.requestPredicate(request) }
        return handler?.responseFactory?.invoke(request) ?: MockResponse().setResponseCode(500)
      }
    }

    serviceTrustStore = SignalServiceTrustStore(application)
    uncensoredConfiguration = SignalServiceConfiguration(
      signalServiceUrls = arrayOf(SignalServiceUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      signalCdnUrlMap = mapOf(
        0 to arrayOf(SignalCdnUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
        2 to arrayOf(SignalCdnUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT))
      ),
      signalStorageUrls = arrayOf(SignalStorageUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      signalCdsiUrls = arrayOf(SignalCdsiUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      signalSvr2Urls = arrayOf(SignalSvr2Url(baseUrl, serviceTrustStore, "localhost", ConnectionSpec.CLEARTEXT)),
      networkInterceptors = emptyList(),
      socketFactory = Network.socketFactory,
      proxySelector = Network.proxySelectorForSocks,
      dns = Network.dns,
      systemHttpProxy = Optional.empty(),
      zkGroupServerPublicParams = Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS),
      genericServerPublicParams = Base64.decode(BuildConfig.GENERIC_SERVER_PUBLIC_PARAMS),
      backupServerPublicParams = Base64.decode(BuildConfig.BACKUP_SERVER_PUBLIC_PARAMS),
      censored = false
    )

    serviceNetworkAccessMock = mockk()

    every { serviceNetworkAccessMock.isCensored() } returns false
    every { serviceNetworkAccessMock.isCensored(any()) } returns false
    every { serviceNetworkAccessMock.isCountryCodeCensoredByDefault(any()) } returns false
    every { serviceNetworkAccessMock.getConfiguration() } returns uncensoredConfiguration
    every { serviceNetworkAccessMock.getConfiguration(any()) } returns uncensoredConfiguration
    every { serviceNetworkAccessMock.uncensoredConfiguration } returns uncensoredConfiguration

    recipientCache = LiveRecipientCache(application) { r -> r.run() }
  }

  override fun provideBillingApi(): BillingApi = billingApi

  override fun provideAccountApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket): AccountApi = accountApi

  override fun provideSignalServiceNetworkAccess(): SignalServiceNetworkAccess {
    return serviceNetworkAccessMock
  }

  override fun provideRecipientCache(): LiveRecipientCache {
    return recipientCache
  }

  override fun provideArchiveApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): ArchiveApi {
    return mockk()
  }

  override fun provideDonationsApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket): DonationsApi {
    return mockk()
  }

  override fun provideSignalServiceMessageSender(
    protocolStore: SignalServiceDataStore,
    pushServiceSocket: PushServiceSocket,
    attachmentApi: AttachmentApi,
    messageApi: MessageApi,
    keysApi: KeysApi
  ): SignalServiceMessageSender {
    if (signalServiceMessageSender == null) {
      signalServiceMessageSender = spyk(objToCopy = default.provideSignalServiceMessageSender(protocolStore, pushServiceSocket, attachmentApi, messageApi, keysApi))
    }
    return signalServiceMessageSender!!
  }

  class MockWebSocket : WebSocketListener() {
    private val TAG = "MockWebSocket"

    var webSocket: WebSocket? = null
      private set

    override fun onOpen(webSocket: WebSocket, response: Response) {
      Log.i(TAG, "onOpen(${webSocket.hashCode()})")
      this.webSocket = webSocket
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      Log.i(TAG, "onClosing(${webSocket.hashCode()}): $code, $reason")
      this.webSocket = null
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      Log.i(TAG, "onClosed(${webSocket.hashCode()}): $code, $reason")
      this.webSocket = null
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      Log.w(TAG, "onFailure(${webSocket.hashCode()})", t)
      this.webSocket = null
    }
  }

  companion object {
    lateinit var webServer: MockWebServer
      private set
    lateinit var baseUrl: String
      private set

    val mockIdentifiedWebSocket = MockWebSocket()

    private val handlers: MutableList<Verb> = mutableListOf()

    fun addMockWebRequestHandlers(vararg verbs: Verb) {
      handlers.addAll(verbs)
    }

    fun injectWebSocketMessage(value: ByteString) {
      mockIdentifiedWebSocket.webSocket!!.send(value)
    }

    fun clearHandlers() {
      handlers.clear()
    }
  }
}
