/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.util

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

class BlacklistingTrustManagerTest {

  private val delegate = mockk<X509ExtendedTrustManager>(relaxed = true)
  private val trustManager = BlacklistingTrustManager(delegate)

  @Test
  fun `default trust store is wrapped with the blacklist`() {
    val trustManager = BlacklistingTrustManager.createForDefaultTrustStore()

    assertThat(trustManager.acceptedIssuers).isNotEmpty()
  }

  @Test
  fun `extended delegate is accepted by the factory`() {
    val extendedDelegate = mockk<X509ExtendedTrustManager>(relaxed = true)
    val trustManager = BlacklistingTrustManager.createFor(arrayOf(extendedDelegate))

    trustManager.acceptedIssuers

    verify { extendedDelegate.acceptedIssuers }
  }

  @Test
  fun `factory rejects a basic delegate without selecting a later extended delegate`() {
    val basicDelegate = mockk<X509TrustManager>()
    val extendedDelegate = mockk<X509ExtendedTrustManager>()

    val failure = assertThrows(IllegalArgumentException::class.java) {
      BlacklistingTrustManager.createFor(arrayOf(basicDelegate, extendedDelegate))
    }

    assertThat(failure.message.orEmpty()).contains(basicDelegate.javaClass.name)
    assertThat(failure.message.orEmpty()).contains(X509ExtendedTrustManager::class.java.simpleName)
  }

  @Test
  fun `blacklist matches the published eMudhra intermediates`() {
    val intermediates = loadImpactedIntermediates()
    assertThat(intermediates).hasSize(16)

    val publishedKeys = intermediates.map { it.spkiSha256.lowercase() }.toSet()

    assertThat(BlacklistingTrustManager.BLACKLISTED_PUBLIC_KEY_SHA256).isEqualTo(publishedKeys)
  }

  @Test
  fun `every published eMudhra intermediate is rejected even when the delegate trusts it`() {
    for (intermediate in loadImpactedIntermediates()) {
      val failure = runCatching { trustManager.checkServerTrusted(arrayOf(intermediate.certificate()), AUTH_TYPE) }.exceptionOrNull()

      assertThat(failure, "${intermediate.name} via ${intermediate.issuerOperator}").isNotNull().isInstanceOf<CertificateException>()
    }
  }

  @Test
  fun `a rejected intermediate cannot poison the delegate cache`() {
    val cachingDelegate = CachingTrustManager()
    val trustManager = BlacklistingTrustManager(cachingDelegate)
    val leaf = readCertificate("ias.cert")
    val compromisedIntermediate = loadImpactedIntermediates().first().certificate()

    assertFailure {
      trustManager.checkServerTrusted(arrayOf(leaf, compromisedIntermediate), AUTH_TYPE)
    }.isInstanceOf<CertificateException>()
    assertThat(cachingDelegate.hasCachedIntermediate).isFalse()

    assertFailure {
      trustManager.checkServerTrusted(arrayOf(leaf), AUTH_TYPE)
    }.isInstanceOf<CertificateException>()
  }

  @Test
  fun `a certificate that is not blacklisted is accepted and the delegate is consulted`() {
    val certificate = readCertificate("ias.cert")

    trustManager.checkServerTrusted(arrayOf(certificate), AUTH_TYPE)

    verify { delegate.checkServerTrusted(any(), AUTH_TYPE) }
  }

  @Test
  fun `extended delegate receives server socket and engine context`() {
    val chain = arrayOf(readCertificate("ias.cert"))
    val socket = Socket()
    val engine = mockk<SSLEngine>()

    trustManager.checkServerTrusted(chain, AUTH_TYPE, socket)
    trustManager.checkServerTrusted(chain, AUTH_TYPE, engine)

    verify(exactly = 1) { delegate.checkServerTrusted(chain, AUTH_TYPE, socket) }
    verify(exactly = 1) { delegate.checkServerTrusted(chain, AUTH_TYPE, engine) }
    verify(exactly = 0) { delegate.checkServerTrusted(chain, AUTH_TYPE) }
  }

  @Test
  fun `extended delegate receives client socket and engine context`() {
    val chain = arrayOf(readCertificate("ias.cert"))
    val socket = Socket()
    val engine = mockk<SSLEngine>()

    trustManager.checkClientTrusted(chain, AUTH_TYPE, socket)
    trustManager.checkClientTrusted(chain, AUTH_TYPE, engine)

    verify(exactly = 1) { delegate.checkClientTrusted(chain, AUTH_TYPE, socket) }
    verify(exactly = 1) { delegate.checkClientTrusted(chain, AUTH_TYPE, engine) }
    verify(exactly = 0) { delegate.checkClientTrusted(chain, AUTH_TYPE) }
  }

  @Test
  fun `extended server checks reject blacklisted certificates before delegation`() {
    val chain = arrayOf(loadImpactedIntermediates().first().certificate())

    assertFailure {
      trustManager.checkServerTrusted(chain, AUTH_TYPE, Socket())
    }.isInstanceOf<CertificateException>()
    assertFailure {
      trustManager.checkServerTrusted(chain, AUTH_TYPE, mockk<SSLEngine>())
    }.isInstanceOf<CertificateException>()

    verify { delegate wasNot Called }
  }

  @Test
  fun `a rejection from the delegate is propagated`() {
    every { delegate.checkServerTrusted(any(), any()) } throws CertificateException("untrusted")

    assertFailure {
      trustManager.checkServerTrusted(arrayOf(readCertificate("ias.cert")), AUTH_TYPE)
    }.isInstanceOf<CertificateException>()
  }

  @Test
  fun `connection-aware delegate rejections propagate without retry or downgrade`() {
    val chain = arrayOf(readCertificate("ias.cert"))
    val socket = Socket()
    val engine = mockk<SSLEngine>()
    val socketFailure = CertificateException("untrusted via socket")
    val engineFailure = CertificateException("untrusted via engine")
    every { delegate.checkServerTrusted(chain, AUTH_TYPE, socket) } throws socketFailure
    every { delegate.checkServerTrusted(chain, AUTH_TYPE, engine) } throws engineFailure

    assertFailure {
      trustManager.checkServerTrusted(chain, AUTH_TYPE, socket)
    }.isSameInstanceAs(socketFailure)

    assertFailure {
      trustManager.checkServerTrusted(chain, AUTH_TYPE, engine)
    }.isSameInstanceAs(engineFailure)

    verify(exactly = 1) { delegate.checkServerTrusted(chain, AUTH_TYPE, socket) }
    verify(exactly = 1) { delegate.checkServerTrusted(chain, AUTH_TYPE, engine) }
    verify(exactly = 0) { delegate.checkServerTrusted(any(), any()) }
  }

  private fun loadImpactedIntermediates(): List<ImpactedIntermediate> {
    val json = checkNotNull(javaClass.classLoader.getResourceAsStream(FIXTURE)) { "Missing $FIXTURE" }
      .bufferedReader()
      .use { it.readText() }

    return Json { ignoreUnknownKeys = true }.decodeFromString(json)
  }

  private fun readCertificate(resource: String): X509Certificate {
    return checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "Missing $resource" }
      .use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
  }

  /** One entry of the upstream impacted-intermediates.json. Fields we do not need are ignored. */
  @Serializable
  private data class ImpactedIntermediate(
    val name: String,
    @SerialName("issuer_operator") val issuerOperator: String,
    @SerialName("spki_sha256") val spkiSha256: String,
    val pem: String
  ) {
    fun certificate(): X509Certificate {
      return CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate
    }
  }

  /** Models a validating trust manager that caches intermediates from successful validations. */
  private class CachingTrustManager : X509ExtendedTrustManager() {
    var hasCachedIntermediate = false
      private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = validateServerChain(chain)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) = validateServerChain(chain)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) = validateServerChain(chain)

    private fun validateServerChain(chain: Array<out X509Certificate>) {
      if (chain.size > 1) {
        hasCachedIntermediate = true
      } else if (!hasCachedIntermediate) {
        throw CertificateException("Missing intermediate")
      }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
  }

  companion object {
    /**
     * Verbatim copy of the impacted-intermediates.json that Mozilla attached to its guidance for trust store operators at
     * https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE (incident report:
     * https://bugzilla.mozilla.org/show_bug.cgi?id=2083449). Also mirrored at
     * https://emudhra.we-have-a-problem.com/impacted-intermediates.json
     */
    private const val FIXTURE = "emudhra-impacted-intermediates.json"
    private const val AUTH_TYPE = "ECDHE_ECDSA"
  }
}
