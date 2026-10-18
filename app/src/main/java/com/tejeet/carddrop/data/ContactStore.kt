package com.tejeet.carddrop.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.SendStatus
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors

/**
 * Local queue of scanned contacts, persisted in SQLite (see ContactDatabase).
 * `contacts` is a Compose SnapshotStateList mirror of the table, so any screen
 * reading it recomposes automatically on add/update/remove.
 *
 * One instance per process (see get()) so background OCR jobs and the UI always
 * update the same list, even if the Activity is recreated while the camera is open.
 */
class ContactStore private constructor(context: Context) {
    private val db = ContactDatabase(context)
    /** Disk writes run here, one at a time and in order, so saving never blocks the UI thread. */
    private val dbWriter = Executors.newSingleThreadExecutor()
    private val legacyFile = File(context.filesDir, "contacts.json")
    val contacts = mutableStateListOf<Contact>()

    init {
        migrateLegacyJson()
        load()
    }

    private fun load() {
        contacts.clear()
        val stored = runCatching { db.getAll() }.getOrDefault(emptyList())
        for (contact in stored) {
            var fixed = contact
            // A send cut off by the app closing would otherwise show "Sending..." forever.
            if (fixed.status == SendStatus.SENDING) {
                fixed = fixed.copy(status = SendStatus.FAILED, lastError = "Interrupted - tap send to retry")
            }
            // Likewise an AI check that never finished; it gets queued again on launch.
            if (fixed.aiStatus == AiStatus.CHECKING) {
                fixed = fixed.copy(aiStatus = AiStatus.NONE)
            }
            if (fixed != contact) write { upsert(fixed) }
            contacts.add(fixed)
        }
    }

    /** One-time move of the old JSON-file queue into SQLite; the file is renamed, not deleted. */
    private fun migrateLegacyJson() {
        if (!legacyFile.exists()) return
        runCatching {
            val arr = JSONArray(legacyFile.readText())
            val now = System.currentTimeMillis()
            // The JSON queue was stored newest-first; offsetting createdAt keeps that order.
            val legacy = (0 until arr.length()).map { i ->
                Contact.fromLegacyJson(arr.getJSONObject(i)).copy(createdAt = now - i)
            }
            db.insertAll(legacy)
            legacyFile.renameTo(File(legacyFile.parentFile, "contacts.json.migrated"))
        }
    }

    private fun write(block: ContactDatabase.() -> Unit) {
        dbWriter.execute { runCatching { db.block() } }
    }

    fun find(id: String): Contact? = contacts.firstOrNull { it.id == id }

    fun add(contact: Contact) {
        write { upsert(contact) }
        contacts.add(0, contact)
    }

    fun update(contact: Contact) {
        val idx = contacts.indexOfFirst { it.id == contact.id }
        if (idx >= 0) {
            write { upsert(contact) }
            contacts[idx] = contact
        }
    }

    fun remove(contact: Contact) {
        write { delete(contact.id) }
        contacts.removeAll { it.id == contact.id }
    }

    companion object {
        @Volatile
        private var instance: ContactStore? = null

        fun get(context: Context): ContactStore =
            instance ?: synchronized(this) {
                instance ?: ContactStore(context.applicationContext).also { instance = it }
            }
    }
}
