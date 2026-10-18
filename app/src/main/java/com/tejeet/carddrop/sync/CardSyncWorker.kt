package com.tejeet.carddrop.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tejeet.carddrop.data.ContactStore
import com.tejeet.carddrop.data.SettingsStore
import com.tejeet.carddrop.network.BackendClient
import com.tejeet.carddrop.util.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Uploads cards to the backend in the background (details + photos, `syncOnly=1`, so nobody is
 * emailed or messaged). Runs as WorkManager work, so it waits for a connection, retries with
 * backoff on expo wifi, and carries on if the app is closed.
 */
class CardSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = ContactStore.get(applicationContext)
        val settings = SettingsStore(applicationContext)

        val requestedIds = inputData.getStringArray(KEY_CARD_IDS)
        val cards = withContext(Dispatchers.Main) {
            if (requestedIds == null) store.contacts.toList() else requestedIds.mapNotNull { store.find(it) }
        }
        if (cards.isEmpty()) return Result.success()

        val client = BackendClient(
            backendUrl = settings.backendUrl,
            sharedSecret = settings.sharedSecret,
            senderName = settings.senderName,
            defaultEventName = settings.eventName,
            deviceId = DeviceInfo.deviceId(applicationContext),
            appVersion = DeviceInfo.appVersion(),
            language = DeviceInfo.language()
        )

        var failed = 0
        for (card in cards) {
            val error = withContext(Dispatchers.IO) { client.syncContact(card) }
            if (error.isBlank()) {
                withContext(Dispatchers.Main) {
                    store.find(card.id)?.let { store.update(it.copy(syncedAt = System.currentTimeMillis())) }
                }
            } else {
                failed++
            }
        }

        return when {
            failed == 0 -> Result.success()
            // Keep retrying a while (backoff below), then give up; the user can sync again by hand.
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val KEY_CARD_IDS = "card_ids"
        private const val MAX_ATTEMPTS = 4
        private const val WORK_ALL = "carddrop-sync-all"
        private const val WORK_CARD_PREFIX = "carddrop-sync-"

        private val networkRequired = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun request(ids: Array<String>?) =
            OneTimeWorkRequestBuilder<CardSyncWorker>()
                .setConstraints(networkRequired)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .apply { if (ids != null) setInputData(Data.Builder().putStringArray(KEY_CARD_IDS, ids).build()) }
                .build()

        /** Queues one card - used automatically whenever a card is saved. */
        fun syncCard(context: Context, contactId: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_CARD_PREFIX + contactId,
                ExistingWorkPolicy.REPLACE,
                request(arrayOf(contactId))
            )
        }

        /** Queues every card, e.g. to rebuild a backend that lost rows. */
        fun syncAll(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_ALL,
                ExistingWorkPolicy.REPLACE,
                request(null)
            )
        }
    }
}
