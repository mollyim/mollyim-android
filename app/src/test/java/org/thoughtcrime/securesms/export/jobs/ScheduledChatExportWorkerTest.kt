package org.thoughtcrime.securesms.export.jobs

import android.app.NotificationManager // Keep if used for notification mocking (conceptual)
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq // Specific Mockito import
import org.mockito.Mockito.mock // Specific Mockito import
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.junit.MockitoJUnitRunner
import org.thoughtcrime.securesms.export.ChatExportHelper
import org.thoughtcrime.securesms.export.model.ExportErrorType
import org.thoughtcrime.securesms.export.model.ExportResult
import org.thoughtcrime.securesms.export.model.ExportedMessage
import java.io.IOException // Make sure this is imported

@RunWith(MockitoJUnitRunner::class)
class ScheduledChatExportWorkerTest {

    @Mock
    private lateinit var mockChatExportHelper: ChatExportHelper
    // NotificationManager mocking is conceptual and might be removed if not directly testable here
    // @Mock
    // private lateinit var mockNotificationManager: NotificationManager

    private lateinit var context: Context

    // Dummy message for mocking successful getMessagesForThread
    private val dummyMessage = ExportedMessage(1L,1L,"s1","Test Sender",1L,1L,"body","type")


    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Setup for mockChatExportHelper will be done in each test case
    }

    private fun createWorker(inputData: androidx.work.Data): ScheduledChatExportWorker {
       val workerFactory = object : androidx.work.WorkerFactory() {
           override fun createWorker(
               appContext: Context,
               workerClassName: String,
               workerParameters: WorkerParameters
           ): ListenableWorker? {
               if (workerClassName == ScheduledChatExportWorker::class.java.name) {
                   return ScheduledChatExportWorker(appContext, workerParameters, mockChatExportHelper)
               }
               return null
           }
       }

       return TestListenableWorkerBuilder<ScheduledChatExportWorker>(context)
           .setInputData(inputData)
           .setWorkerFactory(workerFactory)
           .build()
   }

    @Test
    fun `doWork with missing threadId should return Failure`() = runBlocking {
        val inputData = androidx.work.Data.Builder()
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "LOCAL_FILE")
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockChatExportHelper, never()).getMessagesForThread(anyLong())
    }

    @Test
    fun `doWork with no messages should return Success`() = runBlocking {
        val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "LOCAL_FILE")
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L)).thenReturn(ExportResult.Success(emptyList()))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L)
        verify(mockChatExportHelper, never()).saveExportToFile(anyString(), anyString(), anyString())
        verify(mockChatExportHelper, never()).sendExportToApiSuspending(anyString(), anyString())
    }

    @Test
    fun `doWork when getMessagesForThread returns DatabaseError should return Failure`() = runBlocking {
        val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "LOCAL_FILE")
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L))
            .thenReturn(ExportResult.Error(ExportErrorType.DatabaseError("DB error", "Log: DB error")))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L)
        verify(mockChatExportHelper, never()).saveExportToFile(anyString(), anyString(), anyString())
    }


    @Test
    fun `doWork LOCAL_FILE success`() = runBlocking {
        val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "LOCAL_FILE")
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L)).thenReturn(ExportResult.Success(listOf(dummyMessage)))
        `when`(mockChatExportHelper.saveExportToFile(anyString(), eq("ScheduledExport_TestChat"), eq("json")))
            .thenReturn(ExportResult.Success("/path/to/file.json"))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L)
        verify(mockChatExportHelper).saveExportToFile(anyString(), eq("ScheduledExport_TestChat"), eq("json"))
    }

    @Test
    fun `doWork LOCAL_FILE helper returns FileSystemError`() = runBlocking {
         val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "LOCAL_FILE")
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L)).thenReturn(ExportResult.Success(listOf(dummyMessage)))
        `when`(mockChatExportHelper.saveExportToFile(anyString(), anyString(), anyString()))
            .thenReturn(ExportResult.Error(ExportErrorType.FileSystemError("Disk full", "Log: disk full")))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L)
        verify(mockChatExportHelper).saveExportToFile(anyString(), anyString(), anyString())
    }

    @Test
    fun `doWork API_ENDPOINT with missing URL should return Failure`() = runBlocking {
        val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "API_ENDPOINT")
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            // API_URL is missing
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L)).thenReturn(ExportResult.Success(listOf(dummyMessage)))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L) // Verifies it gets past message fetching
        verify(mockChatExportHelper, never()).sendExportToApiSuspending(anyString(), anyString())
    }

    @Test
    fun `doWork API_ENDPOINT success`() = runBlocking {
        val apiUrl = "http://fake.api/export"
        val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "API_ENDPOINT")
            .putString(ScheduledChatExportWorker.KEY_API_URL, apiUrl)
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L)).thenReturn(ExportResult.Success(listOf(dummyMessage)))
        `when`(mockChatExportHelper.sendExportToApiSuspending(anyString(), eq(apiUrl)))
           .thenReturn(ExportResult.Success("API success"))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L)
        verify(mockChatExportHelper).sendExportToApiSuspending(anyString(), eq(apiUrl))
    }

    @Test
    fun `doWork API_ENDPOINT helper returns ApiError`() = runBlocking {
        val apiUrl = "http://fake.api/export"
        val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "API_ENDPOINT")
            .putString(ScheduledChatExportWorker.KEY_API_URL, apiUrl)
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L)).thenReturn(ExportResult.Success(listOf(dummyMessage)))
        `when`(mockChatExportHelper.sendExportToApiSuspending(anyString(), eq(apiUrl)))
           .thenReturn(ExportResult.Error(ExportErrorType.ApiError(401, "Auth failed", "Log: Auth failed")))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L)
        verify(mockChatExportHelper).sendExportToApiSuspending(anyString(), eq(apiUrl))
    }

    @Test
    fun `doWork API_ENDPOINT helper throws IOException (NetworkError)`() = runBlocking {
        val apiUrl = "http://fake.api/export"
        val inputData = androidx.work.Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, 1L)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, "JSON")
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, "API_ENDPOINT")
            .putString(ScheduledChatExportWorker.KEY_API_URL, apiUrl)
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, "TestChat")
            .build()

        `when`(mockChatExportHelper.getMessagesForThread(1L)).thenReturn(ExportResult.Success(listOf(dummyMessage)))
        `when`(mockChatExportHelper.sendExportToApiSuspending(anyString(), eq(apiUrl)))
           .thenThrow(IOException("Network unreachable"))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(mockChatExportHelper).getMessagesForThread(1L)
        verify(mockChatExportHelper).sendExportToApiSuspending(anyString(), eq(apiUrl))
    }
}
