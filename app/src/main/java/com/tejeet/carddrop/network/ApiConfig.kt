package com.tejeet.carddrop.network

/**
 * Backend endpoint, hardcoded in one place - change HOST / SEND_CARD_PATH / SHARED_SECRET here
 * and rebuild to point the app at another server.
 */
object ApiConfig {
    /** Scheme + host, no trailing slash. */
    const val HOST = "https://apihardware.com"

    /** Path of the endpoint that receives one scanned card (trailing slash is part of the route). */
    const val SEND_CARD_PATH = "/carddrop/send/"

    const val SEND_CARD_URL = "$HOST$SEND_CARD_PATH"

    /** Sent as the `secret` form field; must match the backend. */
    const val SHARED_SECRET = "be05292b82edff4ce60fc0121608bfd2e96c1779798b9720c89ecb66962e9993"
}
