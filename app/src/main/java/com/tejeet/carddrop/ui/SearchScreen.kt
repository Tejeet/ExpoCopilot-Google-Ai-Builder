package com.tejeet.carddrop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.model.LeadPriority
import com.tejeet.carddrop.ui.components.ContactCard
import com.tejeet.carddrop.ui.components.EmptyState
import com.tejeet.carddrop.ui.components.SearchField
import com.tejeet.carddrop.ui.components.TabTopBar
import com.tejeet.carddrop.ui.components.priorityIcon
import com.tejeet.carddrop.ui.components.priorityTone
import com.tejeet.carddrop.ui.theme.AppTheme

/** Search tab: find any card by name, company, email, phone, expo or anything in the notes, optionally by lead priority. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onSendOne: (Contact) -> Unit,
    onDelete: (Contact) -> Unit
) {
    val colors = AppTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf<LeadPriority?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val active = query.isNotBlank() || priority != null
    val results = if (active) {
        contacts.filter { it.matches(query) && (priority == null || it.priority == priority) }
    } else {
        emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabTopBar(title = "Search", subtitle = "Find any card you've scanned")

        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Name, company, email, phone, notes...",
                    focusRequester = focusRequester
                )
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LeadPriority.values().filter { it != LeadPriority.NONE }.forEach { p ->
                        val tone = priorityTone(p, colors)
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = if (priority == p) null else p },
                            label = { Text("${p.label} leads") },
                            leadingIcon = priorityIcon(p)?.let { icon ->
                                { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                iconColor = tone.accent,
                                selectedContainerColor = tone.container,
                                selectedLabelColor = tone.content,
                                selectedLeadingIconColor = tone.content
                            )
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            when {
                !active -> item {
                    EmptyState(
                        Icons.Filled.Search, colors.navy,
                        "Search your cards",
                        "Type a name, company, email, phone number, expo name or anything from your notes - " +
                            "or tap Hot, Warm or Cold to see those leads."
                    )
                }
                results.isEmpty() -> item {
                    EmptyState(
                        Icons.Filled.SearchOff, colors.neutral,
                        "No matches",
                        if (query.isNotBlank()) "No card matches \"$query\"." else "No ${priority?.label?.lowercase()} leads yet."
                    )
                }
                else -> {
                    item {
                        Text(
                            "${results.size} ${if (results.size == 1) "result" else "results"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(results, key = { it.id }) { contact ->
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
}
