package com.shreeyog.engteck.screens

import androidx.compose.foundation.layout.*
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
                .padding(20.dp)
        ) {
            when (selectedTab) {
                0 -> CoverScreen()
                1 -> StudyTabRoot()
                else -> PlaceholderTab(tabs[selectedTab].label)
            }
        }
    }
}

@Composable
private fun HomeTabContent(teacherName: String) {
    Column {
        Text("Welcome,", fontSize = 14.sp, color = InkSoft)
        Text(teacherName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyDeep),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("TGT / PGT / LT Grade / GIC", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aapki coaching journey yahan se shuru hoti hai — Study Material, Live Class, Quiz sab ek jagah.",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 13.sp
                )
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
