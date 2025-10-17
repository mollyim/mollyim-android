package org.thoughtcrime.securesms.messages

import android.app.Application
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupsV2ProcessingLock
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.jobs.PushProcessMessageErrorJob
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.messages.MessageDecryptor.FollowUpOperation
import org.thoughtcrime.securesms.messages.protocol.BufferedProtocolStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.SafeForegroundService
import org.thoughtcrime.securesms.util.AlarmSleepTimer
import org.thoughtcrime.securesms.util.AppForegroundObserver
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.asChain
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.SleepTimer
import org.whispersystems.signalservice.api.util.UptimeSleepTimer
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException
import org.whispersystems.signalservice.internal.push.Envelope
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.round
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The application-level manager of our incoming message processing.
 *
 * This class is responsible for keeping the authenticated websocket open based on the app's state for incoming messages and
 * observing new inbound messages received over the websocket.
 */
class IncomingMessageObserver(
  private val context: Application,
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {

  companion object {
    private val TAG = Log.tag(IncomingMessageObserver::class.java)

    private const val WEB_SOCKET_KEEP_ALIVE_TOKEN = "MessageRetrieval"

    /** How long we wait for the websocket to time out before we try to connect again. */
    private val websocketReadTimeout: Long
      get() = if (censored) 30.seconds.inWholeMilliseconds else 1.minutes.inWholeMilliseconds

    /** How long the websocket is allowed to keep running after the user backgrounds the app. Higher numbers allow us to rely on FCM less. */
    private val maxBackgroundTime: Long
      get() = if (censored) 10.seconds.inWholeMilliseconds else 2.minutes.inWholeMilliseconds

    const val FOREGROUND_ID = 313399

    private val censored: Boolean
      get() = AppDependencies.signalServiceNetworkAccess.isCensored()
  }

  private val decryptionDrainedListeners: MutableList<Runnable> = CopyOnWriteArrayList()

  @Volatile
  private var networkIsActive = true

  private val connectionDecisionSemaphore = Semaphore(0)
  private val networkConnectionListener = NetworkConnectionListener(
    connectivityManager = ServiceUtil.getConnectivityManager(context),
    onNetworkChange = { state ->
      // MOLLY: Accessing libsignalNetwork applies proxy configuration on access
      AppDependencies.libsignalNetwork.onNetworkChange()
      if (state.isReady) {
        networkIsActive = true
      } else {
        Log.w(TAG, "Lost network connection. Resetting the drained state.")
        decryptionDrained = false
        authWebSocket.disconnect()
        // TODO [no-more-rest] Move the connection listener to a neutral location so this isn't passed in
        unauthWebSocket.disconnect()
        networkIsActive = false
      }
      releaseConnectionDecisionSemaphore()
    },
  )

  private val messageContentProcessor = MessageContentProcessor(context)

  data class AppState(
    val isForeground: Boolean,
    val lastInteractionTime: Long
  )
  private var appState = AppState(isForeground = false, lastInteractionTime = System.currentTimeMillis())
  private var webSocketStateDisposable = Disposable.disposed()

  @Volatile
  private var terminated = false

  @Volatile
  var decryptionDrained = false
    private set

  init {
    MessageRetrievalThread().start()

    val registered = SignalStore.account.isRegistered
    val pushAvailable = SignalStore.account.pushAvailable
    val forceWebsocket = SignalStore.internal.isWebsocketModeForced

    if (registered && (!pushAvailable || forceWebsocket)) {
      ForegroundService.startIfNotRunning(context)
    }

    AppForegroundObserver.addListener(object : AppForegroundObserver.Listener {
      override fun onForeground() {
        SignalExecutors.BOUNDED.execute { onAppForegrounded() }
      }

      override fun onBackground() {
        SignalExecutors.BOUNDED.execute { onAppBackgrounded() }
      }
    })

    networkConnectionListener.register()

    webSocketStateDisposable = authWebSocket
      .state
      .observeOn(Schedulers.computation())
      .subscribeBy {
        if (it == WebSocketConnectionState.CONNECTED) {
          releaseConnectionDecisionSemaphore()
        }
      }

    authWebSocket.addKeepAliveChangeListener {
      SignalExecutors.BOUNDED.execute {
        releaseConnectionDecisionSemaphore()
      }
    }
  }

  fun notifyRegistrationStateChanged() {
    releaseConnectionDecisionSemaphore()
  }

  fun notifyRestoreDecisionMade() {
    Log.i(TAG, "Restore decision made, can restart network and process messages")
    AppDependencies.resetNetwork(restartMessageObserver = false)
  }

  fun addDecryptionDrainedListener(listener: Runnable) {
    decryptionDrainedListeners.add(listener)
    if (decryptionDrained) {
      listener.run()
    }
  }

  fun removeDecryptionDrainedListener(listener: Runnable) {
    decryptionDrainedListeners.remove(listener)
  }

  private fun onAppForegrounded() {
    BackgroundService.start(context)
    appState = appState.copy(isForeground = true)
    releaseConnectionDecisionSemaphore()
  }

  private fun onAppBackgrounded() {
    val now = System.currentTimeMillis()
    appState = appState.copy(isForeground = false, lastInteractionTime = now)
    releaseConnectionDecisionSemaphore()
  }

  private fun isConnectionNecessary(): Boolean {
    val appStateSnapshot = appState
    val isForeground = appStateSnapshot.isForeground
    val lastInteractionTime = appStateSnapshot.lastInteractionTime
    val timeIdle = if (isForeground) 0 else System.currentTimeMillis() - lastInteractionTime

    val registered = SignalStore.account.isRegistered
    val pushAvailable = SignalStore.account.pushAvailable
    val hasNetwork = networkIsActive
    val hasProxy = AppDependencies.networkManager.isProxyEnabled
    val forceWebsocket = SignalStore.internal.isWebsocketModeForced
    val websocketAlreadyOpen = isConnectionAvailable()

    val lastInteractionString = if (isForeground) "N/A" else timeIdle.toString() + " ms (" + (if (timeIdle < maxBackgroundTime) "within limit" else "over limit") + ")"
    val conclusion = registered &&
      (isForeground || timeIdle < maxBackgroundTime || !pushAvailable) &&
      hasNetwork

    val needsConnectionString = if (conclusion) "Needs Connection" else "Does Not Need Connection"

    Log.d(TAG, "[$needsConnectionString] Network: $hasNetwork, Foreground: $isForeground, Time Since Last Interaction: $lastInteractionString, PushAvailable: $pushAvailable, WS Open or Keep-alives: $websocketAlreadyOpen, Registered: $registered, Proxy: $hasProxy, Force websocket: $forceWebsocket")
    return conclusion
  }

  private fun isConnectionAvailable(): Boolean {
    return SignalStore.account.isRegistered && (authWebSocket.stateSnapshot == WebSocketConnectionState.CONNECTED || (authWebSocket.shouldSendKeepAlives() && networkIsActive))
  }

  private fun releaseConnectionDecisionSemaphore() {
    connectionDecisionSemaphore.drainPermits()
    connectionDecisionSemaphore.release()
  }

  private fun waitForConnectionNecessary() {
    while (!isConnectionNecessary() && !isConnectionAvailable()) {
      if (connectionDecisionSemaphore.drainPermits() == 0) {
        connectionDecisionSemaphore.acquireUninterruptibly()
      }
    }
  }

  fun terminateAsync() {
    Log.w(TAG, "Termination Enqueued! ${this.hashCode()}", Throwable())
    networkConnectionListener.unregister()
    webSocketStateDisposable.dispose()
    ForegroundService.stopIfRunning(context)
    SignalExecutors.BOUNDED.execute {
      Log.w(TAG, "Beginning termination. ${this.hashCode()}")
      terminated = true
      Log.w(TAG, "Disconnecting auth socket as part of termination")
      authWebSocket.disconnect()
    }
  }

  @VisibleForTesting
  fun processEnvelope(bufferedProtocolStore: BufferedProtocolStore, envelope: Envelope, serverDeliveredTimestamp: Long): List<FollowUpOperation>? {
    return when (envelope.type) {
      Envelope.Type.SERVER_DELIVERY_RECEIPT -> {
        processReceipt(envelope)
        null
      }

      Envelope.Type.PREKEY_BUNDLE,
      Envelope.Type.CIPHERTEXT,
      Envelope.Type.UNIDENTIFIED_SENDER,
      Envelope.Type.PLAINTEXT_CONTENT -> {
        processMessage(bufferedProtocolStore, envelope, serverDeliveredTimestamp)
      }

      else -> {
        Log.w(TAG, "Received envelope of unknown type: " + envelope.type)
        null
      }
    }
  }

  private fun processMessage(bufferedProtocolStore: BufferedProtocolStore, envelope: Envelope, serverDeliveredTimestamp: Long): List<FollowUpOperation> {
    val localReceiveMetric = SignalLocalMetrics.MessageReceive.start()
    val result = MessageDecryptor.decrypt(context, bufferedProtocolStore, envelope, serverDeliveredTimestamp)
    localReceiveMetric.onEnvelopeDecrypted()

    SignalLocalMetrics.MessageLatency.onMessageReceived(envelope.serverTimestamp!!, serverDeliveredTimestamp, envelope.urgent!!)
    when (result) {
      is MessageDecryptor.Result.Success -> {
        val job = PushProcessMessageJob.processOrDefer(messageContentProcessor, result, localReceiveMetric)
        if (job != null) {
          return result.followUpOperations + FollowUpOperation { job.asChain() }
        }
      }
      is MessageDecryptor.Result.Error -> {
        return result.followUpOperations + FollowUpOperation {
          PushProcessMessageErrorJob(
            result.toMessageState(),
            result.errorMetadata.toExceptionMetadata(),
            result.envelope.timestamp!!
          ).asChain()
        }
      }
      is MessageDecryptor.Result.Ignore -> {
        // No action needed
      }
      else -> {
        throw AssertionError("Unexpected result! ${result.javaClass.simpleName}")
      }
    }

    return result.followUpOperations
  }

  private fun processReceipt(envelope: Envelope) {
    val serviceId = ServiceId.parseOrNull(envelope.sourceServiceId)
    if (serviceId == null) {
      Log.w(TAG, "Invalid envelope sourceServiceId!")
      return
    }

    val senderId = RecipientId.from(serviceId)

    Log.i(TAG, "Received server receipt. Sender: $senderId, Device: ${envelope.sourceDevice}, Timestamp: ${envelope.timestamp}")
    SignalDatabase.messages.incrementDeliveryReceiptCount(envelope.timestamp!!, senderId, System.currentTimeMillis())
    SignalDatabase.messageLog.deleteEntryForRecipient(envelope.timestamp!!, senderId, envelope.sourceDevice!!)
  }

  private fun MessageDecryptor.Result.toMessageState(): MessageState {
    return when (this) {
      is MessageDecryptor.Result.DecryptionError -> MessageState.DECRYPTION_ERROR
      is MessageDecryptor.Result.Ignore -> MessageState.NOOP
      is MessageDecryptor.Result.InvalidVersion -> MessageState.INVALID_VERSION
      is MessageDecryptor.Result.LegacyMessage -> MessageState.LEGACY_MESSAGE
      is MessageDecryptor.Result.Success -> MessageState.DECRYPTED_OK
      is MessageDecryptor.Result.UnsupportedDataMessage -> MessageState.UNSUPPORTED_DATA_MESSAGE
    }
  }

  private fun MessageDecryptor.ErrorMetadata.toExceptionMetadata(): ExceptionMetadata {
    return ExceptionMetadata(
      this.sender,
      this.senderDevice,
      this.groupId
    )
  }

  private inner class MessageRetrievalThread : Thread("MessageRetrievalService"), Thread.UncaughtExceptionHandler {

    private var sleepTimer: SleepTimer
    private val canProcessMessages: Boolean

    init {
      Log.i(TAG, "Initializing! (${this.hashCode()})")
      uncaughtExceptionHandler = this

      sleepTimer = if (!SignalStore.account.pushAvailable || SignalStore.internal.isWebsocketModeForced) AlarmSleepTimer(context) else UptimeSleepTimer()

      canProcessMessages = !SignalStore.registration.restoreDecisionState.isDecisionPending
    }

    override fun run() {
      var attempts = 0

      while (!terminated) {
        Log.i(TAG, "Waiting for websocket state change....")
        if (attempts > 1) {
          val backoff = BackoffUtil.exponentialBackoff(attempts, TimeUnit.SECONDS.toMillis(30))
          Log.w(TAG, "Too many failed connection attempts,  attempts: $attempts backing off: $backoff")
          sleepTimer.sleep(backoff)
        }

        waitForConnectionNecessary()
        Log.i(TAG, "Making websocket connection....")

        val webSocketDisposable = authWebSocket.state.subscribe { state: WebSocketConnectionState ->
          Log.d(TAG, "WebSocket State: $state")

          // Any change to a non-connected state means that we are not drained
          if (state != WebSocketConnectionState.CONNECTED) {
            decryptionDrained = false
          }
        }

        try {
          authWebSocket.connect()
          var isConnectionNecessary = false
          while (!terminated && (isConnectionNecessary().also { isConnectionNecessary = it } || isConnectionAvailable())) {
            if (isConnectionNecessary) {
              authWebSocket.registerKeepAliveToken(WEB_SOCKET_KEEP_ALIVE_TOKEN)
            } else {
              authWebSocket.removeKeepAliveToken(WEB_SOCKET_KEEP_ALIVE_TOKEN)
            }

            try {
              if (canProcessMessages) {
                Log.d(TAG, "Reading message...")

                val hasMore = authWebSocket.readMessageBatch(websocketReadTimeout, 30) { batch ->
                  Log.i(TAG, "Retrieved ${batch.size} envelopes!")
                  val bufferedStore = BufferedProtocolStore.create()

                  val startTime = System.currentTimeMillis()
                  GroupsV2ProcessingLock.acquireGroupProcessingLock().use {
                    ReentrantSessionLock.INSTANCE.acquire().use {
                      batch.forEach { response ->
                        Log.d(TAG, "Beginning database transaction...")
                        val followUpOperations = SignalDatabase.runInTransaction { db ->
                          val followUps: List<FollowUpOperation>? = processEnvelope(bufferedStore, response.envelope, response.serverDeliveredTimestamp)
                          bufferedStore.flushToDisk()
                          followUps
                        }
                        Log.d(TAG, "Ended database transaction.")

                        if (followUpOperations != null) {
                          Log.d(TAG, "Running ${followUpOperations.size} follow-up operations...")
                          val jobs = followUpOperations.mapNotNull { it.run() }
                          AppDependencies.jobManager.addAllChains(jobs)
                        }

                        authWebSocket.sendAck(response)
                      }
                    }
                  }
                  val duration = System.currentTimeMillis() - startTime
                  val timePerMessage: Float = duration / batch.size.toFloat()
                  Log.d(TAG, "Decrypted ${batch.size} envelopes in $duration ms (~${round(timePerMessage * 100) / 100} ms per message)")
                }
                attempts = 0
                SignalLocalMetrics.PushWebsocketFetch.onProcessedBatch()

                if (!hasMore && !decryptionDrained) {
                  Log.i(TAG, "Decryptions newly-drained.")
                  decryptionDrained = true

                  for (listener in decryptionDrainedListeners.toList()) {
                    listener.run()
                  }
                } else if (!hasMore) {
                  Log.w(TAG, "Got tombstone, but we thought the network was already drained!")
                }
              } else {
                Log.d(TAG, "Reading and dropping message...")
                authWebSocket.readMessageBatch(websocketReadTimeout, 30) { batch ->
                  Log.w(TAG, "Retrieved ${batch.size} envelopes but dropping until we can finish backup restore.")
                }
                attempts = 0
              }
            } catch (e: WebSocketUnavailableException) {
              Log.i(TAG, "Pipe unexpectedly unavailable, connecting")
              authWebSocket.connect()
            } catch (e: TimeoutException) {
              Log.w(TAG, "Application level read timeout...")
              attempts = 0
            }
          }

          if (!appState.isForeground) {
            BackgroundService.stop(context)
          }
        } catch (e: Throwable) {
          attempts++
          Log.w(TAG, e)
        } finally {
          Log.w(TAG, "Disconnecting auth websocket")
          authWebSocket.disconnect()
          webSocketDisposable.dispose()
          decryptionDrained = false
        }
        Log.i(TAG, "Looping...")
      }
      Log.w(TAG, "Terminated! (${this.hashCode()})")
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
      Log.w(TAG, "Uncaught exception in message thread!", e)
    }
  }

  class ForegroundService : SafeForegroundService() {

    companion object {
      private val startPending = AtomicBoolean(false)
      private val stopPending = AtomicBoolean(false)

      fun startIfNotRunning(context: Context) {
        if (startPending.compareAndSet(false, true)) {
          SignalExecutors.SERIAL.execute {
            val started = start(context, ForegroundService::class.java)
            if (!started) {
              Log.w(TAG, "Unable to start foreground service for websocket!")
            }
            startPending.set(false)
          }
        }
      }

      fun stopIfRunning(context: Context) {
        if (stopPending.compareAndSet(false, true)) {
          SignalExecutors.SERIAL.execute {
            stop(context, ForegroundService::class.java)
            stopPending.set(false)
          }
        }
      }
    }

    override val tag: String = TAG
    override val notificationId: Int = FOREGROUND_ID

    override fun getForegroundNotification(intent: Intent): Notification {
      val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.BACKGROUND)
        .setContentTitle(applicationContext.getString(R.string.IncomingMessageObserver_websocket_service))
        .setContentText(applicationContext.getString(R.string.MessageRetrievalService_ready_to_receive_messages))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setWhen(0)
        .setSmallIcon(R.drawable.ic_notification_websocket)
        .build()
      return notification
    }
  }

  /**
   * A service that exists just to encourage the system to keep our process alive a little longer.
   */
  class BackgroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      Log.d(TAG, "Background service started.")
      return START_STICKY
    }

    override fun onDestroy() {
      Log.d(TAG, "Background service destroyed.")
    }

    companion object {
      fun start(context: Context) {
        try {
          context.startService(Intent(context, BackgroundService::class.java))
        } catch (e: Exception) {
          Log.w(TAG, "Failed to start background service.", e)
        }
      }

      fun stop(context: Context) {
        context.stopService(Intent(context, BackgroundService::class.java))
      }
    }
  }
}
