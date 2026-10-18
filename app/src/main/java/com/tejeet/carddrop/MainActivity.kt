package com.tejeet.carddrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tejeet.carddrop.ai.CardAiVerifier
import com.tejeet.carddrop.audio.VoiceRecorder
import com.tejeet.carddrop.ai.AiModelFiles
import com.tejeet.carddrop.data.ContactStore
import com.tejeet.carddrop.data.SettingsStore
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.OcrStatus
import com.tejeet.carddrop.model.SendStatus
import com.tejeet.carddrop.network.BackendClient
import com.tejeet.carddrop.ocr.CardScanProcessor
import com.tejeet.carddrop.sync.CardSyncWorker
import androidx.compose.animation.Crossfade
import com.tejeet.carddrop.ui.BrandSplashScreen
import com.tejeet.carddrop.ui.CardDetailScreen
import com.tejeet.carddrop.ui.CardFilter
import com.tejeet.carddrop.ui.CardScannerScreen
import com.tejeet.carddrop.ui.MainScreen
import com.tejeet.carddrop.ui.MainTab
import com.tejeet.carddrop.ui.ReviewCardScreen
import com.tejeet.carddrop.ui.theme.CardDropTheme
import com.tejeet.carddrop.util.DeviceInfo
import com.tejeet.carddrop.util.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Which screen is showing. Deliberately not Navigation-Compose: this app is a handful of screens,
 *  and a plain sealed class keeps the dependency list (and the things that can go wrong) small. */
/** Where the review form was opened from, so closing it goes back there. */
enum class ReviewOrigin { MAIN, SCANNER, DETAIL }

sealed class Screen {
    /** Bottom-navigation shell: Home, Cards, Search, Settings. */
    object Main : Screen()
    /** In-app card scanner; [frontPath] is set once the front is captured and the back is next. */
    data class Scanner(val frontPath: String? = null) : Screen()
    /** Read-only view of one saved card, with call / WhatsApp / email actions. */
    data class Detail(val contactId: String) : Screen()
    /** Review form for one card, always showing its latest stored version. */
    data class Review(val contactId: String, val origin: ReviewOrigin = ReviewOrigin.MAIN) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            CardDropTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    var showBrandSplash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1100)
                        showBrandSplash = false
                    }
                    Crossfade(targetState = showBrandSplash, label = "splash") { splash ->
                        if (splash) BrandSplashScreen() else CardDropApp()
                    }
                }
            }
        }
    }
}

@Composable
fun CardDropApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val contactStore = remember { ContactStore.get(context) }
    val settingsStore = remember { SettingsStore(context.applicationContext) }

    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var mainTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var cardsFilter by rememberSaveable { mutableStateOf(CardFilter.ALL) }
    var scannedThisSession by remember { mutableIntStateOf(0) }
    var lastSavedFrontPath by remember { mutableStateOf<String?>(null) }
    var visitRecording by remember { mutableStateOf(false) }
    var recordingElapsed by remember { mutableStateOf("0:00") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        CardScanProcessor.resumePending(contactStore, context)
    }

    // Timer for the REC chip in the scanner.
    LaunchedEffect(visitRecording) {
        while (visitRecording) {
            recordingElapsed = VoiceRecorder.formatDuration(VoiceRecorder.elapsedMs())
            delay(500)
        }
        recordingElapsed = "0:00"
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            visitRecording = VoiceRecorder.start(context)
        } else {
            errorMessage = "Microphone permission is needed to record the conversation."
        }
    }

    /** Starts recording the conversation; it attaches to the next card saved. Toggling off discards it. */
    fun toggleVisitRecording() {
        if (VoiceRecorder.isRecording) {
            VoiceRecorder.cancel()
            visitRecording = false
            infoMessage = "Recording discarded."
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            visitRecording = VoiceRecorder.start(context)
            if (!visitRecording) errorMessage = "Couldn't start recording - the microphone may be in use."
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openScanner() {
        errorMessage = null
        infoMessage = null
        scannedThisSession = 0
        screen = Screen.Scanner()
    }

    // Camera is required to scan; location is optional and only asked for when the setting is on.
    val scanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.CAMERA] == true) {
            openScanner()
        } else {
            errorMessage = "Camera permission is needed to scan cards."
        }
    }

    /** Camera already granted, so whatever the user answers about location, scanning goes ahead. */
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { openScanner() }

    fun startScan() {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val wantsLocation = settingsStore.attachLocation && !LocationProvider.hasPermission(context)
        when {
            !cameraGranted -> scanPermissionLauncher.launch(
                buildList {
                    add(Manifest.permission.CAMERA)
                    if (wantsLocation) {
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }.toTypedArray()
            )
            wantsLocation -> locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
            else -> openScanner()
        }
    }

    /** Saves the card straight away and starts reading its text in the background. Returns the new card's id. */
    fun finishCapture(front: String, back: String?): String {
        // A visit recording in progress belongs to this card.
        val visit = if (VoiceRecorder.isRecording) VoiceRecorder.stop() else null
        visitRecording = false
        val contact = Contact(
            photoPath = front,
            backPhotoPath = back.orEmpty(),
            eventName = settingsStore.eventName,
            assignedTo = settingsStore.assignedTo,
            // Moving hall to hall: the last one used is pre-selected.
            hallName = settingsStore.recentHalls.firstOrNull().orEmpty(),
            voiceNotePath = visit?.path.orEmpty(),
            voiceNoteDurationMs = visit?.durationMs ?: 0L,
            ocrStatus = OcrStatus.PROCESSING
        )
        contactStore.add(contact)
        CardScanProcessor.process(contact.id, contactStore, context)
        CardScanProcessor.attachLocation(contact.id, contactStore, context)
        scannedThisSession++
        lastSavedFrontPath = front
        return contact.id
    }

    /** Both sides done (or the back skipped): go straight to the form, which fills in as OCR finishes. */
    fun openFormForCapture(front: String, back: String?) {
        screen = Screen.Review(finishCapture(front, back), origin = ReviewOrigin.SCANNER)
    }

    fun leaveReview(review: Screen.Review) {
        screen = when (review.origin) {
            ReviewOrigin.SCANNER -> Screen.Scanner()
            ReviewOrigin.DETAIL -> Screen.Detail(review.contactId)
            ReviewOrigin.MAIN -> Screen.Main
        }
    }

    fun closeScanner() {
        // A captured front whose back was never taken is still a card worth keeping.
        (screen as? Screen.Scanner)?.frontPath?.let { finishCapture(it, null) }
        screen = Screen.Main
        if (scannedThisSession > 0) {
            infoMessage = "$scannedThisSession card(s) saved. Reading text in the background - tap a card once it's ready to review."
        }
    }

    /** Queues every card for background upload; sends no emails or WhatsApp messages. */
    fun syncAll() {
        val total = contactStore.contacts.size
        if (total == 0) {
            infoMessage = "No cards to sync yet."
            return
        }
        CardSyncWorker.syncAll(context)
        infoMessage = "Syncing $total card(s) to the server in the background."
    }

    fun send(contacts: List<Contact>) {
        when {
            settingsStore.backendUrl.isBlank() ->
                errorMessage = "Set the backend URL in Settings before sending."
            contacts.isNotEmpty() -> scope.launch {
                infoMessage = "Sending ${contacts.size} card(s)..."
                val (sent, failed) = sendContacts(contacts, contactStore, settingsStore, context)
                infoMessage = if (failed == 0) "Sent $sent card(s)." else "Sent $sent, failed $failed - check the Failed tab."
            }
        }
    }

    fun onSettingsSaved() {
        // Cards scanned before the expo name was set pick it up now.
        val expo = settingsStore.eventName
        if (expo.isNotBlank()) {
            contactStore.contacts
                .filter { it.eventName.isBlank() }
                .forEach { contactStore.update(it.copy(eventName = expo)) }
        }
        // The model may have just finished downloading - check any cards still waiting for review.
        CardScanProcessor.resumePending(contactStore, context)
    }

    BackHandler(enabled = screen !is Screen.Main || mainTab != MainTab.HOME) {
        when (val current = screen) {
            is Screen.Scanner -> closeScanner()
            is Screen.Review -> leaveReview(current)
            is Screen.Detail -> screen = Screen.Main
            is Screen.Main -> mainTab = MainTab.HOME
        }
    }

    when (val current = screen) {
        is Screen.Main -> MainScreen(
            tab = mainTab,
            onTabChange = { mainTab = it },
            cardsFilter = cardsFilter,
            onCardsFilterChange = { cardsFilter = it },
            contacts = contactStore.contacts,
            settingsStore = settingsStore,
            errorMessage = errorMessage,
            infoMessage = infoMessage,
            dismissError = { errorMessage = null },
            dismissInfo = { infoMessage = null },
            onScanClick = { startScan() },
            onContactClick = { c ->
                // A card that's been reviewed opens its detail view; one still waiting goes straight to the form.
                screen = if (c.reviewed && c.ocrStatus != OcrStatus.PROCESSING && c.missingRequired().isEmpty()) {
                    Screen.Detail(c.id)
                } else {
                    Screen.Review(c.id)
                }
            },
            onSendAll = { send(contactStore.contacts.filter { it.isReadyToSend }) },
            onSyncAll = { syncAll() },
            onSendOne = { c -> send(listOf(c)) },
            onDelete = { c -> contactStore.remove(c) },
            onSettingsSaved = { onSettingsSaved() }
        )

        is Screen.Scanner -> CardScannerScreen(
            scanningBack = current.frontPath != null,
            savedCount = scannedThisSession,
            recording = visitRecording,
            recordingElapsed = recordingElapsed,
            onToggleRecording = { toggleVisitRecording() },
            frontPreviewPath = current.frontPath,
            lastSavedPath = lastSavedFrontPath,
            onRetakeFront = {
                current.frontPath?.let { File(it).delete() }
                screen = Screen.Scanner()
            },
            onCaptured = { path ->
                if (current.frontPath == null) {
                    screen = Screen.Scanner(frontPath = path)
                } else {
                    openFormForCapture(current.frontPath, path)
                }
            },
            onSkipBack = { current.frontPath?.let { openFormForCapture(it, null) } },
            onDone = { closeScanner() }
        )

        is Screen.Detail -> {
            val live = contactStore.find(current.contactId)
            if (live == null) {
                LaunchedEffect(current) { screen = Screen.Main }
            } else {
                CardDetailScreen(
                    contact = live,
                    defaultEventName = settingsStore.eventName,
                    onEdit = { screen = Screen.Review(live.id, origin = ReviewOrigin.DETAIL) },
                    onBack = { screen = Screen.Main },
                    onSend = { send(listOf(live)) },
                    onDelete = {
                        contactStore.remove(live)
                        if (lastSavedFrontPath == live.photoPath) lastSavedFrontPath = null
                        screen = Screen.Main
                    }
                )
            }
        }

        is Screen.Review -> {
            // Read from the store on every recomposition so background OCR / AI results reach the open form.
            val live = contactStore.find(current.contactId)
            if (live == null) {
                LaunchedEffect(current) { leaveReview(current) }
            } else {
                key(current.contactId) {
                    ReviewCardScreen(
                        contact = live,
                        defaultEventName = settingsStore.eventName,
                        defaultAssignedTo = settingsStore.assignedTo,
                        suggestedHalls = settingsStore.recentHalls,
                        suggestedTags = settingsStore.recentTags,
                        aiVerifier = if (AiModelFiles.selectedIsReady(context)) CardAiVerifier.get(context) else null,
                        onSave = { edited ->
                            saveReviewed(contactStore, edited)
                            // Every saved card goes to the server in the background straight away.
                            CardSyncWorker.syncCard(context, edited.id)
                            settingsStore.rememberTagsUsed(edited.tags)
                            settingsStore.rememberHallUsed(edited.hallName)
                            leaveReview(current)
                        },
                        onCancel = { leaveReview(current) },
                        onDelete = {
                            contactStore.remove(live)
                            if (lastSavedFrontPath == live.photoPath) lastSavedFrontPath = null
                            leaveReview(current)
                        }
                    )
                }
            }
        }
    }
}

/** Applies review-screen edits to the stored card, resetting a channel's sent flag if its address changed. */
private fun saveReviewed(store: ContactStore, edited: Contact) {
    val base = store.find(edited.id) ?: return
    val emailChanged = !base.email.equals(edited.email, ignoreCase = true)
    val phoneChanged = base.phone != edited.phone
    // manually_edited tells the backend a human corrected what OCR (or the AI) produced.
    val touchedByHand = base.manuallyEdited ||
        listOf(
            base.name to edited.name,
            base.title to edited.title,
            base.company to edited.company,
            base.email to edited.email,
            base.phone to edited.phone,
            base.website to edited.website
        ).any { (before, after) -> before != after }

    val updated = base.copy(
        name = edited.name,
        title = edited.title,
        company = edited.company,
        email = edited.email,
        phone = edited.phone,
        website = edited.website,
        linkedin = edited.linkedin,
        department = edited.department,
        notes = edited.notes,
        eventName = edited.eventName,
        stallNumber = edited.stallNumber,
        hallName = edited.hallName,
        city = edited.city,
        country = edited.country,
        priority = edited.priority,
        assignedTo = edited.assignedTo,
        nextAction = edited.nextAction,
        useCase = edited.useCase,
        voiceNotePath = edited.voiceNotePath,
        voiceNoteDurationMs = edited.voiceNoteDurationMs,
        voiceSummary = edited.voiceSummary,
        followUpDate = edited.followUpDate,
        productInterest = edited.productInterest,
        consentEmail = edited.consentEmail,
        consentWhatsapp = edited.consentWhatsapp,
        manuallyEdited = touchedByHand,
        stallPhotoPaths = edited.stallPhotoPaths,
        tags = edited.tags,
        // A card saved while the AI is still analysing keeps whatever the AI has reported since.
        aiStatus = if (edited.aiStatus == AiStatus.CHECKING) base.aiStatus else edited.aiStatus,
        aiSummary = if (edited.aiStatus == AiStatus.CHECKING) base.aiSummary else edited.aiSummary,
        reviewed = true,
        emailSent = base.emailSent && !emailChanged,
        whatsappSent = base.whatsappSent && !phoneChanged
    )
    val status = when {
        updated.status == SendStatus.SENDING -> SendStatus.SENDING
        updated.pendingChannels().isEmpty() && (updated.emailSent || updated.whatsappSent) -> SendStatus.SENT
        updated.status == SendStatus.SENT -> SendStatus.PENDING
        else -> updated.status
    }
    store.update(updated.copy(status = status))
}

/**
 * Sends contacts one at a time so a single failure never blocks the rest of the queue.
 * Each channel's flag is only set when the backend confirms it, and a retry asks only for
 * the channels still missing. Returns (sent, failed) counts.
 */
private suspend fun sendContacts(
    contacts: List<Contact>,
    contactStore: ContactStore,
    settingsStore: SettingsStore,
    context: android.content.Context
): Pair<Int, Int> {
    val client = BackendClient(
        backendUrl = settingsStore.backendUrl,
        sharedSecret = settingsStore.sharedSecret,
        senderName = settingsStore.senderName,
        defaultEventName = settingsStore.eventName,
        deviceId = DeviceInfo.deviceId(context),
        appVersion = DeviceInfo.appVersion(),
        language = DeviceInfo.language()
    )
    // A few uploads at once: on slow expo Wi-Fi most of the time is spent waiting on the network,
    // not the phone. Store updates still happen on the main thread; only the request runs on IO.
    val uploads = Semaphore(MAX_PARALLEL_UPLOADS)
    val results = coroutineScope {
        contacts.map { queued ->
            async { uploads.withPermit { sendOne(queued, contactStore, client) } }
        }.awaitAll()
    }
    return results.count { it == true } to results.count { it == false }
}

private const val MAX_PARALLEL_UPLOADS = 3

/** Sends one card. Returns true when every channel is confirmed, false on failure, null if it wasn't sendable. */
private suspend fun sendOne(queued: Contact, contactStore: ContactStore, client: BackendClient): Boolean? {
    val original = contactStore.find(queued.id) ?: return null
    if (!original.isReadyToSend) return null
    val channels = original.pendingChannels()

    contactStore.update(original.copy(status = SendStatus.SENDING))
    val result = withContext(Dispatchers.IO) { client.sendContact(original, channels) }

    val latest = contactStore.find(original.id) ?: return null
    val flagged = latest.copy(
        emailSent = latest.emailSent || (result.emailSent && latest.email == original.email),
        whatsappSent = latest.whatsappSent || (result.whatsappSent && latest.phone == original.phone)
    )
    val done = flagged.pendingChannels().isEmpty()
    contactStore.update(
        flagged.copy(
            status = if (done) SendStatus.SENT else SendStatus.FAILED,
            lastError = if (done) "" else result.error.ifBlank { "Not confirmed by the backend" },
            sentAt = if (done) System.currentTimeMillis() else latest.sentAt
        )
    )
    return done
}
