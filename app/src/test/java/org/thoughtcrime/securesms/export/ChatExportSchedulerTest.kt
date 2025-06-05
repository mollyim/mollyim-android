package org.thoughtcrime.securesms.export

import android.content.Context
import androidx.work.ArgumentCaptor as WorkArgumentCaptor // Alias to avoid clash if Mockito ArgumentCaptor is also used directly
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.KeyValueDatabase.Write
import org.thoughtcrime.securesms.export.jobs.ScheduledChatExportWorker
import org.thoughtcrime.securesms.export.model.PersistedScheduleData
import org.thoughtcrime.securesms.export.ui.ExportFrequency
import java.util.concurrent.TimeUnit

@RunWith(MockitoJUnitRunner::class)
class ChatExportSchedulerTest {

    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockWorkManager: WorkManager
    @Mock
    private lateinit var mockKeyValueDatabase: KeyValueDatabase
    @Mock
    private lateinit var mockKvdWrite: Write // For mocking KVD write operations

    private lateinit var gson: Gson // Use a real Gson instance for serialization tests
    private lateinit var scheduler: ChatExportScheduler

    @Captor
    private lateinit var periodicWorkRequestCaptor: WorkArgumentCaptor<PeriodicWorkRequest>
    @Captor
    private lateinit var stringCaptor: ArgumentCaptor<String>

    private val testThreadId = 1L
    private val testChatName = "Test Chat"
    private val testApiUrl = "http://test.api"
    private val testUniqueWorkName = "scheduled_export_thread_$testThreadId"

    @Before
    fun setUp() {
        gson = Gson() // Real Gson for testing serialization logic
        // `when`(mockContext.applicationContext).thenReturn(mockContext) // Not strictly needed if WorkManager is injected

        // Setup KVD Write mock
        // Standard behavior for beginWrite().putXXX().remove().commit() chain:
        // beginWrite() returns the mock Write object.
        // Each put/remove on the mock Write object returns itself to allow chaining.
        // commit() on the mock Write object does nothing by default (or can be verified).
        `when`(mockKeyValueDatabase.beginWrite()).thenReturn(mockKvdWrite)
        `when`(mockKvdWrite.putString(anyString(), anyString())).thenReturn(mockKvdWrite)
        `when`(mockKvdWrite.remove(anyString())).thenReturn(mockKvdWrite)
        // `when`(mockKvdWrite.commit()).then { /* Optionally do something or just allow call */ }


        scheduler = ChatExportScheduler(
            mockContext, // context is still part of constructor, though not directly used in methods under test if WorkManager is injected
            mockWorkManager,
            mockKeyValueDatabase,
            gson
        )
    }

    private fun createDefaultScheduleData(
        workName: String = testUniqueWorkName,
        threadId: Long = testThreadId,
        chatName: String = testChatName,
        format: String = "JSON",
        destination: String = "LOCAL_FILE",
        apiUrl: String? = null,
        frequency: String = ExportFrequency.DAILY.name
    ): PersistedScheduleData {
        return PersistedScheduleData(workName, threadId, chatName, format, destination, apiUrl, frequency)
    }

    @Test
    fun `scheduleExport for LOCAL_FILE DAILY`() {
        // Mock KVD behavior for the ID list update part of saveScheduleConfiguration
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null)).thenReturn(null) // No existing IDs

        val returnedWorkName = scheduler.scheduleExport(
            testThreadId, testChatName, "JSON", "LOCAL_FILE", null, ExportFrequency.DAILY
        )
        assertEquals(testUniqueWorkName, returnedWorkName)

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(testUniqueWorkName),
            eq(ExistingPeriodicWorkPolicy.REPLACE),
            periodicWorkRequestCaptor.capture()
        )

        val request = periodicWorkRequestCaptor.value
        assertEquals(TimeUnit.DAYS.toMillis(1), request.workSpec.intervalDuration)
        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)

        val inputData = request.workSpec.input
        assertEquals(testThreadId, inputData.getLong(ScheduledChatExportWorker.KEY_THREAD_ID, -1L))
        assertEquals("JSON", inputData.getString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT))
        assertEquals("LOCAL_FILE", inputData.getString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION))
        assertEquals(testChatName, inputData.getString(ScheduledChatExportWorker.KEY_CHAT_NAME))
        assertNull(inputData.getString(ScheduledChatExportWorker.KEY_API_URL))

        // Verify persistence
        val expectedScheduleData = createDefaultScheduleData()
        verify(mockKeyValueDatabase, times(1)).beginWrite() // Only one beginWrite per saveScheduleConfiguration call
        verify(mockKvdWrite).putString(eq(ChatExportScheduler.KVD_SCHEDULE_PREFIX + testUniqueWorkName), eq(gson.toJson(expectedScheduleData)))
        verify(mockKvdWrite).putString(eq(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY), eq(gson.toJson(setOf(testUniqueWorkName))))
        verify(mockKvdWrite, times(1)).commit()
    }

    @Test
    fun `scheduleExport for API_ENDPOINT WEEKLY with URL`() {
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null)).thenReturn(null)


        val returnedWorkName = scheduler.scheduleExport(
            testThreadId, testChatName, "CSV", "API_ENDPOINT", testApiUrl, ExportFrequency.WEEKLY
        )
        assertEquals(testUniqueWorkName, returnedWorkName)

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(testUniqueWorkName),
            eq(ExistingPeriodicWorkPolicy.REPLACE),
            periodicWorkRequestCaptor.capture()
        )

        val request = periodicWorkRequestCaptor.value
        assertEquals(TimeUnit.DAYS.toMillis(7), request.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)

        val inputData = request.workSpec.input
        assertEquals("CSV", inputData.getString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT))
        assertEquals("API_ENDPOINT", inputData.getString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION))
        assertEquals(testApiUrl, inputData.getString(ScheduledChatExportWorker.KEY_API_URL))

        // Verify persistence
        val expectedScheduleData = createDefaultScheduleData(
            format = "CSV", destination = "API_ENDPOINT", apiUrl = testApiUrl, frequency = ExportFrequency.WEEKLY.name
        )
        verify(mockKeyValueDatabase, times(1)).beginWrite()
        verify(mockKvdWrite).putString(eq(ChatExportScheduler.KVD_SCHEDULE_PREFIX + testUniqueWorkName), eq(gson.toJson(expectedScheduleData)))
    }

    @Test
    fun `cancelScheduledExport calls WorkManager and removes from KVD`() {
        // Mock initial state of KVD ID list
        val initialSet = setOf(testUniqueWorkName, "otherWork")
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null)).thenReturn(gson.toJson(initialSet))

        scheduler.cancelScheduledExport(testUniqueWorkName)

        verify(mockWorkManager).cancelUniqueWork(testUniqueWorkName)

        verify(mockKeyValueDatabase, times(1)).beginWrite() // Only one beginWrite per removeScheduleConfiguration
        verify(mockKvdWrite).remove(ChatExportScheduler.KVD_SCHEDULE_PREFIX + testUniqueWorkName)
        verify(mockKvdWrite).putString(eq(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY), eq(gson.toJson(setOf("otherWork"))))
        verify(mockKvdWrite, times(1)).commit()
    }

    @Test
    fun `getScheduleConfig returns null for non-existent schedule`() {
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_PREFIX + "nonExistent", null)).thenReturn(null)
        assertNull(scheduler.getScheduleConfig("nonExistent"))
    }

    @Test
    fun `getScheduleConfig returns deserialized data for existing schedule`() {
        val scheduleData = createDefaultScheduleData()
        val json = gson.toJson(scheduleData)
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_PREFIX + testUniqueWorkName, null)).thenReturn(json)

        val result = scheduler.getScheduleConfig(testUniqueWorkName)
        assertEquals(scheduleData, result)
    }

    @Test
    fun `getAllScheduledExportConfigs returns empty list when no schedules`() {
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null)).thenReturn(null)
        assertTrue(scheduler.getAllScheduledExportConfigs().isEmpty())

        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null)).thenReturn(gson.toJson(emptySet<String>()))
        assertTrue(scheduler.getAllScheduledExportConfigs().isEmpty())
    }

    @Test
    fun `getAllScheduledExportConfigs returns list of schedules`() {
        val schedule1 = createDefaultScheduleData("work1", 1L, "Chat1")
        val schedule2 = createDefaultScheduleData("work2", 2L, "Chat2", format="CSV")
        val idsJson = gson.toJson(setOf("work1", "work2"))

        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null)).thenReturn(idsJson)
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_PREFIX + "work1", null)).thenReturn(gson.toJson(schedule1))
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_PREFIX + "work2", null)).thenReturn(gson.toJson(schedule2))

        val result = scheduler.getAllScheduledExportConfigs()
        assertEquals(2, result.size)
        assertTrue(result.contains(schedule1))
        assertTrue(result.contains(schedule2))
    }

    @Test
    fun `cancelAllScheduledExports cancels all from WorkManager and KVD`() {
        val ids = setOf("work1", "work2")
        val idsJson = gson.toJson(ids)
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null)).thenReturn(idsJson)

        scheduler.cancelAllScheduledExports()

        verify(mockWorkManager).cancelUniqueWork("work1")
        verify(mockWorkManager).cancelUniqueWork("work2")

        // Each KVD item removal + list removal is one beginWrite().commit() block in current implementation
        verify(mockKeyValueDatabase, times(ids.size + 1)).beginWrite()
        verify(mockKvdWrite).remove(ChatExportScheduler.KVD_SCHEDULE_PREFIX + "work1")
        verify(mockKvdWrite).remove(ChatExportScheduler.KVD_SCHEDULE_PREFIX + "work2")
        verify(mockKvdWrite).remove(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY)
        verify(mockKvdWrite, times(ids.size + 1)).commit()
    }

    @Test
    fun `saveScheduleConfiguration correctly updates list of IDs in KVD`() {
        val initialIds = setOf("existingWork1")
        val initialIdsJson = gson.toJson(initialIds)
        // Mock the KVD to return existing IDs first, then allow subsequent calls to get updated list (though not strictly needed for this verify)
        `when`(mockKeyValueDatabase.getString(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY, null))
            .thenReturn(initialIdsJson) // First call by saveScheduleConfiguration
            .thenReturn(gson.toJson(setOf("existingWork1", "newWork"))) // Subsequent calls by getScheduleIds if any

        val newScheduleData = createDefaultScheduleData(workName = "newWork")

        scheduler.scheduleExport(newScheduleData.threadId, newScheduleData.chatName, newScheduleData.format,
                                 newScheduleData.destination, newScheduleData.apiUrl, ExportFrequency.valueOf(newScheduleData.frequency))

        val expectedFinalIds = setOf("existingWork1", "newWork")
        // Verify putString for the ID list was called with the correctly updated set
        verify(mockKvdWrite).putString(eq(ChatExportScheduler.KVD_SCHEDULE_LIST_KEY), eq(gson.toJson(expectedFinalIds)))
    }
}
