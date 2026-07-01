package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.InboxMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxView(
    messages: List<InboxMessage>,
    onMarkAsRead: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Admin Inbox",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (messages.isEmpty()) {
            Text("No messages in your inbox.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                messages.forEach { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "From: ${msg.senderName}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
                            
                            if (!msg.isRead) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { onMarkAsRead(msg.id) }) {
                                    Text("Mark as Read")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
