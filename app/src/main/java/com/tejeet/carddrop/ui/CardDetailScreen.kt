package com.tejeet.carddrop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.tejeet.carddrop.messaging.FollowUpMessages
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.LeadPriority
import com.tejeet.carddrop.model.OcrStatus
import com.tejeet.carddrop.model.SendStatus
import com.tejeet.carddrop.ui.components.AiAnalyzingLabel
import com.tejeet.carddrop.ui.components.Avatar
import com.tejeet.carddrop.ui.components.aiShimmer
import com.tejeet.carddrop.ui.components.rememberImage
import com.tejeet.carddrop.ui.components.PhotoViewer
import com.tejeet.carddrop.ui.components.Pill
import com.tejeet.carddrop.ui.components.ViewablePhoto
import com.tejeet.carddrop.ui.components.SectionCard
import com.tejeet.carddrop.ui.components.priorityIcon
import com.tejeet.carddrop.ui.components.priorityTone
import com.tejeet.carddrop.ui.theme.AppTheme
import com.tejeet.carddrop.ui.theme.Tone
import com.tejeet.carddrop.util.ImageUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only view of a saved card: photos, details you can tap to call/message or copy,
 * notes, tags and send status. Editing happens on the review screen, one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(
    contact: Contact,
    /** Expo name from Settings, used in the follow-up messages when the card has none. */
    defaultEventName: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showRaw by remember { mutableStateOf(false) }
    // The on-device AI can still be working on a card that was saved quickly on the expo floor.
    val analyzing = contact.aiStatus == AiStatus.CHECKING || contact.ocrStatus == OcrStatus.PROCESSING

    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    val photos = remember(contact.photoPath, contact.backPhotoPath, contact.stallPhotoPaths) {
        buildList {
            if (contact.photoPath.isNotBlank()) add(ViewablePhoto(contact.photoPath, "Front of card"))
            if (contact.backPhotoPath.isNotBlank()) add(ViewablePhoto(contact.backPhotoPath, "Back of card"))
            contact.stallPhotoPaths.forEachIndexed { i, path ->
                add(ViewablePhoto(path, "Stall photo ${i + 1}"))
            }
        }
    }

    val frontImage = rememberImage(contact.photoPath, maxDimension = 800)
    val backImage = rememberImage(contact.backPhotoPath, maxDimension = 800)

    viewerStartIndex?.let { start ->
        PhotoViewer(photos = photos, startIndex = start, onDismiss = { viewerStartIndex = null })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Card", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit card")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete card")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBar,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onSend,
                        enabled = contact.isReadyToSend,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                analyzing -> "AI analyzing..."
                                contact.missingRequired().isNotEmpty() -> "Missing details"
                                contact.status == SendStatus.FAILED -> "Retry"
                                else -> "Send"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            DetailHeader(contact, analyzing)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (analyzing) {
                    Surface(
                        color = colors.ai.container,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().aiShimmer(true, cornerRadius = 14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            AiAnalyzingLabel("AI analyzing this card...")
                            Text(
                                "Details may still be corrected. Send unlocks as soon as the check is done.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ai.content
                            )
                        }
                    }
                }

                // Reach out
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (contact.hasPhone) {
                        BigAction("Call", Icons.Filled.Call, colors.sent, Modifier.weight(1f)) {
                            ContactActions.call(context, contact)
                        }
                        BigAction("WhatsApp", Icons.Filled.Chat, colors.brand, Modifier.weight(1f)) {
                            ContactActions.whatsApp(context, contact, defaultEventName)
                        }
                    }
                    if (contact.hasEmail) {
                        BigAction("Email", Icons.Filled.Email, colors.pending, Modifier.weight(1f)) {
                            ContactActions.email(context, contact, defaultEventName)
                        }
                    }
                }

                if (contact.photoPath.isNotBlank() || contact.backPhotoPath.isNotBlank()) {
                    SectionCard(title = "Card photos", icon = Icons.Filled.Style, tone = colors.pending) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (contact.photoPath.isNotBlank()) {
                                PhotoWithLabel(frontImage, "Front", Modifier.weight(1f)) {
                                    viewerStartIndex = photos.indexOfFirst { it.path == contact.photoPath }
                                }
                            }
                            if (contact.backPhotoPath.isNotBlank()) {
                                PhotoWithLabel(backImage, "Back", Modifier.weight(1f)) {
                                    viewerStartIndex = photos.indexOfFirst { it.path == contact.backPhotoPath }
                                }
                            }
                        }
                    }
                }

                if (contact.stallPhotoPaths.isNotEmpty()) {
                    SectionCard(
                        title = "Stall / product photos",
                        icon = Icons.Filled.Storefront,
                        tone = colors.hot,
                        subtitle = "${contact.stallPhotoPaths.size} saved"
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            contact.stallPhotoPaths.forEach { path ->
                                val bitmap = rememberImage(path, maxDimension = 400)
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewerStartIndex = photos.indexOfFirst { p -> p.path == path } }
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Stall photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }

                SectionCard(
                    title = "Contact details",
                    icon = Icons.Filled.Person,
                    tone = colors.navy,
                    modifier = Modifier.aiShimmer(analyzing, cornerRadius = 20.dp)
                ) {
                    if (contact.hasEmail) {
                        DetailRow(Icons.Filled.Email, "Email", contact.email, clipboard, context)
                    }
                    if (contact.hasPhone) {
                        DetailRow(Icons.Filled.Phone, "Phone / WhatsApp", contact.phone, clipboard, context)
                    }
                    if (contact.company.isNotBlank()) {
                        DetailRow(Icons.Filled.Business, "Company", contact.company, clipboard, context)
                    }
                    if (contact.website.isNotBlank()) {
                        DetailRow(Icons.Filled.Language, "Website", contact.website, clipboard, context)
                    }
                    if (contact.linkedin.isNotBlank()) {
                        DetailRow(Icons.Filled.Link, "LinkedIn", contact.linkedin, clipboard, context)
                    }
                    if (contact.department.isNotBlank()) {
                        DetailRow(Icons.Filled.Groups, "Department", contact.department, clipboard, context)
                    }
                }

                val locationLine = listOf(contact.stallNumber.takeIf { it.isNotBlank() }?.let { "Stall $it" },
                    contact.hallName.takeIf { it.isNotBlank() }?.let { "Hall $it" })
                    .filterNotNull()
                    .joinToString(" · ")
                if (contact.eventName.isNotBlank() || locationLine.isNotBlank() ||
                    contact.city.isNotBlank() || contact.country.isNotBlank() || contact.geoLat != null
                ) {
                    SectionCard(title = "Expo & location", icon = Icons.Filled.Place, tone = colors.warm) {
                        if (contact.eventName.isNotBlank()) {
                            DetailRow(Icons.Filled.Event, "Expo", contact.eventName, clipboard, context)
                        }
                        if (locationLine.isNotBlank()) {
                            DetailRow(Icons.Filled.Storefront, "Stand", locationLine, clipboard, context)
                        }
                        val place = listOf(contact.city, contact.country).filter { it.isNotBlank() }.joinToString(", ")
                        if (place.isNotBlank()) {
                            DetailRow(Icons.Filled.Place, "Place", place, clipboard, context)
                        }
                        if (contact.geoLat != null && contact.geoLng != null) {
                            DetailRow(
                                Icons.Filled.Place,
                                "GPS",
                                "%.5f, %.5f".format(contact.geoLat, contact.geoLng),
                                clipboard,
                                context
                            )
                        }
                    }
                }

                if (contact.notes.isNotBlank()) {
                    SectionCard(title = "Expo notes", icon = Icons.Filled.EditNote, tone = colors.brand) {
                        Text(contact.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (contact.assignedTo.isNotBlank() || contact.nextAction.isNotBlank() || contact.useCase.isNotBlank() ||
                    contact.followUpDate.isNotBlank() || contact.productInterest.isNotBlank()
                ) {
                    SectionCard(title = "Follow-up", icon = Icons.Filled.TaskAlt, tone = colors.hot) {
                        if (contact.assignedTo.isNotBlank()) {
                            DetailRow(Icons.Filled.Person, "Assigned to", contact.assignedTo, clipboard, context)
                        }
                        if (contact.followUpDate.isNotBlank()) {
                            DetailRow(Icons.Filled.CalendarMonth, "Follow up on", contact.followUpDate, clipboard, context)
                        }
                        if (contact.useCase.isNotBlank()) {
                            DetailRow(Icons.Filled.Lightbulb, "Use case", contact.useCase, clipboard, context)
                        }
                        if (contact.nextAction.isNotBlank()) {
                            DetailRow(Icons.Filled.TaskAlt, "Next action", contact.nextAction, clipboard, context)
                        }
                        if (contact.productInterest.isNotBlank()) {
                            DetailRow(Icons.Filled.Inventory2, "Product interest", contact.productInterest, clipboard, context)
                        }
                    }
                }

                if (contact.priority != LeadPriority.NONE || contact.tags.isNotEmpty()) {
                    SectionCard(title = "Lead & tags", icon = Icons.Filled.Flag, tone = colors.warm) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            priorityIcon(contact.priority)?.let { icon ->
                                Pill(
                                    if (contact.priority == LeadPriority.HOT) "Hot lead" else "${contact.priority.label} lead",
                                    priorityTone(contact.priority, colors),
                                    icon
                                )
                            }
                            contact.tags.forEach { tag -> Pill(tag, colors.pending, Icons.Filled.LocalOffer) }
                        }
                    }
                }

                val messages = remember(contact, defaultEventName) { FollowUpMessages.build(contact, defaultEventName) }
                SectionCard(
                    title = "Follow-up messages",
                    icon = Icons.Filled.Chat,
                    tone = colors.brand,
                    subtitle = "Generated from this card and sent with it"
                ) {
                    if (contact.hasPhone) {
                        Text("WhatsApp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            color = colors.sent.container,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(messages.whatsapp, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (contact.hasEmail) {
                        Text("Email", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Subject: ${messages.emailSubject}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.navy.content
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(messages.emailText, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Sent as a branded HTML email.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                SectionCard(title = "Sending", icon = Icons.Filled.Send, tone = sendTone(contact, colors)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (contact.hasEmail) {
                            Pill(
                                if (contact.emailSent) "Email sent" else "Email pending",
                                if (contact.emailSent) colors.sent else colors.neutral,
                                Icons.Filled.Email
                            )
                        }
                        if (contact.hasPhone) {
                            Pill(
                                if (contact.whatsappSent) "WhatsApp sent" else "WhatsApp pending",
                                if (contact.whatsappSent) colors.sent else colors.neutral,
                                Icons.Filled.Chat
                            )
                        }
                    }
                    Text(
                        when (contact.status) {
                            SendStatus.SENT -> "Everything sent${if (contact.sentAt > 0) " on ${formatDateTime(contact.sentAt)}" else ""}."
                            SendStatus.SENDING -> "Sending now..."
                            SendStatus.FAILED -> "Last attempt failed: ${contact.lastError.ifBlank { "unknown error" }}"
                            SendStatus.PENDING -> if (contact.missingRequired().isNotEmpty()) {
                                "Add ${contact.missingRequired().joinToString(", ")} (tap Edit) before this card can be sent."
                            } else {
                                "Not sent yet - use Send below, or Send all from the Cards tab."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (contact.status == SendStatus.FAILED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (contact.syncedAt > 0) {
                            "Last uploaded to the server ${formatDateTime(contact.syncedAt)}"
                        } else {
                            "Not uploaded to the server yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Scanned ${formatDateTime(contact.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Consent: email ${if (contact.consentEmail) "yes" else "no"} · " +
                            "WhatsApp ${if (contact.consentWhatsapp) "yes" else "no"} · " +
                            "scan confidence ${contact.ocrConfidence}%" +
                            if (contact.manuallyEdited) " · edited by hand" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (contact.aiSummary.isNotBlank()) {
                    SectionCard(
                        title = "On-device AI check",
                        icon = Icons.Filled.AutoAwesome,
                        tone = colors.ai,
                        subtitle = if (contact.aiStatus == AiStatus.FAILED) "Check failed" else null
                    ) {
                        Text(contact.aiSummary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (contact.rawOcrText.isNotBlank()) {
                    SectionCard(title = "Scanned text", icon = Icons.Filled.Description, tone = colors.neutral) {
                        TextButton(onClick = { showRaw = !showRaw }) {
                            Text(if (showRaw) "Hide raw scanned text" else "Show raw scanned text")
                        }
                        if (showRaw) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    contact.rawOcrText,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(contact: Contact, analyzing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(AppTheme.colors.headerGradient))
            .aiShimmer(analyzing)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = contact.name.ifBlank { contact.company }, size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    contact.name.ifBlank { "(no name)" },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.title.isNotBlank()) {
                    Text(
                        contact.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                if (contact.company.isNotBlank()) {
                    Text(
                        contact.company,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BigAction(label: String, icon: ImageVector, tone: Tone, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = tone.container, contentColor = tone.content)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    clipboard: ClipboardManager,
    context: android.content.Context
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = {
            clipboard.setText(AnnotatedString(value))
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhotoWithLabel(bitmap: ImageBitmap?, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$label of card",
                modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().height(130.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {}
        }
        Text(
            "$label · tap to zoom",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun sendTone(contact: Contact, colors: com.tejeet.carddrop.ui.theme.ExtendedColors): Tone = when (contact.status) {
    SendStatus.SENT -> colors.sent
    SendStatus.FAILED -> colors.failed
    else -> colors.pending
}


private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
