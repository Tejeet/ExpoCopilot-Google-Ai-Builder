package com.tejeet.carddrop.util

import android.content.Context
import android.provider.Settings
import com.tejeet.carddrop.BuildConfig
import java.util.Locale

/** Small facts about this install, sent with every card (device_id, app_version, language). */
object DeviceInfo {

    /** Per-app, per-device id. Resets if the app is reinstalled, which is fine for tracing scans. */
    fun deviceId(context: Context): String =
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")

    fun appVersion(): String = BuildConfig.VERSION_NAME

    /** Language code, e.g. "en", "hi", "mr". */
    fun language(): String = Locale.getDefault().language
}
