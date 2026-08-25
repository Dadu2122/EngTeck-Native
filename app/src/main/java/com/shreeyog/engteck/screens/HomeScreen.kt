package com.shreeyog.engteck.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreeyog.engteck.ui.theme.Gold
import com.shreeyog.engteck.ui.theme.InkSoft
import com.shreeyog.engteck.ui.theme.NavyDeep
import com.shreeyog.engteck.ui.theme.Paper
import kotlinx.coroutines.launch

data class BottomTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    BottomTab("Home", Icons.Filled.Home),
    BottomTab("Study", Icons.Filled.MenuBook),
    BottomTab("Live", Icons.Filled.VideoCall),
    BottomTab("Quiz", Icons.Filled.Quiz),
    BottomTab("Profile", Icons.Filled.Person)
)

@Composable
fun HomeScreen(
    teacherName: String,
    autoJoinTeacherKey: String? = null,
    autoJoinTeacherName: String? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NavyDeep,
                            selectedTextColor = NavyDeep,
                            indicatorColor = Gold.copy(alpha = 0.25f),
                            unselectedIconColor = InkSoft,
                            unselectedTextColor = InkSoft
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!autoJoinTeacherKey.isNullOrBlank()) {
                // Opened via a tapped class-share link — go straight to that
                // teacher's join-request form instead of the normal scrolling
                // home page (no cover page, no manual scrolling/tapping needed).
                LiveClassJoinCard(
                    initialTeacherKey = autoJoinTeacherKey,
                    initialTeacherName = autoJoinTeacherName ?: "Teacher"
                )
            } else {
                when (selectedTab) {
                    0 -> {
                        var selectedBook by remember { mutableStateOf<Pair<String, String>?>(null) }
                        var showProgressAnalytics by remember { mutableStateOf(false) }
                        var openFlashcardDeck by remember { mutableStateOf<Pair<String, String>?>(null) }
                        var showFlashcardAdmin by remember { mutableStateOf(false) }
                        if (showProgressAnalytics) {
                            ProgressAnalyticsScreen(onClose = { showProgressAnalytics = false })
                        } else if (showFlashcardAdmin) {
                            FlashcardAdminScreen(onBack = { showFlashcardAdmin = false })
                        } else if (openFlashcardDeck != null) {
                            FlashcardScreen(
                                deckKey = openFlashcardDeck!!.first,
                                deckLabel = openFlashcardDeck!!.second,
                                onBack = { openFlashcardDeck = null }
                            )
                        } else if (selectedBook != null) {
                            MiniBookReaderScreen(
                                bookKey = selectedBook!!.first,
                                title = selectedBook!!.second,
                                onBack = { selectedBook = null }
                            )
                        } else {
                            val homeScrollState = rememberScrollState()
                            val homeScrollScope = rememberCoroutineScope()
                            var demoVideoY by remember { mutableStateOf(0) }
                            var registrationFormY by remember { mutableStateOf(0) }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(homeScrollState)
                            ) {
                                Column {
                                    CoverScreen(
                                        onProgressClick = { showProgressAnalytics = true },
                                        onRegisterClick = {
                                            homeScrollScope.launch { homeScrollState.animateScrollTo(registrationFormY) }
                                        },
                                        onWatchDemoClick = {
                                            homeScrollScope.launch { homeScrollState.animateScrollTo(demoVideoY) }
                                        }
                                    )
                                    TeacherProfileCard()
                                    FlashcardsCard(
                                        onOpenDeck = { key, label -> openFlashcardDeck = key to label },
                                        onManageContent = { showFlashcardAdmin = true }
                                    )
                                    Box(modifier = Modifier.onGloballyPositioned { demoVideoY = it.positionInParent().y.toInt() }) {
                                        DemoVideoCard()
                                    }
                                    SelectedAspirantsCard()
                                    AreasCoveredCard()
                                    PricingCard()
                                    SyllabusPdfCard()
                                    InquiryFormCard()
                                    MiniBooksScreen(onBookClick = { key, title -> selectedBook = key to title })
                                    Box(modifier = Modifier.onGloballyPositioned { registrationFormY = it.positionInParent().y.toInt() }) {
                                        RegistrationFormCard()
                                    }
                                    QuestionPapersCard()
                                    OfficialCutoffsCard()
                                    ExpectedCutoffsCard()
                                    LiveClassJoinCard()
                                    VideoLibraryCard()
                                    ClassRecordingsCard()
                                    PremiumPdfLibraryCard()
                                    AiTutorCard()
                                    HelpDeskCard()
                                }
                            }
                        }
                    }
                    1 -> Box(Modifier.fillMaxSize().padding(20.dp)) {
                        StudyTabRoot()
                    }
                    2 -> Box(Modifier.fillMaxSize()) {
                        // Fixes the "Live" bottom tab, which previously showed
                        // only a "coming in next build phase" placeholder even
                        // though the Live Class join flow already exists —
                        // students had to find it buried in the Home scroll.
                        LiveClassJoinCard()
                    }
                    4 -> Box(Modifier.fillMaxSize()) {
                        ProfileTabScreen()
                    }
                    else -> Box(Modifier.fillMaxSize().padding(20.dp)) {
                        PlaceholderTab(tabs[selectedTab].label)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label — coming in the next build phase", color = InkSoft, fontSize = 14.sp)
    }
}
