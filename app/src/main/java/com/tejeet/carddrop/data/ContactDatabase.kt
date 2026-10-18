package com.tejeet.carddrop.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.LeadPriority
import com.tejeet.carddrop.model.OcrStatus
import com.tejeet.carddrop.model.SendStatus
import org.json.JSONArray

/**
 * On-device SQLite database holding every scanned contact plus its send flags
 * (email_sent / whatsapp_sent / sent_at). Plain SQLiteOpenHelper rather than Room
 * keeps the build free of annotation processors.
 */
class ContactDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_NAME TEXT NOT NULL DEFAULT '',
                $COL_COMPANY TEXT NOT NULL DEFAULT '',
                $COL_TITLE TEXT NOT NULL DEFAULT '',
                $COL_EMAIL TEXT NOT NULL DEFAULT '',
                $COL_PHONE TEXT NOT NULL DEFAULT '',
                $COL_WEBSITE TEXT NOT NULL DEFAULT '',
                $COL_LINKEDIN TEXT NOT NULL DEFAULT '',
                $COL_DEPARTMENT TEXT NOT NULL DEFAULT '',
                $COL_NOTES TEXT NOT NULL DEFAULT '',
                $COL_EVENT_NAME TEXT NOT NULL DEFAULT '',
                $COL_STALL_NUMBER TEXT NOT NULL DEFAULT '',
                $COL_HALL_NAME TEXT NOT NULL DEFAULT '',
                $COL_CITY TEXT NOT NULL DEFAULT '',
                $COL_COUNTRY TEXT NOT NULL DEFAULT '',
                $COL_GEO_LAT REAL,
                $COL_GEO_LNG REAL,
                $COL_PRODUCT_INTEREST TEXT NOT NULL DEFAULT '',
                $COL_FOLLOW_UP_DATE TEXT NOT NULL DEFAULT '',
                $COL_ASSIGNED_TO TEXT NOT NULL DEFAULT '',
                $COL_NEXT_ACTION TEXT NOT NULL DEFAULT '',
                $COL_USE_CASE TEXT NOT NULL DEFAULT '',
                $COL_VOICE_PATH TEXT NOT NULL DEFAULT '',
                $COL_VOICE_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_VOICE_SUMMARY TEXT NOT NULL DEFAULT '',
                $COL_CONSENT_EMAIL INTEGER NOT NULL DEFAULT 1,
                $COL_CONSENT_WHATSAPP INTEGER NOT NULL DEFAULT 1,
                $COL_OCR_CONFIDENCE INTEGER NOT NULL DEFAULT 0,
                $COL_MANUALLY_EDITED INTEGER NOT NULL DEFAULT 0,
                $COL_PRIORITY TEXT NOT NULL DEFAULT '${LeadPriority.NONE.name}',
                $COL_RAW_OCR TEXT NOT NULL DEFAULT '',
                $COL_PHOTO_PATH TEXT NOT NULL DEFAULT '',
                $COL_BACK_PHOTO_PATH TEXT NOT NULL DEFAULT '',
                $COL_STALL_PHOTOS TEXT NOT NULL DEFAULT '[]',
                $COL_TAGS TEXT NOT NULL DEFAULT '[]',
                $COL_OCR_STATUS TEXT NOT NULL DEFAULT '${OcrStatus.DONE.name}',
                $COL_REVIEWED INTEGER NOT NULL DEFAULT 0,
                $COL_AI_STATUS TEXT NOT NULL DEFAULT '${AiStatus.NONE.name}',
                $COL_AI_SUMMARY TEXT NOT NULL DEFAULT '',
                $COL_STATUS TEXT NOT NULL DEFAULT '${SendStatus.PENDING.name}',
                $COL_LAST_ERROR TEXT NOT NULL DEFAULT '',
                $COL_EMAIL_SENT INTEGER NOT NULL DEFAULT 0,
                $COL_WHATSAPP_SENT INTEGER NOT NULL DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_SENT_AT INTEGER NOT NULL DEFAULT 0,
                $COL_SYNCED_AT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_STALL_PHOTOS TEXT NOT NULL DEFAULT '[]'")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_AI_STATUS TEXT NOT NULL DEFAULT '${AiStatus.NONE.name}'")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_AI_SUMMARY TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_TAGS TEXT NOT NULL DEFAULT '[]'")
        }
        if (oldVersion < 5) {
            listOf(
                "$COL_STALL_NUMBER TEXT NOT NULL DEFAULT ''",
                "$COL_HALL_NAME TEXT NOT NULL DEFAULT ''",
                "$COL_CITY TEXT NOT NULL DEFAULT ''",
                "$COL_COUNTRY TEXT NOT NULL DEFAULT ''",
                "$COL_GEO_LAT REAL",
                "$COL_GEO_LNG REAL",
                "$COL_PRODUCT_INTEREST TEXT NOT NULL DEFAULT ''",
                "$COL_FOLLOW_UP_DATE TEXT NOT NULL DEFAULT ''",
                "$COL_ASSIGNED_TO TEXT NOT NULL DEFAULT ''",
                "$COL_NEXT_ACTION TEXT NOT NULL DEFAULT ''",
                "$COL_CONSENT_EMAIL INTEGER NOT NULL DEFAULT 1",
                "$COL_CONSENT_WHATSAPP INTEGER NOT NULL DEFAULT 1",
                "$COL_OCR_CONFIDENCE INTEGER NOT NULL DEFAULT 0",
                "$COL_MANUALLY_EDITED INTEGER NOT NULL DEFAULT 0"
            ).forEach { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $it") }
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_LINKEDIN TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_DEPARTMENT TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_USE_CASE TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_SYNCED_AT INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_VOICE_PATH TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_VOICE_DURATION INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_VOICE_SUMMARY TEXT NOT NULL DEFAULT ''")
        }
    }

    fun getAll(): List<Contact> =
        readableDatabase.query(TABLE, null, null, null, null, null, "$COL_CREATED_AT DESC").use { c ->
            buildList { while (c.moveToNext()) add(c.toContact()) }
        }

    fun upsert(contact: Contact) {
        writableDatabase.insertWithOnConflict(TABLE, null, contact.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertAll(contacts: List<Contact>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            contacts.forEach { db.insertWithOnConflict(TABLE, null, it.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: String) {
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id))
    }

    private fun Contact.toValues() = ContentValues().apply {
        put(COL_ID, id)
        put(COL_NAME, name)
        put(COL_COMPANY, company)
        put(COL_TITLE, title)
        put(COL_EMAIL, email)
        put(COL_PHONE, phone)
        put(COL_WEBSITE, website)
        put(COL_LINKEDIN, linkedin)
        put(COL_DEPARTMENT, department)
        put(COL_NOTES, notes)
        put(COL_EVENT_NAME, eventName)
        put(COL_STALL_NUMBER, stallNumber)
        put(COL_HALL_NAME, hallName)
        put(COL_CITY, city)
        put(COL_COUNTRY, country)
        put(COL_GEO_LAT, geoLat)
        put(COL_GEO_LNG, geoLng)
        put(COL_PRODUCT_INTEREST, productInterest)
        put(COL_FOLLOW_UP_DATE, followUpDate)
        put(COL_ASSIGNED_TO, assignedTo)
        put(COL_NEXT_ACTION, nextAction)
        put(COL_USE_CASE, useCase)
        put(COL_VOICE_PATH, voiceNotePath)
        put(COL_VOICE_DURATION, voiceNoteDurationMs)
        put(COL_VOICE_SUMMARY, voiceSummary)
        put(COL_CONSENT_EMAIL, if (consentEmail) 1 else 0)
        put(COL_CONSENT_WHATSAPP, if (consentWhatsapp) 1 else 0)
        put(COL_OCR_CONFIDENCE, ocrConfidence)
        put(COL_MANUALLY_EDITED, if (manuallyEdited) 1 else 0)
        put(COL_PRIORITY, priority.name)
        put(COL_RAW_OCR, rawOcrText)
        put(COL_PHOTO_PATH, photoPath)
        put(COL_BACK_PHOTO_PATH, backPhotoPath)
        put(COL_STALL_PHOTOS, JSONArray(stallPhotoPaths).toString())
        put(COL_TAGS, JSONArray(tags).toString())
        put(COL_OCR_STATUS, ocrStatus.name)
        put(COL_REVIEWED, if (reviewed) 1 else 0)
        put(COL_AI_STATUS, aiStatus.name)
        put(COL_AI_SUMMARY, aiSummary)
        put(COL_STATUS, status.name)
        put(COL_LAST_ERROR, lastError)
        put(COL_EMAIL_SENT, if (emailSent) 1 else 0)
        put(COL_WHATSAPP_SENT, if (whatsappSent) 1 else 0)
        put(COL_CREATED_AT, createdAt)
        put(COL_SENT_AT, sentAt)
        put(COL_SYNCED_AT, syncedAt)
    }

    private fun Cursor.toContact(): Contact {
        fun str(col: String) = getString(getColumnIndexOrThrow(col)).orEmpty()
        fun long(col: String) = getLong(getColumnIndexOrThrow(col))
        fun nullableDouble(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
        return Contact(
            id = str(COL_ID),
            name = str(COL_NAME),
            company = str(COL_COMPANY),
            title = str(COL_TITLE),
            email = str(COL_EMAIL),
            phone = str(COL_PHONE),
            website = str(COL_WEBSITE),
            linkedin = str(COL_LINKEDIN),
            department = str(COL_DEPARTMENT),
            notes = str(COL_NOTES),
            eventName = str(COL_EVENT_NAME),
            stallNumber = str(COL_STALL_NUMBER),
            hallName = str(COL_HALL_NAME),
            city = str(COL_CITY),
            country = str(COL_COUNTRY),
            geoLat = nullableDouble(COL_GEO_LAT),
            geoLng = nullableDouble(COL_GEO_LNG),
            productInterest = str(COL_PRODUCT_INTEREST),
            followUpDate = str(COL_FOLLOW_UP_DATE),
            assignedTo = str(COL_ASSIGNED_TO),
            nextAction = str(COL_NEXT_ACTION),
            useCase = str(COL_USE_CASE),
            voiceNotePath = str(COL_VOICE_PATH),
            voiceNoteDurationMs = long(COL_VOICE_DURATION),
            voiceSummary = str(COL_VOICE_SUMMARY),
            consentEmail = long(COL_CONSENT_EMAIL) != 0L,
            consentWhatsapp = long(COL_CONSENT_WHATSAPP) != 0L,
            ocrConfidence = long(COL_OCR_CONFIDENCE).toInt(),
            manuallyEdited = long(COL_MANUALLY_EDITED) != 0L,
            priority = runCatching { LeadPriority.valueOf(str(COL_PRIORITY)) }.getOrDefault(LeadPriority.NONE),
            rawOcrText = str(COL_RAW_OCR),
            photoPath = str(COL_PHOTO_PATH),
            backPhotoPath = str(COL_BACK_PHOTO_PATH),
            stallPhotoPaths = stringList(str(COL_STALL_PHOTOS)),
            tags = stringList(str(COL_TAGS)),
            ocrStatus = runCatching { OcrStatus.valueOf(str(COL_OCR_STATUS)) }.getOrDefault(OcrStatus.DONE),
            reviewed = long(COL_REVIEWED) != 0L,
            aiStatus = runCatching { AiStatus.valueOf(str(COL_AI_STATUS)) }.getOrDefault(AiStatus.NONE),
            aiSummary = str(COL_AI_SUMMARY),
            status = runCatching { SendStatus.valueOf(str(COL_STATUS)) }.getOrDefault(SendStatus.PENDING),
            lastError = str(COL_LAST_ERROR),
            emailSent = long(COL_EMAIL_SENT) != 0L,
            whatsappSent = long(COL_WHATSAPP_SENT) != 0L,
            createdAt = long(COL_CREATED_AT),
            sentAt = long(COL_SENT_AT),
            syncedAt = long(COL_SYNCED_AT)
        )
    }

    private fun stringList(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val DB_NAME = "carddrop.db"
        const val DB_VERSION = 9
        const val TABLE = "contacts"

        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_COMPANY = "company"
        const val COL_TITLE = "title"
        const val COL_EMAIL = "email"
        const val COL_PHONE = "phone"
        const val COL_WEBSITE = "website"
        const val COL_LINKEDIN = "linkedin"
        const val COL_DEPARTMENT = "department"
        const val COL_NOTES = "notes"
        const val COL_EVENT_NAME = "event_name"
        const val COL_STALL_NUMBER = "stall_number"
        const val COL_HALL_NAME = "hall_name"
        const val COL_CITY = "city"
        const val COL_COUNTRY = "country"
        const val COL_GEO_LAT = "geo_lat"
        const val COL_GEO_LNG = "geo_lng"
        const val COL_PRODUCT_INTEREST = "product_interest"
        const val COL_FOLLOW_UP_DATE = "follow_up_date"
        const val COL_ASSIGNED_TO = "assigned_to"
        const val COL_NEXT_ACTION = "next_action"
        const val COL_USE_CASE = "use_case"
        const val COL_VOICE_PATH = "voice_note_path"
        const val COL_VOICE_DURATION = "voice_note_duration_ms"
        const val COL_VOICE_SUMMARY = "voice_summary"
        const val COL_CONSENT_EMAIL = "consent_email"
        const val COL_CONSENT_WHATSAPP = "consent_whatsapp"
        const val COL_OCR_CONFIDENCE = "ocr_confidence"
        const val COL_MANUALLY_EDITED = "manually_edited"
        const val COL_PRIORITY = "priority"
        const val COL_RAW_OCR = "raw_ocr_text"
        const val COL_PHOTO_PATH = "photo_path"
        const val COL_BACK_PHOTO_PATH = "back_photo_path"
        const val COL_STALL_PHOTOS = "stall_photo_paths"
        const val COL_TAGS = "tags"
        const val COL_OCR_STATUS = "ocr_status"
        const val COL_REVIEWED = "reviewed"
        const val COL_AI_STATUS = "ai_status"
        const val COL_AI_SUMMARY = "ai_summary"
        const val COL_STATUS = "status"
        const val COL_LAST_ERROR = "last_error"
        const val COL_EMAIL_SENT = "email_sent"
        const val COL_WHATSAPP_SENT = "whatsapp_sent"
        const val COL_CREATED_AT = "created_at"
        const val COL_SENT_AT = "sent_at"
        const val COL_SYNCED_AT = "synced_at"
    }
}
