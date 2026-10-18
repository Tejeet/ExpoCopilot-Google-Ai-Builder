package com.tejeet.carddrop.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tejeet.carddrop.ai.CardAiVerifier
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.LeadPriority
import com.tejeet.carddrop.model.OcrStatus
import com.tejeet.carddrop.ui.components.AiAnalyzingLabel
import com.tejeet.carddrop.ui.components.FieldShape
import com.tejeet.carddrop.ui.components.aiShimmer
import com.tejeet.carddrop.ui.components.InlineNotice
import com.tejeet.carddrop.ui.components.rememberImage
import com.tejeet.carddrop.ui.components.SectionCard
import com.tejeet.carddrop.ui.theme.AppTheme
import com.tejeet.carddrop.ui.theme.ExtendedColors
import com.tejeet.carddrop.ui.theme.Tone
import com.tejeet.carddrop.util.ImageUtils
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.DisposableEffect
import com.tejeet.carddrop.audio.VoicePlayer
import com.tejeet.carddrop.audio.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

private val QUICK_NOTES = listOf(
    "Wants a demo", "Send pricing", "Send brochure", "Follow up next week", "Potential partner", "Decision maker"
)

/**
 * Editable form pre-filled from OCR. This screen is the safety net for the whole app:
 * ML Kit + our heuristics (and optionally the on-device AI) guess at fields, but the user
 * always confirms (or fixes) them here before anything can be sent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewCardScreen(
    contact: Contact,
    /** Expo name from Settings, used when this card doesn't have one of its own. */
    defaultEventName: String,
    /** Rep from Settings, pre-filled when the card has none. */
    defaultAssignedTo: String,
    /** Recently used halls, most recent first, offered as one-tap choices. */
    suggestedHalls: List<String>,
    /** Recently used tags, most recent first, offered as one-tap suggestions. */
    suggestedTags: List<String>,
    /** Null when the on-device model isn't downloaded. */
    aiVerifier: CardAiVerifier?,
    onSave: (Contact) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AppTheme.colors
    val context = LocalContext.current

    var name by remember { mutableStateOf(contact.name) }
    var company by remember { mutableStateOf(contact.company) }
    var title by remember { mutableStateOf(contact.title) }
    var email by remember { mutableStateOf(contact.email) }
    var phone by remember { mutableStateOf(contact.phone) }
    var website by remember { mutableStateOf(contact.website) }
    var linkedin by remember { mutableStateOf(contact.linkedin) }
    var department by remember { mutableStateOf(contact.department) }
    var eventName by remember { mutableStateOf(contact.eventName.ifBlank { defaultEventName }) }
    var editingEvent by remember { mutableStateOf(eventName.isBlank()) }
    var stallNumber by remember { mutableStateOf(contact.stallNumber) }
    var hallName by remember { mutableStateOf(contact.hallName) }
    var city by remember { mutableStateOf(contact.city) }
    var country by remember { mutableStateOf(contact.country) }
    var priority by remember { mutableStateOf(contact.priority) }
    var assignedTo by remember { mutableStateOf(contact.assignedTo.ifBlank { defaultAssignedTo }) }
    var nextAction by remember { mutableStateOf(contact.nextAction) }
    var useCase by remember { mutableStateOf(contact.useCase) }
    var followUpDate by remember { mutableStateOf(contact.followUpDate) }
    var productInterest by remember { mutableStateOf(contact.productInterest) }
    var notes by remember { mutableStateOf(contact.notes) }
    var stallPhotos by remember { mutableStateOf(contact.stallPhotoPaths) }
    var voicePath by remember { mutableStateOf(contact.voiceNotePath) }
    var voiceDuration by remember { mutableStateOf(contact.voiceNoteDurationMs) }
    var voiceSummary by remember { mutableStateOf(contact.voiceSummary) }
    var recordingHere by remember { mutableStateOf(false) }
    var recordElapsed by remember { mutableStateOf("0:00") }
    var summarising by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(contact.tags) }
    var tagInput by remember { mutableStateOf("") }
    var consentEmail by remember { mutableStateOf(contact.consentEmail) }
    var consentWhatsapp by remember { mutableStateOf(contact.consentWhatsapp) }
    var showRaw by remember { mutableStateOf(false) }
    var aiStatus by remember { mutableStateOf(contact.aiStatus) }
    var aiSummary by remember { mutableStateOf(contact.aiSummary) }
    var aiRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun withTag(current: List<String>, raw: String): List<String> {
        val tag = raw.trim().trimStart('#').trim()
        return if (tag.isBlank() || current.any { it.equals(tag, ignoreCase = true) }) current else current + tag
    }
    fun addTag(raw: String) {
        tags = withTag(tags, raw)
        tagInput = ""
    }

    fun formContact() = contact.copy(
        name = name.trim(),
        company = company.trim(),
        title = title.trim(),
        email = email.trim(),
        phone = phone.trim(),
        website = website.trim(),
        linkedin = linkedin.trim(),
        department = department.trim(),
        eventName = eventName.trim(),
        stallNumber = stallNumber.trim(),
        hallName = hallName.trim(),
        city = city.trim(),
        country = country.trim(),
        priority = priority,
        assignedTo = assignedTo.trim(),
        nextAction = nextAction.trim(),
        useCase = useCase.trim(),
        followUpDate = followUpDate,
        productInterest = productInterest.trim(),
        notes = notes.trim(),
        stallPhotoPaths = stallPhotos,
        voiceNotePath = voicePath,
        voiceNoteDurationMs = voiceDuration,
        voiceSummary = voiceSummary.trim(),
        // A tag still being typed when Save is tapped counts too.
        tags = withTag(tags, tagInput),
        consentEmail = consentEmail,
        consentWhatsapp = consentWhatsapp,
        aiStatus = aiStatus,
        aiSummary = aiSummary
    )

    // The form can open while OCR (and the AI check) are still running. When the stored card changes,
    // copy the new values into every field the user hasn't edited yet; anything they typed is kept.
    var baseline by remember { mutableStateOf(contact) }
    LaunchedEffect(contact) {
        val before = baseline
        if (before == contact) return@LaunchedEffect
        fun sync(current: String, old: String, new: String) = if (current == old) new else current
        name = sync(name, before.name, contact.name)
        title = sync(title, before.title, contact.title)
        company = sync(company, before.company, contact.company)
        email = sync(email, before.email, contact.email)
        phone = sync(phone, before.phone, contact.phone)
        website = sync(website, before.website, contact.website)
        // City / country arrive from GPS a moment after the scan.
        city = sync(city, before.city, contact.city)
        country = sync(country, before.country, contact.country)
        if (!aiRunning) {
            aiStatus = contact.aiStatus
            aiSummary = contact.aiSummary
        }
        baseline = contact
    }
    val reading = contact.ocrStatus == OcrStatus.PROCESSING
    val aiCheckingInBackground = contact.aiStatus == AiStatus.CHECKING
    // While the on-device model works on this card its fields shimmer; saving is allowed, sending waits.
    val aiBusy = aiRunning || aiCheckingInBackground

    val frontImage = rememberImage(contact.photoPath, maxDimension = 800)
    val backImage = rememberImage(contact.backPhotoPath, maxDimension = 800)

    var pendingStallPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takeStallPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingStallPath?.let { File(it) }
        pendingStallPath = null
        if (success && file != null && file.exists() && file.length() > 0) {
            stallPhotos = stallPhotos + file.absolutePath
        } else {
            file?.delete()
        }
    }
    fun launchStallCamera() {
        val file = ImageUtils.newImageFile(context, "stall")
        pendingStallPath = file.absolutePath
        takeStallPhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    val stallCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchStallCamera()
    }
    val pickStallPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageUtils.copyToAppStorage(context, uri, "stall")?.let { stallPhotos = stallPhotos + it }
    }

    LaunchedEffect(recordingHere) {
        while (recordingHere) {
            recordElapsed = VoiceRecorder.formatDuration(VoiceRecorder.elapsedMs())
            delay(500)
        }
        recordElapsed = "0:00"
    }
    fun startRecording() {
        recordingHere = VoiceRecorder.start(context)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }
    // Leaving the screen mid-recording keeps what was captured rather than losing it.
    DisposableEffect(Unit) {
        onDispose {
            VoicePlayer.stop()
            if (VoiceRecorder.isRecording) {
                VoiceRecorder.stop()?.let {
                    voicePath = it.path
                    voiceDuration = it.durationMs
                }
            }
        }
    }

    val channels = listOfNotNull(
        "Email".takeIf { email.isNotBlank() },
        "WhatsApp".takeIf { phone.isNotBlank() }
    )
    // The backend needs all three; everything else may be left empty.
    val missingRequired = listOfNotNull(
        "company".takeIf { company.isBlank() },
        "email".takeIf { email.isBlank() },
        "phone".takeIf { phone.isBlank() }
    )
    // Named for history: this now only says whether the card is complete enough to *send*.
    val canSave = missingRequired.isEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            name.ifBlank { if (reading) "Reading card..." else "Review card" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Check the details before sending",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
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
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        when {
                            reading -> "Reading the card - save now if you like, the details fill in automatically"
                            aiCheckingInBackground -> "AI still analyzing - save now and keep scanning; Send unlocks when it's done"
                            !canSave -> "Missing ${missingRequired.joinToString(", ")} - save now, add it before sending"
                            else -> "Will send by ${channels.joinToString(" + ")}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (!reading && !aiCheckingInBackground && !canSave) colors.review.content
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onSave(formContact()) },
                        enabled = !aiRunning,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save card", fontWeight = FontWeight.SemiBold)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (contact.photoPath.isNotBlank() || contact.backPhotoPath.isNotBlank()) {
                SectionCard(title = "Card photos", icon = Icons.Filled.Style, tone = colors.pending) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (contact.photoPath.isNotBlank()) CardPhoto(frontImage, "Front", Modifier.weight(1f))
                        if (contact.backPhotoPath.isNotBlank()) CardPhoto(backImage, "Back", Modifier.weight(1f))
                    }
                }
            }

            SectionCard(
                title = "Stall / product photos",
                icon = Icons.Filled.Storefront,
                tone = colors.hot,
                subtitle = "Their booth, product or brochure, for later reference"
            ) {
                if (stallPhotos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stallPhotos.forEach { path ->
                            StallThumb(path = path, onRemove = { stallPhotos = stallPhotos - path })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        shape = FieldShape,
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) launchStallCamera() else stallCameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take photo")
                    }
                    OutlinedButton(
                        shape = FieldShape,
                        onClick = {
                            pickStallPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery")
                    }
                }
            }

            if (reading) {
                Surface(
                    color = colors.brand.container,
                    contentColor = colors.brand.content,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.brand.content
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Reading the card... the fields below fill in automatically in a few seconds. " +
                                "Meanwhile you can add expo notes and stall photos.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (contact.ocrStatus == OcrStatus.FAILED) {
                InlineNotice(
                    "Couldn't read text from this card - please fill in the details by hand.",
                    colors.failed,
                    Icons.Filled.ErrorOutline
                )
            }

            if (aiSummary.isNotBlank() || aiVerifier != null || aiCheckingInBackground) {
                SectionCard(
                    title = "On-device AI check",
                    icon = Icons.Filled.AutoAwesome,
                    tone = colors.ai,
                    modifier = Modifier.aiShimmer(aiBusy, cornerRadius = 20.dp),
                    subtitle = "Compares the fields with the card photo"
                ) {
                    if (aiCheckingInBackground && !aiRunning) {
                        AiAnalyzingLabel("AI analyzing this card...")
                        Text(
                            "Comparing every field with the card photo. Corrections appear here and in the form when it's done.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            aiSummary.ifBlank { "Not checked yet." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (aiStatus == AiStatus.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (aiVerifier != null && !aiCheckingInBackground) {
                        FilledTonalButton(
                            enabled = !aiRunning && !reading,
                            shape = FieldShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.ai.container,
                                contentColor = colors.ai.content
                            ),
                            onClick = {
                                scope.launch {
                                    aiRunning = true
                                    runCatching { aiVerifier.check(formContact()) }
                                        .onSuccess { r ->
                                            name = r.corrected.name
                                            title = r.corrected.title
                                            company = r.corrected.company
                                            email = r.corrected.email
                                            phone = r.corrected.phone
                                            website = r.corrected.website
                                            aiStatus = AiStatus.DONE
                                            aiSummary = r.summary
                                        }
                                        .onFailure { e ->
                                            aiStatus = AiStatus.FAILED
                                            aiSummary = "AI check failed: ${e.message ?: "unknown error"}"
                                        }
                                    aiRunning = false
                                }
                            }
                        ) {
                            if (aiRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.ai.content
                                )
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when {
                                    aiRunning -> "Checking (up to a minute)..."
                                    aiStatus == AiStatus.NONE -> "Check with AI"
                                    else -> "Check again with AI"
                                }
                            )
                        }
                    }
                }
            }

            SectionCard(
                title = "Conversation",
                icon = Icons.Filled.Mic,
                tone = colors.ai,
                subtitle = "Recorded at the stand and kept with this card"
            ) {
                when {
                    recordingHere -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color(0xFFE11D48))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recording… $recordElapsed", style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(
                            shape = FieldShape,
                            onClick = {
                                VoiceRecorder.stop()?.let {
                                    voicePath = it.path
                                    voiceDuration = it.durationMs
                                    voiceSummary = ""
                                }
                                recordingHere = false
                            }
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop and keep")
                        }
                    }

                    voicePath.isNotBlank() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                shape = FieldShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.ai.container,
                                    contentColor = colors.ai.content
                                ),
                                onClick = {
                                    VoicePlayer.toggle(voicePath) { playing = false }
                                    playing = VoicePlayer.isPlaying(voicePath)
                                }
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (playing) "Stop" else "Play ${VoiceRecorder.formatDuration(voiceDuration)}")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                VoicePlayer.stop()
                                java.io.File(voicePath).delete()
                                voicePath = ""
                                voiceDuration = 0L
                                voiceSummary = ""
                                playing = false
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete recording")
                            }
                        }

                        if (voiceSummary.isNotBlank()) {
                            Surface(color = colors.ai.container, contentColor = colors.ai.content, shape = FieldShape) {
                                Text(
                                    voiceSummary,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        val canSummarise = aiVerifier?.canSummariseAudio() == true
                        if (canSummarise) {
                            FilledTonalButton(
                                enabled = !summarising,
                                shape = FieldShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.ai.container,
                                    contentColor = colors.ai.content
                                ),
                                onClick = {
                                    scope.launch {
                                        summarising = true
                                        runCatching { aiVerifier.summariseConversation(voicePath) }
                                            .onSuccess { voiceSummary = it }
                                            .onFailure { voiceSummary = "Couldn't summarise: ${it.message ?: "unknown error"}" }
                                        summarising = false
                                    }
                                }
                            ) {
                                if (summarising) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.ai.content
                                    )
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    when {
                                        summarising -> "Listening to the recording…"
                                        voiceSummary.isBlank() -> "Summarise on this phone"
                                        else -> "Summarise again"
                                    }
                                )
                            }
                        } else {
                            Text(
                                "Choose Gemma 4 in Settings to summarise recordings on the phone. " +
                                    "The recording is uploaded with the card either way.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        OutlinedButton(
                            shape = FieldShape,
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Record the conversation")
                        }
                        Text(
                            "Kept on the phone with this card, so you can come back to what was actually said.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionCard(
                title = "Contact details",
                icon = Icons.Filled.Person,
                tone = colors.navy,
                modifier = Modifier.aiShimmer(aiBusy, cornerRadius = 20.dp),
                subtitle = "OCR guesses aren't always right - double-check them"
            ) {
                FormField(name, { name = it }, "Name", Icons.Filled.Person)
                FormField(title, { title = it }, "Title", Icons.Filled.Work)
                FormField(company, { company = it }, "Company *", Icons.Filled.Business, required = true)
                FormField(email, { email = it }, "Email *", Icons.Filled.Email, KeyboardType.Email, required = true)
                FormField(
                    phone, { phone = it }, "Phone / WhatsApp (with country code) *",
                    Icons.Filled.Phone, KeyboardType.Phone, required = true
                )
                FormField(website, { website = it }, "Website", Icons.Filled.Language, KeyboardType.Uri)
                FormField(linkedin, { linkedin = it }, "LinkedIn", Icons.Filled.Link, KeyboardType.Uri)
                FormField(department, { department = it }, "Department", Icons.Filled.Groups)
                Text(
                    "* Company, email and phone are needed before sending. You can save without them - the AI may fill them in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(
                title = "Expo & location",
                icon = Icons.Filled.Place,
                tone = colors.warm,
                subtitle = "Where you met them"
            ) {
                if (editingEvent) {
                    FormField(eventName, { eventName = it }, "Expo name", Icons.Filled.Event)
                    if (defaultEventName.isBlank()) {
                        Text(
                            "Tip: set the expo name once in Settings and it's filled in on every card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Surface(color = colors.warm.container, contentColor = colors.warm.content, shape = FieldShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text("Expo", style = MaterialTheme.typography.labelSmall)
                                Text(eventName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { editingEvent = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = colors.warm.content)
                            ) { Text("Change") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField(stallNumber, { stallNumber = it }, "Stall no.", Icons.Filled.Storefront, modifier = Modifier.weight(1f))
                    FormField(hallName, { hallName = it }, "Hall", Icons.Filled.MeetingRoom, modifier = Modifier.weight(1f))
                }
                val hallChoices = suggestedHalls.filterNot { it.equals(hallName.trim(), ignoreCase = true) }
                if (hallChoices.isNotEmpty()) {
                    Text(
                        "Recent halls",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        hallChoices.forEach { hall ->
                            AssistChip(
                                onClick = { hallName = hall },
                                label = { Text(hall) },
                                leadingIcon = { Icon(Icons.Filled.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(50),
                                colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = colors.warm.content)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField(city, { city = it }, "City", Icons.Filled.LocationOn, modifier = Modifier.weight(1f))
                    FormField(country, { country = it }, "Country", Icons.Filled.Public, modifier = Modifier.weight(1f))
                }
                Text(
                    if (contact.geoLat != null && contact.geoLng != null) {
                        "GPS attached: %.5f, %.5f".format(contact.geoLat, contact.geoLng)
                    } else {
                        "No GPS position on this card (turn it on in Settings, or allow location)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(
                title = "Expo notes",
                icon = Icons.Filled.EditNote,
                tone = colors.brand,
                subtitle = "Sent with the card details by email and WhatsApp"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    QUICK_NOTES.forEach { note ->
                        AssistChip(
                            onClick = { notes = appendNote(notes, note) },
                            label = { Text(note) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(50),
                            colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = colors.brand.content)
                        )
                    }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("What did you talk about?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                    minLines = 4
                )
            }

            SectionCard(
                title = "Lead & follow-up",
                icon = Icons.Filled.Flag,
                tone = colors.hot,
                subtitle = "Who owns it and what happens next"
            ) {
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LeadPriority.values().forEach { p ->
                        val (tone, icon) = priorityStyle(p, colors)
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) },
                            leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) } },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tone.container,
                                selectedLabelColor = tone.content,
                                selectedLeadingIconColor = tone.content,
                                iconColor = tone.accent
                            )
                        )
                    }
                }
                FormField(assignedTo, { assignedTo = it }, "Assigned to", Icons.Filled.Person)
                FormField(useCase, { useCase = it }, "Use case (what they want to build)", Icons.Filled.Lightbulb)
                Text(
                    "Used in the WhatsApp and email follow-up, e.g. \"hearing about smart parking sensors\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FormField(nextAction, { nextAction = it }, "Next action", Icons.Filled.TaskAlt)
                FollowUpDateField(followUpDate, { followUpDate = it })
                FormField(productInterest, { productInterest = it }, "Product interest (comma separated)", Icons.Filled.Inventory2)
            }

            SectionCard(
                title = "Tags",
                icon = Icons.Filled.LocalOffer,
                tone = colors.pending,
                subtitle = "Group cards, e.g. distributor, VIP, follow-up"
            ) {
                val suggestions = suggestedTags.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }
                if (suggestions.isNotEmpty()) {
                    Text(
                        "Recently used",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = { addTag(suggestion) },
                                label = { Text(suggestion) },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(50),
                                colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = colors.pending.accent)
                            )
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags = tags - tag },
                                label = { Text(tag) },
                                leadingIcon = { Icon(Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $tag", modifier = Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(50),
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = colors.pending.container,
                                    selectedLabelColor = colors.pending.content,
                                    selectedLeadingIconColor = colors.pending.content,
                                    selectedTrailingIconColor = colors.pending.content
                                )
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { value ->
                        // A comma finishes a tag, so several can be typed in a row.
                        if (value.endsWith(",")) addTag(value.dropLast(1)) else tagInput = value
                    },
                    label = { Text("Add a tag") },
                    leadingIcon = { Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    trailingIcon = {
                        if (tagInput.isNotBlank()) {
                            IconButton(onClick = { addTag(tagInput) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add tag")
                            }
                        }
                    },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionCard(
                title = "Consent",
                icon = Icons.Filled.VerifiedUser,
                tone = colors.sent,
                subtitle = "Recorded with the card (consent_email / consent_whatsapp)"
            ) {
                ConsentRow("They're happy to get an email", consentEmail, colors.sent.accent) { consentEmail = it }
                ConsentRow("They're happy to get a WhatsApp message", consentWhatsapp, colors.sent.accent) { consentWhatsapp = it }
            }

            if (contact.rawOcrText.isNotBlank()) {
                SectionCard(title = "Scanned text", icon = Icons.Filled.Description, tone = colors.neutral) {
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Hide raw scanned text" else "Show raw scanned text")
                    }
                    if (showRaw) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = FieldShape,
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

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    required: Boolean = false,
    modifier: Modifier = Modifier
) {
    val missing = required && value.isBlank()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (missing) AppTheme.colors.review.accent else MaterialTheme.colorScheme.secondary
            )
        },
        // Amber hint rather than a red error: saving is fine, only sending needs it.
        supportingText = if (missing) {
            { Text("Needed before sending", color = AppTheme.colors.review.content) }
        } else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

/** Read-only field that opens the system date picker; stores an ISO date for `follow_up_date`. */
@Composable
private fun FollowUpDateField(value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    fun openPicker() {
        val calendar = Calendar.getInstance()
        value.split("-").takeIf { it.size == 3 }?.let { (y, m, d) ->
            runCatching { calendar.set(y.toInt(), m.toInt() - 1, d.toInt()) }
        }
        DatePickerDialog(
            context,
            { _, year, month, day -> onValueChange("%04d-%02d-%02d".format(year, month + 1, day)) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text("Follow-up date") },
        placeholder = { Text("Not set") },
        leadingIcon = {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        },
        trailingIcon = {
            Row {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear follow-up date")
                    }
                }
                IconButton(onClick = { openPicker() }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick a date")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = FieldShape
    )
}

@Composable
private fun ConsentRow(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
}

private fun priorityStyle(priority: LeadPriority, colors: ExtendedColors): Pair<Tone, ImageVector?> = when (priority) {
    LeadPriority.NONE -> colors.neutral to Icons.Filled.RemoveCircleOutline
    LeadPriority.HOT -> colors.hot to Icons.Filled.LocalFireDepartment
    LeadPriority.WARM -> colors.warm to Icons.Filled.WbSunny
    LeadPriority.COLD -> colors.cold to Icons.Filled.AcUnit
}

@Composable
private fun CardPhoto(bitmap: ImageBitmap?, label: String, modifier: Modifier) {
    Column(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$label of card",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {}
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun StallThumb(path: String, onRemove: () -> Unit) {
    val bitmap = rememberImage(path, maxDimension = 300)
    Box(modifier = Modifier.size(96.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Stall photo",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {}
        }
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove photo", modifier = Modifier.padding(4.dp))
        }
    }
}

private fun appendNote(current: String, note: String): String =
    if (current.isBlank()) note else current.trimEnd() + "\n" + note

