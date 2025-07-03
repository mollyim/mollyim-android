package org.thoughtcrime.securesms.push

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.TlsVersion
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.DeprecatedClientPreventionInterceptor
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.net.Networking
import org.thoughtcrime.securesms.net.RemoteDeprecationDetectorInterceptor
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor
import org.thoughtcrime.securesms.net.StorageServiceSizeLoggingInterceptor
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.internal.configuration.HttpProxy
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl
import org.whispersystems.signalservice.internal.configuration.SignalSvr2Url
import java.io.IOException
import java.util.Optional
import android.net.Proxy as AndroidProxy

/**
 * Provides a [SignalServiceConfiguration] to be used with our service layer.
 * If you're looking for a place to start, look at [getConfiguration].
 */
class SignalServiceNetworkAccess(context: Context) {
  companion object {
    private val TAG = Log.tag(SignalServiceNetworkAccess::class.java)

    // MOLLY: DNS object moved to Networking.kt

    private fun String.stripProtocol(): String {
      return this.removePrefix("https://")
    }

    private const val COUNTRY_CODE_EGYPT = 20
    private const val COUNTRY_CODE_UAE = 971
    private const val COUNTRY_CODE_OMAN = 968
    private const val COUNTRY_CODE_QATAR = 974
    private const val COUNTRY_CODE_IRAN = 98
    private const val COUNTRY_CODE_CHINA = 86
    private const val COUNTRY_CODE_CUBA = 53
    private const val COUNTRY_CODE_UZBEKISTAN = 998
    private const val COUNTRY_CODE_VENEZUELA = 58
    private const val COUNTRY_CODE_PAKISTAN = 92

    // MOLLY: Add new hostnames and URLs to HOSTNAMES below
    private const val SIGNALCAPTCHAS_HOST = "signalcaptchas.org"
    private const val UPDATES2_HOST = "updates2.signal.org"
    private const val G_HOST = "reflector-nrgwuv7kwq-uc.a.run.app"
    private const val F_SERVICE_HOST = "chat-signal.global.ssl.fastly.net"
    private const val F_STORAGE_HOST = "storage.signal.org.global.prod.fastly.net"
    private const val F_CDN_HOST = "cdn.signal.org.global.prod.fastly.net"
    private const val F_CDN2_HOST = "cdn2.signal.org.global.prod.fastly.net"
    private const val F_CDN3_HOST = "cdn3-signal.global.ssl.fastly.net"
    private const val F_CDSI_HOST = "cdsi-signal.global.ssl.fastly.net"
    private const val F_SVR2_HOST = "svr2-signal.global.ssl.fastly.net"
    private const val HTTPS_WWW_GOOGLE_COM = "https://www.google.com"
    private const val HTTPS_ANDROID_CLIENTS_GOOGLE_COM = "https://android.clients.google.com"
    private const val HTTPS_CLIENTS_3_GOOGLE_COM = "https://clients3.google.com"
    private const val HTTPS_CLIENTS_4_GOOGLE_COM = "https://clients4.google.com"
    private const val HTTPS_GOOGLEMAIL_COM = "https://googlemail.com"
    private const val HTTPS_GITHUB_GITHUBASSETS_COM = "https://github.githubassets.com"
    private const val HTTPS_WWW_GOOGLE_COM_EG = "https://www.google.com.eg"
    private const val HTTPS_WWW_GOOGLE_AE = "https://www.google.ae"
    private const val HTTPS_WWW_GOOGLE_COM_OM = "https://www.google.com.om"
    private const val HTTPS_WWW_GOOGLE_COM_QA = "https://www.google.com.qa"
    private const val HTTPS_WWW_GOOGLE_CO_UZ = "https://www.google.co.uz"
    private const val HTTPS_WWW_GOOGLE_CO_VE = "https://www.google.co.ve"
    private const val HTTPS_WWW_GOOGLE_COM_PK = "https://www.google.com.pk"
    private const val HTTPS_WWW_GOV_UK = "https://www.gov.uk"

    @JvmField
    val HOSTNAMES = setOf(
      BuildConfig.SIGNAL_URL.stripProtocol(),
      BuildConfig.STORAGE_URL.stripProtocol(),
      BuildConfig.SIGNAL_CDN_URL.stripProtocol(),
      BuildConfig.SIGNAL_CDN2_URL.stripProtocol(),
      BuildConfig.SIGNAL_CDN3_URL.stripProtocol(),
      BuildConfig.SIGNAL_CDSI_URL.stripProtocol(),
      // SIGNAL_SERVICE_STATUS_URL
      BuildConfig.SIGNAL_SVR2_URL.stripProtocol(),
      BuildConfig.SIGNAL_SFU_URL.stripProtocol(),
      BuildConfig.SIGNAL_STAGING_SFU_URL.stripProtocol(),
      BuildConfig.CONTENT_PROXY_HOST,
      SIGNALCAPTCHAS_HOST,
      UPDATES2_HOST,
      G_HOST,
      F_SERVICE_HOST,
      F_STORAGE_HOST,
      F_CDN_HOST,
      F_CDN2_HOST,
      F_CDN3_HOST,
      F_CDSI_HOST,
      F_SVR2_HOST,
      HTTPS_WWW_GOOGLE_COM.stripProtocol(),
      HTTPS_ANDROID_CLIENTS_GOOGLE_COM.stripProtocol(),
      HTTPS_CLIENTS_3_GOOGLE_COM.stripProtocol(),
      HTTPS_CLIENTS_4_GOOGLE_COM.stripProtocol(),
      HTTPS_GOOGLEMAIL_COM.stripProtocol(),
      HTTPS_GITHUB_GITHUBASSETS_COM.stripProtocol(),
      HTTPS_WWW_GOOGLE_COM_EG.stripProtocol(),
      HTTPS_WWW_GOOGLE_AE.stripProtocol(),
      HTTPS_WWW_GOOGLE_COM_OM.stripProtocol(),
      HTTPS_WWW_GOOGLE_COM_QA.stripProtocol(),
      HTTPS_WWW_GOOGLE_CO_UZ.stripProtocol(),
      HTTPS_WWW_GOOGLE_CO_VE.stripProtocol(),
      HTTPS_WWW_GOOGLE_COM_PK.stripProtocol(),
      HTTPS_WWW_GOV_UK.stripProtocol(),
    )

    private val GMAPS_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val GMAIL_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val PLAY_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val APP_CONNECTION_SPEC = ConnectionSpec.MODERN_TLS

    @Suppress("DEPRECATION")
    private fun getSystemHttpProxy(context: Context): HttpProxy? {
      return if (Build.VERSION.SDK_INT >= 23) {
        val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java) ?: return null

        connectivityManager
          .activeNetwork
          ?.let { connectivityManager.getLinkProperties(it)?.httpProxy }
          ?.takeIf { !it.exclusionList.contains(BuildConfig.SIGNAL_URL.stripProtocol()) }
          ?.let { proxy -> HttpProxy(proxy.host, proxy.port) }
      } else {
        val host: String? = AndroidProxy.getHost(context)
        val port: Int = AndroidProxy.getPort(context)

        if (host != null) {
          HttpProxy(host, port)
        } else {
          null
        }
      }
    }
  }

  private val serviceTrustStore: TrustStore = SignalServiceTrustStore(context)
  private val gTrustStore: TrustStore = DomainFrontingTrustStore(context)
  private val fTrustStore: TrustStore = DomainFrontingDigicertTrustStore(context)

  private val interceptors: List<Interceptor> = listOf(
    StandardUserAgentInterceptor(),
    StorageServiceSizeLoggingInterceptor(),
    RemoteDeprecationDetectorInterceptor(this::getConfiguration),
    DeprecatedClientPreventionInterceptor(),
    DeviceTransferBlockingInterceptor.getInstance()
  )

  private val zkGroupServerPublicParams: ByteArray = try {
    Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS)
  } catch (e: IOException) {
    throw AssertionError(e)
  }

  private val genericServerPublicParams: ByteArray = try {
    Base64.decode(BuildConfig.GENERIC_SERVER_PUBLIC_PARAMS)
  } catch (e: IOException) {
    throw AssertionError(e)
  }

  private val backupServerPublicParams: ByteArray = try {
    Base64.decode(BuildConfig.BACKUP_SERVER_PUBLIC_PARAMS)
  } catch (e: IOException) {
    throw AssertionError(e)
  }

  private val baseGHostConfigs: List<HostConfig> = listOf(
    HostConfig(HTTPS_WWW_GOOGLE_COM, G_HOST, GMAIL_CONNECTION_SPEC),
    HostConfig(HTTPS_ANDROID_CLIENTS_GOOGLE_COM, G_HOST, PLAY_CONNECTION_SPEC),
    HostConfig(HTTPS_CLIENTS_3_GOOGLE_COM, G_HOST, GMAPS_CONNECTION_SPEC),
    HostConfig(HTTPS_CLIENTS_4_GOOGLE_COM, G_HOST, GMAPS_CONNECTION_SPEC),
    HostConfig(HTTPS_GOOGLEMAIL_COM, G_HOST, GMAIL_CONNECTION_SPEC)
  )

  private val fUrls = arrayOf(HTTPS_GITHUB_GITHUBASSETS_COM, HTTPS_WWW_GOV_UK)

  private val fConfig: SignalServiceConfiguration = SignalServiceConfiguration(
    signalServiceUrls = fUrls.map { SignalServiceUrl(it, F_SERVICE_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    signalCdnUrlMap = mapOf(
      0 to fUrls.map { SignalCdnUrl(it, F_CDN_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
      2 to fUrls.map { SignalCdnUrl(it, F_CDN2_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
      3 to fUrls.map { SignalCdnUrl(it, F_CDN3_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray()
    ),
    signalStorageUrls = fUrls.map { SignalStorageUrl(it, F_STORAGE_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    signalCdsiUrls = fUrls.map { SignalCdsiUrl(it, F_CDSI_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    signalSvr2Urls = fUrls.map { SignalSvr2Url(it, fTrustStore, F_SVR2_HOST, APP_CONNECTION_SPEC) }.toTypedArray(),
    networkInterceptors = interceptors,
    socketFactory = Networking.socketFactory,
    proxySelector = Networking.proxySelectorForSocks,
    dns = Networking.dns,
    systemHttpProxy = Optional.empty(),
    zkGroupServerPublicParams = zkGroupServerPublicParams,
    genericServerPublicParams = genericServerPublicParams,
    backupServerPublicParams = backupServerPublicParams,
    censored = true
  )

  private val censorshipConfiguration: Map<Int, SignalServiceConfiguration> = mapOf(
    COUNTRY_CODE_EGYPT to buildGConfiguration(
      listOf(HostConfig(HTTPS_WWW_GOOGLE_COM_EG, G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_UAE to buildGConfiguration(
      listOf(HostConfig(HTTPS_WWW_GOOGLE_AE, G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_OMAN to buildGConfiguration(
      listOf(HostConfig(HTTPS_WWW_GOOGLE_COM_OM, G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_QATAR to buildGConfiguration(
      listOf(HostConfig(HTTPS_WWW_GOOGLE_COM_QA, G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_UZBEKISTAN to buildGConfiguration(
      listOf(HostConfig(HTTPS_WWW_GOOGLE_CO_UZ, G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_VENEZUELA to buildGConfiguration(
      listOf(HostConfig(HTTPS_WWW_GOOGLE_CO_VE, G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_PAKISTAN to buildGConfiguration(
      listOf(HostConfig(HTTPS_WWW_GOOGLE_COM_PK, G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_IRAN to fConfig,
    COUNTRY_CODE_CHINA to fConfig,
    COUNTRY_CODE_CUBA to fConfig
  )

  private val defaultCensoredConfiguration: SignalServiceConfiguration = buildGConfiguration(baseGHostConfigs) + fConfig

  private val defaultCensoredCountryCodes: Set<Int> = setOf(
    COUNTRY_CODE_EGYPT,
    COUNTRY_CODE_UAE,
    COUNTRY_CODE_OMAN,
    COUNTRY_CODE_QATAR,
    COUNTRY_CODE_IRAN,
    COUNTRY_CODE_CHINA,
    COUNTRY_CODE_CUBA,
    COUNTRY_CODE_UZBEKISTAN,
    COUNTRY_CODE_VENEZUELA,
    COUNTRY_CODE_PAKISTAN
  )

  val uncensoredConfiguration: SignalServiceConfiguration = SignalServiceConfiguration(
    signalServiceUrls = arrayOf(SignalServiceUrl(BuildConfig.SIGNAL_URL, serviceTrustStore)),
    signalCdnUrlMap = mapOf(
      0 to arrayOf(SignalCdnUrl(BuildConfig.SIGNAL_CDN_URL, serviceTrustStore)),
      2 to arrayOf(SignalCdnUrl(BuildConfig.SIGNAL_CDN2_URL, serviceTrustStore)),
      3 to arrayOf(SignalCdnUrl(BuildConfig.SIGNAL_CDN3_URL, serviceTrustStore))
    ),
    signalStorageUrls = arrayOf(SignalStorageUrl(BuildConfig.STORAGE_URL, serviceTrustStore)),
    signalCdsiUrls = arrayOf(SignalCdsiUrl(BuildConfig.SIGNAL_CDSI_URL, serviceTrustStore)),
    signalSvr2Urls = arrayOf(SignalSvr2Url(BuildConfig.SIGNAL_SVR2_URL, serviceTrustStore)),
    networkInterceptors = interceptors,
    socketFactory = Networking.socketFactory,
    proxySelector = Networking.proxySelectorForSocks,
    dns = Networking.dns,
    systemHttpProxy = Optional.ofNullable(getSystemHttpProxy(context)),
    zkGroupServerPublicParams = zkGroupServerPublicParams,
    genericServerPublicParams = genericServerPublicParams,
    backupServerPublicParams = backupServerPublicParams,
    censored = false
  )

  fun getConfiguration(): SignalServiceConfiguration {
    return getConfiguration(SignalStore.account.e164)
  }

  fun getConfiguration(e164: String?): SignalServiceConfiguration {
    if (e164.isNullOrEmpty()) {
      return uncensoredConfiguration
    }

    val countryCode: Int = PhoneNumberUtil.getInstance().parse(e164, null).countryCode

    return when (SignalStore.settings.censorshipCircumventionEnabled) {
      SettingsValues.CensorshipCircumventionEnabled.ENABLED -> {
        censorshipConfiguration[countryCode] ?: defaultCensoredConfiguration
      }
      SettingsValues.CensorshipCircumventionEnabled.DISABLED -> {
        uncensoredConfiguration
      }
      SettingsValues.CensorshipCircumventionEnabled.DEFAULT -> {
        if (defaultCensoredCountryCodes.contains(countryCode)) {
          censorshipConfiguration[countryCode] ?: defaultCensoredConfiguration
        } else {
          uncensoredConfiguration
        }
      }
    }
  }

  fun isCensored(): Boolean {
    return isCensored(SignalStore.account.e164)
  }

  fun isCensored(number: String?): Boolean {
    return getConfiguration(number) != uncensoredConfiguration
  }

  fun isCountryCodeCensoredByDefault(countryCode: Int): Boolean {
    return defaultCensoredCountryCodes.contains(countryCode)
  }

  private fun buildGConfiguration(
    hostConfigs: List<HostConfig>
  ): SignalServiceConfiguration {
    val serviceUrls: Array<SignalServiceUrl> = hostConfigs.map { SignalServiceUrl("${it.baseUrl}/service", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdnUrls: Array<SignalCdnUrl> = hostConfigs.map { SignalCdnUrl("${it.baseUrl}/cdn", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdn2Urls: Array<SignalCdnUrl> = hostConfigs.map { SignalCdnUrl("${it.baseUrl}/cdn2", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdn3Urls: Array<SignalCdnUrl> = hostConfigs.map { SignalCdnUrl("${it.baseUrl}/cdn3", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val storageUrls: Array<SignalStorageUrl> = hostConfigs.map { SignalStorageUrl("${it.baseUrl}/storage", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdsiUrls: Array<SignalCdsiUrl> = hostConfigs.map { SignalCdsiUrl("${it.baseUrl}/cdsi", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val svr2Urls: Array<SignalSvr2Url> = hostConfigs.map { SignalSvr2Url("${it.baseUrl}/svr2", gTrustStore, it.host, it.connectionSpec) }.toTypedArray()

    return SignalServiceConfiguration(
      signalServiceUrls = serviceUrls,
      signalCdnUrlMap = mapOf(
        0 to cdnUrls,
        2 to cdn2Urls,
        3 to cdn3Urls
      ),
      signalStorageUrls = storageUrls,
      signalCdsiUrls = cdsiUrls,
      signalSvr2Urls = svr2Urls,
      networkInterceptors = interceptors,
      socketFactory = Networking.socketFactory,
      proxySelector = Networking.proxySelectorForSocks,
      dns = Networking.dns,
      systemHttpProxy = Optional.empty(),
      zkGroupServerPublicParams = zkGroupServerPublicParams,
      genericServerPublicParams = genericServerPublicParams,
      backupServerPublicParams = backupServerPublicParams,
      censored = true
    )
  }

  private data class HostConfig(val baseUrl: String, val host: String, val connectionSpec: ConnectionSpec)
}
