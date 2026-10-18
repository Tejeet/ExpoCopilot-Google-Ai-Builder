package com.tejeet.carddrop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.LeadPriority
import com.tejeet.carddrop.model.OcrStatus
import com.tejeet.carddrop.model.SendStatus
import com.tejeet.carddrop.ui.theme.AppTheme
import com.tejeet.carddrop.ui.theme.ExtendedColors
import com.tejeet.carddrop.ui.theme.Tone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One scanned card in a list: coloured status strip, initials, contact info, labels and current state. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactCard(
    contact: Contact,
    onClick: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val tone = stateTone(contact, colors)
    val displayName = when {
        contact.name.isNotBlank() -> contact.name
        contact.ocrStatus == OcrStatus.PROCESSING -> "Reading card..."
        else -> "(no name)"
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).aiShimmer(contact.aiStatus == AiStatus.CHECKING || contact.ocrStatus == OcrStatus.PROCESSING)) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(tone.accent))
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = contact.name.ifBlank { contact.company })
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subtitle = listOf(contact.title, contact.company).filter { it.isNotBlank() }.joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (contact.isReadyToSend) {
                        FilledTonalIconButton(
                            onClick = onSend,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.sent.container,
                                contentColor = colors.sent.content
                            )
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (contact.hasEmail || contact.hasPhone) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (contact.hasEmail) InfoLine(Icons.Filled.Email, contact.email)
                        if (contact.hasPhone) InfoLine(Icons.Filled.Phone, contact.phone)
                    }
                }

                if (contact.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Icon(
                                Icons.Filled.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                contact.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    priorityIcon(contact.priority)?.let { icon ->
                        Pill(if (contact.priority == LeadPriority.HOT) "Hot lead" else contact.priority.label, priorityTone(contact.priority, colors), icon)
                    }
                    contact.tags.take(MAX_TAG_PILLS).forEach { tag -> Pill(tag, colors.pending, Icons.Filled.LocalOffer) }
                    if (contact.tags.size > MAX_TAG_PILLS) Pill("+${contact.tags.size - MAX_TAG_PILLS}", colors.pending)
                    if (contact.eventName.isNotBlank()) Pill(contact.eventName, colors.neutral, Icons.Filled.Event)
                    Pill(if (contact.backPhotoPath.isNotBlank()) "Front + back" else "Front", colors.neutral, Icons.Filled.Style)
                    if (contact.stallPhotoPaths.isNotEmpty()) {
                        Pill("${contact.stallPhotoPaths.size} photo(s)", colors.neutral, Icons.Filled.PhotoLibrary)
                    }
                    when (contact.aiStatus) {
                        AiStatus.DONE -> Pill("AI checked", colors.ai, Icons.Filled.AutoAwesome)
                        AiStatus.FAILED -> Pill("AI check failed", colors.neutral, Icons.Filled.AutoAwesome)
                        else -> {}
                    }
                    if (contact.hasEmail) {
                        Pill(if (contact.emailSent) "Email sent" else "Email", if (contact.emailSent) colors.sent else colors.neutral, Icons.Filled.Email)
                    }
                    if (contact.hasPhone) {
                        Pill(if (contact.whatsappSent) "WhatsApp sent" else "WhatsApp", if (contact.whatsappSent) colors.sent else colors.neutral, Icons.Filled.Forum)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                CardStateLabel(contact, tone)
            }
        }
    }
}

private const val MAX_TAG_PILLS = 3

/** Big "Send all" button, shown wherever there are reviewed cards waiting to go out. */
@Composable
fun SendAllButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        )
    ) {
        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text("Send all ($count)", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(6.dp))
        Text("· Email + WhatsApp", style = MaterialTheme.typography.labelMedium)
    }
}

fun priorityTone(priority: LeadPriority, colors: ExtendedColors): Tone = when (priority) {
    LeadPriority.HOT -> colors.hot
    LeadPriority.WARM -> colors.warm
    LeadPriority.COLD -> colors.cold
    LeadPriority.NONE -> colors.neutral
}

fun priorityIcon(priority: LeadPriority): ImageVector? = when (priority) {
    LeadPriority.HOT -> Icons.Filled.LocalFireDepartment
    LeadPriority.WARM -> Icons.Filled.WbSunny
    LeadPriority.COLD -> Icons.Filled.AcUnit
    LeadPriority.NONE -> null
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun stateTone(c: Contact, colors: ExtendedColors): Tone = when {
    c.ocrStatus == OcrStatus.PROCESSING || c.aiStatus == AiStatus.CHECKING -> colors.ai
    !c.reviewed && c.ocrStatus == OcrStatus.FAILED -> colors.failed
    !c.reviewed -> colors.review
    c.status != SendStatus.SENT && c.missingRequired().isNotEmpty() -> colors.review
    c.status == SendStatus.SENT -> colors.sent
    c.status == SendStatus.FAILED -> colors.failed
    else -> colors.pending
}

@Composable
private fun CardStateLabel(contact: Contact, tone: Tone) {
    val (label, icon) = when {
        contact.ocrStatus == OcrStatus.PROCESSING -> "Reading text in the background..." to null
        contact.aiStatus == AiStatus.CHECKING -> "AI analyzing this card..." to null
        !contact.reviewed && contact.ocrStatus == OcrStatus.FAILED ->
            "Couldn't read the text - tap to fill in details" to Icons.Filled.ErrorOutline
        !contact.reviewed -> "Needs review - tap to check details" to Icons.Filled.RateReview
        contact.status != SendStatus.SENT && contact.missingRequired().isNotEmpty() ->
            "Missing ${contact.missingRequired().joinToString(", ")} - tap to add before sending" to Icons.Filled.RateReview
        contact.status == SendStatus.SENDING -> "Sending..." to null
        contact.status == SendStatus.SENT ->
            "Sent${if (contact.sentAt > 0) " · ${formatTime(contact.sentAt)}" else ""}" to Icons.Filled.DoneAll
        contact.status == SendStatus.FAILED ->
            "Failed${if (contact.lastError.isNotBlank()) ": ${contact.lastError}" else ""}" to Icons.Filled.ErrorOutline
        else -> "Ready to send" to Icons.Filled.Schedule
    }
    Surface(
        color = tone.container,
        contentColor = tone.content,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon == null) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = tone.content)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
