package com.tejeet.carddrop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tejeet.carddrop.data.SettingsStore
import com.tejeet.carddrop.model.Contact
import com.tejeet.carddrop.ui.theme.AppTheme
import com.tejeet.carddrop.ui.theme.BrandTeal
import com.tejeet.carddrop.ui.theme.BrandTealDark
import com.tejeet.carddrop.ui.theme.OnBrandTeal

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CARDS("Cards", Icons.Filled.Style),
    SEARCH("Search", Icons.Filled.Search),
    SETTINGS("Settings", Icons.Filled.Settings)
}

/** The app's main shell: bottom navigation between Home, Cards, Search and Settings. */
@Composable
fun MainScreen(
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    cardsFilter: CardFilter,
    onCardsFilterChange: (CardFilter) -> Unit,
    contacts: List<Contact>,
    settingsStore: SettingsStore,
    errorMessage: String?,
    infoMessage: String?,
    dismissError: () -> Unit,
    dismissInfo: () -> Unit,
    onScanClick: () -> Unit,
    onContactClick: (Contact) -> Unit,
    onSendAll: () -> Unit,
    onSyncAll: () -> Unit,
    onSendOne: (Contact) -> Unit,
    onDelete: (Contact) -> Unit,
    onSettingsSaved: () -> Unit
) {
    val colors = AppTheme.colors
    val toReviewCount = contacts.count { CardFilter.TO_REVIEW.matches(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // The nav bar sits 22dp down inside this Box so the round Scan button can rise above it
            // and still receive taps over its whole area.
            Box(modifier = Modifier.fillMaxWidth()) {
                NavigationBar(
                    modifier = Modifier.padding(top = 22.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    MainTab.values().forEachIndexed { index, t ->
                        if (index == MainTab.values().size / 2) {
                            // Centre slot: room for the Scan button, with its label lined up with the tabs'.
                            // Fixed height, not fillMaxHeight: NavigationBar only sets a *minimum* height,
                            // so filling max height would stretch the whole bar to the size of the screen.
                            Box(
                                modifier = Modifier.weight(1f).height(80.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    "Scan",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.brand.content,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                            }
                        }
                        val selected = t == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onTabChange(t) },
                            icon = {
                                if (t == MainTab.CARDS && toReviewCount > 0) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = colors.review.accent, contentColor = Color.White) {
                                            Text(if (toReviewCount > 99) "99+" else "$toReviewCount")
                                        }
                                    }) {
                                        Icon(t.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(t.icon, contentDescription = null)
                                }
                            },
                            label = { Text(t.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.brand.content,
                                selectedTextColor = colors.navy.content,
                                indicatorColor = colors.brand.container,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                ScanNavButton(onClick = onScanClick, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    contacts = contacts,
                    eventName = settingsStore.eventName,
                    senderName = settingsStore.senderName,
                    errorMessage = errorMessage,
                    infoMessage = infoMessage,
                    dismissError = dismissError,
                    dismissInfo = dismissInfo,
                    onScanClick = onScanClick,
                    onSendAll = onSendAll,
                    onOpenCards = { filter ->
                        onCardsFilterChange(filter)
                        onTabChange(MainTab.CARDS)
                    },
                    onOpenSearch = { onTabChange(MainTab.SEARCH) },
                    onContactClick = onContactClick,
                    onSendOne = onSendOne,
                    onDelete = onDelete
                )

                MainTab.CARDS -> CardsScreen(
                    contacts = contacts,
                    filter = cardsFilter,
                    onFilterChange = onCardsFilterChange,
                    onSyncAll = onSyncAll,
                    errorMessage = errorMessage,
                    infoMessage = infoMessage,
                    dismissError = dismissError,
                    dismissInfo = dismissInfo,
                    onContactClick = onContactClick,
                    onSendAll = onSendAll,
                    onSendOne = onSendOne,
                    onDelete = onDelete
                )

                MainTab.SEARCH -> SearchScreen(
                    contacts = contacts,
                    onContactClick = onContactClick,
                    onSendOne = onSendOne,
                    onDelete = onDelete
                )

                MainTab.SETTINGS -> SettingsScreen(
                    settingsStore = settingsStore,
                    onSaved = onSettingsSaved
                )
            }
        }
    }
}

/** Raised round Scan button in the middle of the bottom bar, reachable from every tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanNavButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(66.dp)
            .semantics { contentDescription = "Scan card" },
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 10.dp,
        // A ring in the bar's colour makes the button look cut into the bar.
        border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(BrandTeal, BrandTealDark))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.DocumentScanner,
                contentDescription = null,
                tint = OnBrandTeal,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
