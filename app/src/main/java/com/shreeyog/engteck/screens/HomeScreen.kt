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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreeyog.engteck.ui.theme.Gold
import com.shreeyog.engteck.ui.theme.InkSoft
import com.shreeyog.engteck.ui.theme.NavyDeep
import com.shreeyog.engteck.ui.theme.Paper

data class BottomTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    BottomTab("Home", Icons.Filled.Home),
    BottomTab("Study", Icons.Filled.MenuBook),
    BottomTab("Live", Icons.Filled.VideoCall),
    BottomTab("Quiz", Icons.Filled.Quiz),
    BottomTab("Profile", Icons.Filled.Person)
)

@Composable
fun HomeScreen(teacherName: String) {
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
            when (selectedTab) {
                0 -> {
                    var selectedBook by remember { mutableStateOf<Pair<String, String>?>(null) }
                    if (selectedBook != null) {
                        MiniBookReaderScreen(
                            bookKey = selectedBook!!.first,
                            title = selectedBook!!.second,
                            onBack = { selectedBook = null }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column {
                                CoverScreen()
                                TeacherProfileCard()
                                DemoVideoCard()
                                SelectedAspirantsCard()
                                AreasCoveredCard()
                                PricingCard()
                                SyllabusPdfCard()
                                InquiryFormCard()
                                MiniBooksScreen(onBookClick = { key, title -> selectedBook = key to title })
                                RegistrationFormCard()
                                QuestionPapersCard()
                                OfficialCutoffsCard()
                                ExpectedCutoffsCard()
                                LiveClassJoinCard()
                                VideoLibraryCard()
                                ClassRecordingsCard()
                                PaidPdfLibraryCard()
                                AiTutorCard()
                                HelpDeskCard()
                            }
                        }
                    }
                }
                1 -> Box(Modifier.fillMaxSize().padding(20.dp)) {
                    StudyTabRoot()
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

@Composable
private fun PlaceholderTab(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label — coming in the next build phase", color = InkSoft, fontSize = 14.sp)
    }
}
