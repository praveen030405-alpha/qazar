package com.qazar.pdfviewer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Phase 5: Cryptographic Redaction Confirmation Dialog.
 * Compliant with ISO 32000 permanent content excision & text absence verification.
 */
@Composable
fun RedactionDialog(
    pageIndex: Int,
    onConfirmRedaction: (classification: String, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedClassification by remember { mutableStateOf("PII") }
    var reasonText by remember { mutableStateOf("Redaction of Personally Identifiable Information") }

    val classifications = listOf(
        "PII" to "Personally Identifiable Information",
        "FINANCIAL" to "Financial / Banking Data",
        "PRIVILEGED" to "Attorney-Client Privilege",
        "SECURITY" to "National / Security Sensitive"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E22),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFFF5252))
                    )
                    Text(
                        text = "PERMANENT CONTENT EXCISION",
                        color = Color(0xFFFF8080),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Apply Cryptographic Redaction",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Selected content on Page ${pageIndex + 1} will be permanently purged from the raster, text stream, and content tree with a SHA-256 tamper-evident audit record.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Classification Code",
                    color = Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Classification selection chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    classifications.forEach { (code, label) ->
                        val isSelected = selectedClassification == code
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF323238) else Color(0xFF26262B))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF6C63FF) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedClassification = code
                                    reasonText = "Redaction of $label"
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = code,
                                        color = if (isSelected) Color(0xFF9D95FF) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = label,
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "SELECTED",
                                        color = Color(0xFF6C63FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Audit Justification", fontSize = 12.sp, color = Color(0xFFAAAAAA)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6C63FF),
                        unfocusedBorderColor = Color(0xFF444444)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onConfirmRedaction(selectedClassification, reasonText)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Assert & Expose",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
