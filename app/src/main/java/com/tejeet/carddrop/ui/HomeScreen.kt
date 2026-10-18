package com.tejeet.carddrop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tejeet.carddrop.R
import com.tejeet.carddrop.model.AiStatus
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.OcrStatus
import com.tejeet.carddrop.ui.components.ContactCard
import com.tejeet.carddrop.ui.components.EmptyState
import com.tejeet.carddrop.ui.components.IconBadge
import com.tejeet.carddrop.ui.components.MessageBanner
import com.tejeet.carddrop.ui.components.SectionHeader
import com.tejeet.carddrop.ui.theme.AppTheme
import com.tejeet.carddrop.ui.theme.BrandNavy
import com.tejeet.carddrop.ui.theme.BrandTeal
import com.tejeet.carddrop.ui.theme.OnBrandTeal

/** Home tab: at-a-glance dashboard with quick actions, status overview and the latest cards. */
@Composable
fun HomeScreen(
    contacts: List<Contact>,
    eventName: String,
    senderName: String,
    errorMessage: String?,
    infoMessage: String?,
    dismissError: () -> Unit,
    dismissInfo: () -> Unit,
    onScanClick: () -> Unit,
    onSendAll: () -> Unit,
    onOpenCards: (CardFilter) -> Unit,
    onOpenSearch: () -> Unit,
    onContactClick: (Contact) -> Unit,
    onSendOne: (Contact) -> Unit,
    onDelete: (Contact) -> Unit
) {
    val colors = AppTheme.colors
    val sendableCount = contacts.count { it.isReadyToSend }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { HomeHeader(contacts, eventName, senderName, onOpenSearch) }

        if (errorMessage != null) {
            item {
                MessageBanner(
                    errorMessage, colors.failed, Icons.Filled.ErrorOutline, dismissError,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
            }
        }
        if (infoMessage != null) {
            item {
                MessageBanner(
                    infoMessage, colors.pending, Icons.Filled.Info, dismissInfo,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionTile(
                    title = "Scan cards",
                    subtitle = "Front & back, then fill the form",
                    icon = Icons.Filled.DocumentScanner,
                    container = BrandTeal,
                    content = OnBrandTeal,
                    enabled = true,
                    scanning = true,
                    onClick = onScanClick,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                ActionTile(
                    title = if (sendableCount > 0) "Send all" else "All caught up",
                    subtitle = if (sendableCount > 0) "$sendableCount ready · Email + WhatsApp" else "Nothing waiting to send",
                    icon = if (sendableCount > 0) Icons.Filled.Send else Icons.Filled.DoneAll,
                    container = BrandNavy,
                    content = Color.White,
                    enabled = sendableCount > 0,
                    onClick = onSendAll,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        item { SectionHeader("Overview") }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusTile(CardFilter.TO_REVIEW, contacts.count { CardFilter.TO_REVIEW.matches(it) }, onOpenCards, Modifier.weight(1f))
                    StatusTile(CardFilter.PENDING, contacts.count { CardFilter.PENDING.matches(it) }, onOpenCards, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusTile(CardFilter.SENT, contacts.count { CardFilter.SENT.matches(it) }, onOpenCards, Modifier.weight(1f))
                    StatusTile(CardFilter.FAILED, contacts.count { CardFilter.FAILED.matches(it) }, onOpenCards, Modifier.weight(1f))
                }
            }
        }

        item {
            SectionHeader(
                title = "Recent cards",
                actionLabel = if (contacts.size > RECENT_COUNT) "See all (${contacts.size})" else null,
                onAction = { onOpenCards(CardFilter.ALL) }
            )
        }
        if (contacts.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.DocumentScanner, colors.brand,
                    "No cards yet",
                    "Tap \"Scan cards\" and line each card up inside the frame. Front, back, then the form - " +
                        "and straight on to the next card."
                )
            }
        } else {
            items(contacts.take(RECENT_COUNT), key = { it.id }) { contact ->
                // Each card fades and slides in once, so a freshly scanned card arrives visibly.
                val appear = remember(contact.id) { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = appear,
                    enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 3 }
                ) {
                    ContactCard(
                        contact = contact,
                        onClick = { onContactClick(contact) },
                        onSend = { onSendOne(contact) },
                        onDelete = { onDelete(contact) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private const val RECENT_COUNT = 5

/**
 * Counts roll up from zero the first time the dashboard is drawn, and animate again whenever a
 * card is scanned or sent, so the numbers show that something happened.
 */
@Composable
private fun animatedCount(target: Int): Int {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateIntAsState(
        targetValue = if (started) target else 0,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "count"
    )
    return value
}

@Composable
private fun HomeHeader(contacts: List<Contact>, eventName: String, senderName: String, onOpenSearch: () -> Unit) {
    val gradient = AppTheme.colors.headerGradient
    // Three slow loops: the gradient drifts, a soft light sweeps across it and the logo breathes.
    // Nothing moves under the text, so the header stays readable while it feels alive.
    val motion = rememberInfiniteTransition(label = "header")
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val sweep by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, delayMillis = 800, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(gradient.first(), gradient.last(), gradient.first()),
                    start = Offset(0f, 0f),
                    end = Offset(500f + 1300f * drift, 1500f)
                )
            )
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationX = sweep * (size.width + 520f) - 260f }
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_splash_logo),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ExpoCopilot",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        if (senderName.isNotBlank()) {
                            "Welcome back, ${senderName.substringBefore(' ')}"
                        } else {
                            "Scan cards. Follow up faster."
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search cards", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                eventName.ifBlank { "Set your expo name in Settings" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeaderStat(Icons.Filled.Badge, contacts.size, "Scanned", Modifier.weight(1f))
                HeaderStat(Icons.Filled.Email, contacts.count { it.emailSent }, "Emails", Modifier.weight(1f))
                HeaderStat(Icons.Filled.Forum, contacts.count { it.whatsappSent }, "WhatsApp", Modifier.weight(1f))
                HeaderStat(
                    Icons.Filled.AutoAwesome,
                    contacts.count { it.ocrStatus == OcrStatus.PROCESSING || it.aiStatus == AiStatus.CHECKING },
                    "Processing",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeaderStat(icon: ImageVector, value: Int, label: String, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.9f))
            Spacer(modifier = Modifier.height(4.dp))
            Text("${animatedCount(value)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    scanning: Boolean = false
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.55f),
            disabledContentColor = content.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            if (scanning) {
                // A scanner line crossing the tile, the same idea as the frame in the camera.
                val scan = rememberInfiniteTransition(label = "scan")
                val line by scan.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Reverse),
                    label = "line"
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { translationY = (line - 0.5f) * size.height }
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent)
                            )
                        )
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                        .background(LocalContentColor.current.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusTile(filter: CardFilter, count: Int, onOpenCards: (CardFilter) -> Unit, modifier: Modifier) {
    val tone = filter.tone(AppTheme.colors)
    Card(
        onClick = { onOpenCards(filter) },
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(filter.icon, tone, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "${animatedCount(count)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tone.content
                )
                Text(filter.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
