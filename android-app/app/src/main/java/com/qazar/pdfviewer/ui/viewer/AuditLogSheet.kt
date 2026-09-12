package com.qazar.pdfviewer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.bridge.AuditLogEntry

/**
 * Phase 5: Cryptographic SHA-256 Hash-Chained Audit Log Bottom Sheet.
 * Displays real-time forensic proof and tamper-evidence status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogSheet(
    auditEntries: List<AuditLogEntry>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF44444A))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF00E676))
                        )
                        Text(
                            text = "FORENSIC INTEGRITY AUDIT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676),
                            letterSpacing = 1.2.sp
                        )
                    }
                    Text(
                        text = "SHA-256 Hash Chain",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFAAAAAA))
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Each action is sealed with a cryptographic hash linked to the previous entry, establishing indisputable non-repudiation.",
                fontSize = 12.sp,
                color = Color(0xFF888888),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (auditEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No cryptographic actions recorded yet.\nPerform a redaction or apply a signature to initialize the ledger.",
                        color = Color(0xFF666666),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(auditEntries) { entry ->
                        AuditEntryCard(entry)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AuditLogEntry) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222227))
            .border(1.dp, Color(0xFF33333C), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    entry.action.contains("REDACT", ignoreCase = true) -> Color(0xFFD32F2F)
                                    entry.action.contains("SIGN", ignoreCase = true) -> Color(0xFF00C853)
                                    else -> Color(0xFF6C63FF)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.action,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "#${entry.index}",
                        fontSize = 12.sp,
                        color = Color(0xFF888888),
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = entry.timestamp,
                    fontSize = 11.sp,
                    color = Color(0xFF888888)
                )
            }

            Text(
                text = entry.details,
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Divider(color = Color(0xFF2E2E35), thickness = 0.5.dp)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "PREV: ${entry.prevHash.take(16)}...",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF777777)
                )
                Text(
                    text = "HASH: ${entry.entryHash.take(24)}...",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
