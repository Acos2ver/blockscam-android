package com.oyc.blockscam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oyc.blockscam.ui.theme.BlockScamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BlockScamTheme {
                BlockScamApp()
            }
        }
    }
}

@Composable
fun BlockScamApp() {
    var phoneNumber by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("Enter a phone number to check.") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Block Scam",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Check if a phone number is listed in your scam database.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone number") },
            placeholder = { Text("949-555-7788") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (phoneNumber.trim().isEmpty()) {
                    resultText = "Please enter a phone number."
                    return@Button
                }

                isLoading = true
                resultText = "Checking..."

                scope.launch {
                    resultText = checkPhoneNumber(phoneNumber)
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Checking..." else "Check Number")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = resultText,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

suspend fun checkPhoneNumber(phoneNumber: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val encodedNumber = URLEncoder.encode(phoneNumber.trim(), "UTF-8")
            val url = URL("http://10.0.2.2:3000/api/check-number/$encodedNumber")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val statusCode = connection.responseCode

            val responseText = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "Server returned error code $statusCode"
            }

            connection.disconnect()

            val json = JSONObject(responseText)
            val found = json.getBoolean("found")

            if (found) {
                val blockedNumber = json.getJSONObject("blockedNumber")
                val label = blockedNumber.optString("label", "No label")
                val risk = blockedNumber.optString("manualRisk", "Auto")
                val notes = blockedNumber.optString("notes", "No notes")

                "⚠️ Scam number found\n\nRisk: $risk\nLabel: $label\nNotes: $notes"
            } else {
                "✅ No match found\n\nThis number is not currently in your scam blocklist."
            }

        } catch (e: Exception) {
            "Could not connect to the Block Scam API.\n\nCheck that your Node server is running on port 3000."
        }
    }
}