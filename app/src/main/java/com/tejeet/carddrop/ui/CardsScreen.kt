package com.tejeet.carddrop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.SendStatus
import com.tejeet.carddrop.ui.components.ContactCard
import com.tejeet.carddrop.ui.components.EmptyState
import com.tejeet.carddrop.ui.components.MessageBanner
import com.tejeet.carddrop.ui.components.SearchField
import com.tejeet.carddrop.ui.components.SendAllButton
import com.tejeet.carddrop.ui.components.TabTopBar
import com.tejeet.carddrop.ui.theme.AppTheme
import com.tejeet.carddrop.ui.theme.ExtendedColors
import com.tejeet.carddrop.ui.theme.Tone

/** Status filters on the Cards tab (also opened from the Home overview tiles). */
enum class CardFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Filled.ViewAgenda),
    TO_REVIEW("To review", Icons.Filled.RateReview),
    PENDING("Pending", Icons.Filled.Schedule),
    SENT("Sent", Icons.Filled.DoneAll),
    FAILED("Failed", Icons.Filled.ErrorOutline);

    fun matches(c: Contact): Boolean = when (this) {
        ALL -> true
        TO_REVIEW -> !c.reviewed || (c.status != SendStatus.SENT && c.missingRequired().isNotEmpty())
        PENDING -> c.reviewed && c.missingRequired().isEmpty() &&
            (c.status == SendStatus.PENDING || c.status == SendStatus.SENDING)
        SENT -> c.status == SendStatus.SENT
        FAILED -> c.reviewed && c.status == SendStatus.FAILED
    }

    fun tone(colors: ExtendedColors): Tone = when (this) {
        ALL -> colors.navy
        TO_REVIEW -> colors.review
        PENDING -> colors.pending
        SENT -> colors.sent
        FAILED -> colors.failed
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardsScreen(
    contacts: List<Contact>,
    filter: CardFilter,
    onFilterChange: (CardFilter) -> Unit,
    errorMessage: String?,
    infoMessage: String?,
    dismissError: () -> Unit,
    dismissInfo: () -> Unit,
    onContactClick: (Contact) -> Unit,
    onSendAll: () -> Unit,
    onSyncAll: () -> Unit,
    onSendOne: (Contact) -> Unit,
    onDelete: (Contact) -> Unit
) {
    val colors = AppTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    // Recomputed only when the cards, the search text or the filter change, not on every recomposition.
    val matching by remember(contacts, query) { derivedStateOf { contacts.filter { it.matches(query) } } }
    val visible by remember(filter) { derivedStateOf { matching.filter { filter.matches(it) } } }
    val sendableCount by remember(contacts) { derivedStateOf { contacts.count { it.isReadyToSend } } }

    Column(modifier = Modifier.fillMaxSize()) {
        val notSynced = contacts.count { it.syncedAt == 0L }
        TabTopBar(
            title = "Cards",
            subtitle = if (notSynced > 0) "${contacts.size} scanned · $notSynced not uploaded" else "${contacts.size} scanned",
            actions = {
                IconButton(onClick = onSyncAll, enabled = contacts.isNotEmpty()) {
                    Icon(Icons.Filled.CloudSync, contentDescription = "Sync all cards to the server")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Search name, company, email, notes...",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(CardFilter.values().toList()) { f ->
                                FilterTabChip(
                                    filter = f,
                                    count = matching.count { f.matches(it) },
                                    selected = f == filter,
                                    onClick = { onFilterChange(f) }
                                )
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    MessageBanner(
                        errorMessage, colors.failed, Icons.Filled.ErrorOutline, dismissError,
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }
            if (infoMessage != null) {
                item {
                    MessageBanner(
                        infoMessage, colors.pending, Icons.Filled.Info, dismissInfo,
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }

            if (sendableCount > 0 && filter in setOf(CardFilter.ALL, CardFilter.PENDING, CardFilter.FAILED)) {
                item {
                    SendAllButton(sendableCount, onSendAll, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                }
            }

            if (visible.isEmpty()) {
                item {
                    when {
                        query.isNotBlank() -> EmptyState(
                            Icons.Filled.SearchOff, colors.neutral,
                            "No matches",
                            "Nothing in \"${filter.label}\" matches \"$query\"."
                        )
                        contacts.isEmpty() -> EmptyState(
                            Icons.Filled.Style, colors.brand,
                            "No cards yet",
                            "Tap \"Scan card\" and line the card up inside the frame."
                        )
                        else -> EmptyState(
                            filter.icon, filter.tone(colors),
                            "Nothing here",
                            when (filter) {
                                CardFilter.TO_REVIEW -> "Every scanned card has been checked."
                                CardFilter.PENDING -> "Reviewed cards waiting to be sent show up here."
                                CardFilter.SENT -> "Cards show up here once the backend confirms every message."
                                CardFilter.FAILED -> "Cards the backend couldn't send show up here, ready to retry."
                                CardFilter.ALL -> ""
                            }
                        )
                    }
                }
            } else {
                items(visible, key = { it.id }) { contact ->
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

@Composable
private fun FilterTabChip(filter: CardFilter, count: Int, selected: Boolean, onClick: () -> Unit) {
    val tone = filter.tone(AppTheme.colors)
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        leadingIcon = if (selected) {
            { Icon(filter.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(filter.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) tone.accent else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = tone.container,
            selectedLabelColor = tone.content,
            selectedLeadingIconColor = tone.content
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = tone.accent.copy(alpha = 0.5f)
        )
    )
}
