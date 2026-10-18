package com.tejeet.carddrop.messaging

import com.tejeet.carddrop.model.Contact

/**
 * The follow-up messages for a card, generated on the phone and sent to the backend as
 * `whatsappMessage`, `emailSubject` and `emailMessage` (HTML). The same text backs the
 * WhatsApp / Email buttons on the card detail screen, so what you preview is what goes out.
 *
 * Branding lives in the constants below - change it here.
 */
object FollowUpMessages {
    const val TEAM_NAME = "Team apiHW"
    const val WEBSITE_URL = "https://www.apihardware.com"
    const val WEBSITE_LABEL = "www.apihardware.com"
    const val CONTACT_EMAIL = "info@apihardware.com"

    private const val BRAND_TEAL = "#03D3C0"
    private const val BRAND_NAVY = "#404B69"

    data class Messages(
        val whatsapp: String,
        val emailSubject: String,
        /** Branded, email-client-safe HTML (tables + inline styles). */
        val emailHtml: String,
        /** Plain-text version of the email, for previews and the phone's own mail app. */
        val emailText: String
    )

    fun build(contact: Contact, defaultEventName: String): Messages {
        val firstName = contact.name.trim().substringBefore(' ').ifBlank { "there" }
        val event = contact.eventName.ifBlank { defaultEventName }.trim()
        val useCase = contact.useCase.trim()

        val metAt = if (event.isNotBlank()) "Great meeting you at $event!" else "Great meeting you at the expo!"
        val connecting = if (useCase.isNotBlank()) {
            "It was nice connecting with you and hearing about $useCase. " +
                "We'd love to stay in touch and explore how we can help you with it."
        } else {
            "It was nice connecting with you. " +
                "We'd love to stay in touch and explore opportunities to work together."
        }

        val whatsapp = """
            |Hi $firstName,
            |
            |$metAt
            |
            |$connecting
            |
            |Regards,
            |$TEAM_NAME
            |🌐 $WEBSITE_LABEL
            |✉️ $CONTACT_EMAIL
        """.trimMargin()

        val subject = if (event.isNotBlank()) "Great meeting you at $event" else "Great meeting you"

        val emailText = """
            |Hi $firstName,
            |
            |$metAt
            |
            |$connecting
            |
            |Regards,
            |$TEAM_NAME
            |Website: $WEBSITE_LABEL
            |Email: $CONTACT_EMAIL
        """.trimMargin()

        return Messages(
            whatsapp = whatsapp,
            emailSubject = subject,
            emailHtml = emailHtml(firstName.html(), metAt.html(), connecting.html()),
            emailText = emailText
        )
    }

    private fun emailHtml(firstName: String, metAt: String, connecting: String): String = """
        |<!DOCTYPE html>
        |<html>
        |<body style="margin:0;padding:0;background:#F5F7FB;">
        |<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F5F7FB;padding:24px 12px;">
        |  <tr><td align="center">
        |    <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#FFFFFF;border-radius:12px;overflow:hidden;font-family:Arial,Helvetica,sans-serif;">
        |      <tr>
        |        <td style="background:$BRAND_NAVY;padding:20px 28px;">
        |          <span style="color:#FFFFFF;font-size:22px;font-weight:bold;">api</span><span style="color:$BRAND_TEAL;font-size:22px;font-weight:bold;">HW</span>
        |        </td>
        |      </tr>
        |      <tr><td style="height:4px;background:$BRAND_TEAL;line-height:4px;font-size:0;">&nbsp;</td></tr>
        |      <tr>
        |        <td style="padding:28px;color:#1F2937;font-size:15px;line-height:1.6;">
        |          <p style="margin:0 0 16px;">Hi $firstName,</p>
        |          <p style="margin:0 0 16px;font-size:18px;font-weight:bold;color:$BRAND_NAVY;">$metAt</p>
        |          <p style="margin:0 0 24px;">$connecting</p>
        |          <p style="margin:0;">Regards,<br><strong style="color:$BRAND_NAVY;">$TEAM_NAME</strong></p>
        |        </td>
        |      </tr>
        |      <tr>
        |        <td style="padding:16px 28px;background:#F9FAFB;border-top:1px solid #E5E7EB;font-size:13px;color:#4B5563;">
        |          &#127760; <a href="$WEBSITE_URL" style="color:$BRAND_NAVY;text-decoration:none;font-weight:bold;">$WEBSITE_LABEL</a>
        |          &nbsp;&nbsp;|&nbsp;&nbsp;
        |          &#9993;&#65039; <a href="mailto:$CONTACT_EMAIL" style="color:$BRAND_NAVY;text-decoration:none;font-weight:bold;">$CONTACT_EMAIL</a>
        |        </td>
        |      </tr>
        |    </table>
        |  </td></tr>
        |</table>
        |</body>
        |</html>
    """.trimMargin()

    /** Card details are typed or scanned text, so escape them before they go into HTML. */
    private fun String.html(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
