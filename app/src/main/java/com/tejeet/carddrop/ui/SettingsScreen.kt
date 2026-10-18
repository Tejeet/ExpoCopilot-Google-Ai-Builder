package com.tejeet.carddrop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tejeet.carddrop.ai.CardAiVerifier
import com.tejeet.carddrop.ai.AiModel
import com.tejeet.carddrop.ai.AiModelDownloader
import com.tejeet.carddrop.ai.AiModelFiles
import com.tejeet.carddrop.data.SettingsStore
import com.tejeet.carddrop.network.ApiConfig
import com.tejeet.carddrop.ui.components.FieldShape
import com.tejeet.carddrop.ui.components.InlineNotice
import com.tejeet.carddrop.ui.components.SectionCard
import com.tejeet.carddrop.ui.components.TabTopBar
import com.tejeet.carddrop.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    /** Called after settings are written, e.g. to give existing cards the new expo name. */
    onSaved: () -> Unit
) {
    val colors = AppTheme.colors
    var hallName by remember { mutableStateOf(settingsStore.recentHalls.firstOrNull().orEmpty()) }
    var senderName by remember { mutableStateOf(settingsStore.senderName) }
    var eventName by remember { mutableStateOf(settingsStore.eventName) }
    var assignedTo by remember { mutableStateOf(settingsStore.assignedTo) }
    var attachLocation by remember { mutableStateOf(settingsStore.attachLocation) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun persist() {
        settingsStore.rememberHallUsed(hallName)
        settingsStore.senderName = senderName.trim()
        settingsStore.eventName = eventName.trim()
        settingsStore.assignedTo = assignedTo.trim()
        settingsStore.attachLocation = attachLocation
        onSaved()
    }

    fun saveWithConfirmation() {
        persist()
        scope.launch { snackbarHostState.showSnackbar("Settings saved") }
    }

    // Switching to another tab saves too, so nothing typed here is lost.
    DisposableEffect(Unit) {
        onDispose { persist() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TabTopBar(
                title = "Settings",
                actions = {
                    TextButton(onClick = { saveWithConfirmation() }) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard(
                title = "Current expo",
                icon = Icons.Filled.Storefront,
                tone = colors.warm,
                subtitle = "Filled in automatically on every card you scan"
            ) {
                SettingsField(eventName, { eventName = it }, "Expo name", Icons.Filled.Event)
                SettingsField(hallName, { hallName = it }, "Current hall", Icons.Filled.MeetingRoom)
                Text(
                    "Set this once before the event. Cards already scanned without an expo name get it when you save. " +
                        "You can still change it for a single card on its review screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(
                title = "Your details",
                icon = Icons.Filled.Person,
                tone = colors.navy,
                subtitle = "Used in the follow-up messages"
            ) {
                SettingsField(senderName, { senderName = it }, "Your name", Icons.Filled.Person)
                SettingsField(assignedTo, { assignedTo = it }, "Assigned to (rep who follows up)", Icons.Filled.AssignmentInd)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Attach location to each card", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Saves GPS coordinates plus the city and country (geo_lat, geo_lng, city, country). " +
                                "Needs location permission; cards still save without it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = attachLocation,
                        onCheckedChange = { attachLocation = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.brand.accent)
                    )
                }
            }

            SectionCard(
                title = "Backend API",
                icon = Icons.Filled.Cloud,
                tone = colors.pending,
                subtitle = "Sends the email and WhatsApp message"
            ) {
                Text(
                    "Each card is POSTed here as multipart/form-data (details, expo notes and photos). " +
                        "See \"Backend API contract\" in DEVELOPMENT.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                InlineNotice(ApiConfig.SEND_CARD_URL, colors.pending, Icons.Filled.Link)
                Text(
                    "Hardcoded in network/ApiConfig.kt (HOST, SEND_CARD_PATH, SHARED_SECRET) - change it there and rebuild.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(
                title = "On-device AI check",
                icon = Icons.Filled.AutoAwesome,
                tone = colors.ai,
                subtitle = "Open-weight models, running on this phone"
            ) {
                AiModelSection(settingsStore)
            }

            Button(
                onClick = { saveWithConfirmation() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save settings", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = FieldShape
    )
}

@Composable
private fun AiModelSection(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(AiModel.fromId(settingsStore.aiModelId)) }
    val downloader = remember(selected) { AiModelDownloader(context, selected) }
    var state by remember(selected) { mutableStateOf(downloader.state()) }
    var autoCheck by remember { mutableStateOf(settingsStore.aiAutoCheck) }
    var wifiOnly by remember { mutableStateOf(settingsStore.aiWifiOnly) }

    LaunchedEffect(selected) {
        while (true) {
            state = downloader.state()
            delay(1000)
        }
    }

    Text(
        "Runs entirely on this phone - no internet needed once downloaded. After a card's text is read, " +
            "it corrects OCR mistakes before you review.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    AiModel.values().forEach { model ->
        val isSelected = model == selected
        Surface(
            onClick = {
                selected = model
                settingsStore.aiModelId = model.id
                // The engine holds whichever model was loaded; drop it so the next check loads this one.
                scope.launch { CardAiVerifier.get(context).release() }
            },
            shape = FieldShape,
            color = if (isSelected) colors.ai.container else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) colors.ai.content else MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, if (isSelected) colors.ai.accent else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = {
                        selected = model
                        settingsStore.aiModelId = model.id
                        scope.launch { CardAiVerifier.get(context).release() }
                    },
                    colors = RadioButtonDefaults.colors(selectedColor = colors.ai.accent)
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        "${model.displayName} · ${model.sizeLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        model.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) colors.ai.content else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (AiModelFiles.isReady(context, model)) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = colors.sent.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    when (val s = state) {
        AiModelDownloader.State.Ready -> {
            InlineNotice("${selected.displayName} is downloaded and ready", colors.sent, Icons.Filled.CheckCircle)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check new cards automatically", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "One card at a time, in the background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCheck,
                    onCheckedChange = {
                        autoCheck = it
                        settingsStore.aiAutoCheck = it
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.ai.accent)
                )
            }
            OutlinedButton(
                shape = FieldShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = {
                    scope.launch {
                        CardAiVerifier.get(context).release()
                        downloader.cancelAndDelete()
                        state = downloader.state()
                    }
                }
            ) { Text("Delete ${selected.displayName} (frees ${selected.sizeLabel})") }
        }

        is AiModelDownloader.State.Downloading -> {
            val fraction = (s.downloadedBytes.toFloat() / s.totalBytes).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colors.ai.accent,
                trackColor = colors.ai.container,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Text(
                if (s.paused) "Paused - waiting for ${if (wifiOnly) "Wi-Fi" else "a connection"} " +
                    "(${gb(s.downloadedBytes)} of ${gb(s.totalBytes)})"
                else "Downloading ${(fraction * 100).toInt()}% · ${gb(s.downloadedBytes)} of ${gb(s.totalBytes)} · " +
                    "you can leave this screen",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = {
                downloader.cancelAndDelete()
                state = downloader.state()
            }) { Text("Cancel download") }
        }

        is AiModelDownloader.State.Failed, AiModelDownloader.State.NotDownloaded -> {
            if (s is AiModelDownloader.State.Failed) {
                Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = wifiOnly,
                    onCheckedChange = {
                        wifiOnly = it
                        settingsStore.aiWifiOnly = it
                    },
                    colors = CheckboxDefaults.colors(checkedColor = colors.ai.accent)
                )
                Text("Download over Wi-Fi only", style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.ai.accent),
                onClick = {
                    downloader.start(wifiOnly)
                    state = downloader.state()
                }
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (s is AiModelDownloader.State.Failed) "Retry download"
                    else "Download ${selected.displayName} (${selected.sizeLabel})"
                )
            }
        }
    }
}

private fun gb(bytes: Long): String = String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
