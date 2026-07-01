package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ContactAdminView(
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Contact Admin",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            placeholder = { Text("Type your message to the admin here...") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (message.isNotBlank()) {
                    onSendMessage(message)
                    Toast.makeText(context, "Message sent to admin!", Toast.LENGTH_SHORT).show()
                    message = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = message.isNotBlank()
        ) {
            Text("Send Message")
        }
    }
}
