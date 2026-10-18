package com.tejeet.carddrop.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tejeet.carddrop.messaging.FollowUpMessages
import com.tejeet.carddrop.model.Contact

/**
 * Reaching a contact straight from the app: the dialler, WhatsApp and the mail app, pre-filled
 * with the same follow-up text the backend sends (see FollowUpMessages).
 *
 * ACTION_DIAL (not ACTION_CALL) opens the dialler with the number typed in, so no CALL_PHONE
 * permission is needed and the user still decides to place the call.
 */
object ContactActions {

    fun call(context: Context, contact: Contact) {
        val number = contact.phone.trim()
        if (number.isBlank()) return
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")), "No dialler app found")
    }

    fun whatsApp(context: Context, contact: Contact, defaultEventName: String) {
        val digits = contact.phone.filter(Char::isDigit)
        if (digits.isBlank()) return
        val text = Uri.encode(FollowUpMessages.build(contact, defaultEventName).whatsapp)
        start(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=$text")),
            "Couldn't open WhatsApp"
        )
    }

    fun email(context: Context, contact: Contact, defaultEventName: String) {
        val address = contact.email.trim()
        if (address.isBlank()) return
        val messages = FollowUpMessages.build(contact, defaultEventName)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(address)}")).apply {
            putExtra(Intent.EXTRA_SUBJECT, messages.emailSubject)
            putExtra(Intent.EXTRA_TEXT, messages.emailText)
        }
        start(context, intent, "No email app found")
    }

    private fun start(context: Context, intent: Intent, failureMessage: String) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
