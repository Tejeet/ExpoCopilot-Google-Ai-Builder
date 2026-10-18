package com.tejeet.carddrop.data

import android.content.Context
import com.tejeet.carddrop.ai.AiModel
import com.tejeet.carddrop.network.ApiConfig
import org.json.JSONArray

/**
 * Small SharedPreferences wrapper for the handful of settings the app needs:
 * where the backend lives, and what to put in the emails it sends.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("carddrop_settings", Context.MODE_PRIVATE)

    /** Hardcoded in ApiConfig - change it there and rebuild. */
    val backendUrl: String get() = ApiConfig.SEND_CARD_URL
    val sharedSecret: String get() = ApiConfig.SHARED_SECRET

    var senderName: String
        get() = prefs.getString(KEY_SENDER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SENDER, value).apply()

    var eventName: String
        get() = prefs.getString(KEY_EVENT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_EVENT, value).apply()

    /** Run the on-device AI check on every newly scanned card (only once the model is downloaded). */
    var aiAutoCheck: Boolean
        get() = prefs.getBoolean(KEY_AI_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_AUTO_CHECK, value).apply()

    /** Which on-device model the AI check uses (see AiModel). */
    var aiModelId: String
        get() = prefs.getString(KEY_AI_MODEL, AiModel.DEFAULT.id).orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    var aiWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_AI_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_WIFI_ONLY, value).apply()

    /** Rep who owns the follow-up; pre-filled on new cards (sent as assigned_to). */
    var assignedTo: String
        get() = prefs.getString(KEY_ASSIGNED_TO, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ASSIGNED_TO, value).apply()

    /** Attach the phone's GPS position (and the city/country it maps to) to each scanned card. */
    var attachLocation: Boolean
        get() = prefs.getBoolean(KEY_ATTACH_LOCATION, true)
        set(value) = prefs.edit().putBoolean(KEY_ATTACH_LOCATION, value).apply()

    /** Halls in most-recently-used order; the first is pre-filled on every new card. */
    var recentHalls: List<String>
        get() = runCatching {
            val arr = JSONArray(prefs.getString(KEY_RECENT_HALLS, "[]"))
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
        set(value) = prefs.edit().putString(KEY_RECENT_HALLS, JSONArray(value).toString()).apply()

    /** Moving from hall to hall: the latest one becomes the default for the next scans. */
    fun rememberHallUsed(hall: String) {
        val trimmed = hall.trim()
        if (trimmed.isBlank()) return
        recentHalls = (listOf(trimmed) + recentHalls).distinctBy { it.lowercase() }.take(MAX_RECENT_HALLS)
    }

    /** Tags in most-recently-used order, suggested at the top of the tag picker. */
    var recentTags: List<String>
        get() = runCatching {
            val arr = JSONArray(prefs.getString(KEY_RECENT_TAGS, "[]"))
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
        set(value) = prefs.edit().putString(KEY_RECENT_TAGS, JSONArray(value).toString()).apply()

    fun rememberTagsUsed(tags: List<String>) {
        if (tags.isEmpty()) return
        recentTags = (tags + recentTags).distinctBy { it.lowercase() }.take(MAX_RECENT_TAGS)
    }

    private companion object {
        const val KEY_ASSIGNED_TO = "assigned_to"
        const val KEY_ATTACH_LOCATION = "attach_location"
        const val KEY_RECENT_TAGS = "recent_tags"
        const val KEY_RECENT_HALLS = "recent_halls"
        const val MAX_RECENT_HALLS = 8
        const val MAX_RECENT_TAGS = 15
        const val KEY_AI_MODEL = "ai_model_id"
        const val KEY_AI_AUTO_CHECK = "ai_auto_check"
        const val KEY_AI_WIFI_ONLY = "ai_wifi_only"
        const val KEY_URL = "backend_url"
        const val KEY_SECRET = "shared_secret"
        const val KEY_SENDER = "sender_name"
        const val KEY_EVENT = "event_name"
    }
}
