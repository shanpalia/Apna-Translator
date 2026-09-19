package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenTranslator: () -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val history by viewModel.historyItems.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFE7FBF7)),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "BREAK LANGUAGE BARRIERS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MintPrimaryDark
                )
                Text(
                    text = "Translate. Speak. Connect.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 27.sp
                    ),
                    color = Color(0xFF12343B),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Offline. Private. Always with You.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeroBadge("✓", "100% Offline")
                    HeroBadge("⚡", "Fast")
                    HeroBadge("🔒", "Private")
                }
            }
        }

        SectionHeader("Quick Actions", Icons.Default.Translate)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction("Text Translate", "Type & Translate", Icons.Default.Translate, MintPrimaryDark, Color(0xFFE8FBF8), onOpenTranslator, Modifier.weight(1f))
            QuickAction("Voice Translate", "Speak & Translate", Icons.Default.Mic, OceanBlue, Color(0xFFEAF6FF), onOpenTranslator, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction("Camera Translate", "Scan & Translate", Icons.Default.CameraAlt, Color(0xFFD94F70), Color(0xFFFFEEF3), onOpenTranslator, Modifier.weight(1f))
            QuickAction("Conversation Mode", "Two-way Translation", Icons.Default.Chat, Color(0xFFD88A00), Color(0xFFFFF6E5), onOpenConversation, Modifier.weight(1f))
        }

        SectionHeader("Recent Translations", Icons.Default.History)

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            if (history.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    Text("No translations yet", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "Your real translation history will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            } else {
                history.take(3).forEachIndexed { index, item ->
                    RecentTranslationRow(
                        text = item.sourceText,
                        languages = "\${item.sourceLangName} → \${item.targetLangName}",
                        time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                    )
                    if (index < minOf(2, history.lastIndex)) {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE4F8F5)) {
            Icon(icon, null, tint = MintPrimaryDark, modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HeroBadge(symbol: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = MintPrimaryDark, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = MintPrimaryDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = background,
        tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Icon(Icons.Default.ArrowForward, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RecentTranslationRow(text: String, languages: String, time: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE6F7F4)) {
            Icon(Icons.Default.Translate, null, tint = SuccessGreen, modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(languages, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}
