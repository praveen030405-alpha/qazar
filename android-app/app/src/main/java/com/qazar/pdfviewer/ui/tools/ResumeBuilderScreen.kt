package com.qazar.pdfviewer.ui.tools

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.data.tools.ResumePdfGenerator
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.GoogleSansTextFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resume Maker Screen: Build Professional ATS-Friendly Resumes with Inbuilt Templates
 */
@Composable
fun ResumeBuilderScreen(
    onBack: () -> Unit,
    onPdfGenerated: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedTemplate by remember { mutableStateOf(ResumePdfGenerator.ResumeTemplate.MODERN_EXECUTIVE) }

    // Form fields
    var fullName by remember { mutableStateOf("Alex Morgan") }
    var jobTitle by remember { mutableStateOf("Principal Software Architect") }
    var email by remember { mutableStateOf("alex.morgan@enterprise.io") }
    var phone by remember { mutableStateOf("+1 (555) 234-5678") }
    var location by remember { mutableStateOf("San Francisco, CA") }
    var summary by remember {
        mutableStateOf("High-impact systems architect specializing in high-throughput native engines, distributed synchronization, and low-latency mobile graphics frameworks.")
    }

    // Work Experience
    var expRole by remember { mutableStateOf("Lead Systems Engineer") }
    var expCompany by remember { mutableStateOf("Quantum Micro-Systems Inc.") }
    var expDuration by remember { mutableStateOf("2021 â€” Present") }
    var expDetails by remember {
        mutableStateOf("Architected 120 FPS hardware rendering engine, cutting frame drop rates by 84%. Engineered CRDT offline data pipeline processing 4M real-time mutation events.")
    }

    // Education
    var eduDegree by remember { mutableStateOf("B.S. in Computer Science & Engineering") }
    var eduInstitution by remember { mutableStateOf("University of California, Berkeley") }
    var eduYear by remember { mutableStateOf("2017 â€” 2021") }

    // Skills
    var skillsInput by remember {
        mutableStateOf("Rust, Kotlin, Android NDK, High-Performance GPU Shaders, Jetpack Compose, System Architecture, CRDT")
    }

    var isGenerating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C12)) // Deep Dark Obsidian
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color(0x20EF4444),
                    border = BorderStroke(0.8.dp, Color(0x60EF4444)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Resume Builder",
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Inbuilt ATS-grade vector PDF templates",
                        fontFamily = GoogleSansTextFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Quick prefill sample button
            Surface(
                onClick = {
                    fullName = "Elena Rostova"
                    jobTitle = "Senior Product Designer & UI Architect"
                    email = "elena.design@meridian.org"
                    phone = "+1 (415) 890-1234"
                    location = "New York, NY"
                    summary = "Award-winning design architect crafting sensory-rich digital interactions, tactile design systems, and enterprise accessibility workflows."
                    expRole = "Head of Interaction Design"
                    expCompany = "Vanguard Digital Lab"
                    expDuration = "2020 â€” Present"
                    expDetails = "Designed multi-platform design token system adopted across 14 enterprise suites. Championed fluid 120 FPS micro-animations enhancing user engagement by 42%."
                    eduDegree = "Master of Fine Arts in Interaction Design"
                    eduInstitution = "Rhode Island School of Design"
                    eduYear = "2016 â€” 2020"
                    skillsInput = "Design Systems, Micro-animations, Figma, Spatial UI, Swift, Compose, Typography"
                },
                shape = RoundedCornerShape(12.dp),
                color = Color(0x1CDC2626),
                border = BorderStroke(0.8.dp, Color(0x60EF4444))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFF3B56), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sample Data", fontSize = 11.sp, fontFamily = GoogleSansFamily, color = Color.White)
                }
            }
        }

        // Main Form Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Template Selector Cards
            Text(
                text = "CHOOSE TEMPLATE",
                fontSize = 11.sp,
                fontFamily = GoogleSansFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444),
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResumePdfGenerator.ResumeTemplate.entries.forEach { tmpl ->
                    val isSelected = (selectedTemplate == tmpl)
                    Surface(
                        onClick = { selectedTemplate = tmpl },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0x28DC2626) else Color(0xFF140E16),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFF3B56) else Color(0x30FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tmpl.name.replace("_", "\n"),
                                fontSize = 10.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Personal Info Section
            ResumeSectionCard(title = "PERSONAL DETAILS") {
                ResumeTextField(label = "Full Name", value = fullName, onValueChange = { fullName = it })
                ResumeTextField(label = "Job Title", value = jobTitle, onValueChange = { jobTitle = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ResumeTextField(label = "Email", value = email, onValueChange = { email = it })
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ResumeTextField(label = "Phone", value = phone, onValueChange = { phone = it })
                    }
                }
                ResumeTextField(label = "Location", value = location, onValueChange = { location = it })
                ResumeTextField(label = "Executive Summary", value = summary, onValueChange = { summary = it }, singleLine = false)
            }

            // Experience Section
            ResumeSectionCard(title = "PRIMARY EXPERIENCE") {
                ResumeTextField(label = "Role / Designation", value = expRole, onValueChange = { expRole = it })
                ResumeTextField(label = "Company / Organization", value = expCompany, onValueChange = { expCompany = it })
                ResumeTextField(label = "Duration (e.g. 2021 â€” Present)", value = expDuration, onValueChange = { expDuration = it })
                ResumeTextField(label = "Key Achievements & Details", value = expDetails, onValueChange = { expDetails = it }, singleLine = false)
            }

            // Education Section
            ResumeSectionCard(title = "EDUCATION") {
                ResumeTextField(label = "Degree / Certification", value = eduDegree, onValueChange = { eduDegree = it })
                ResumeTextField(label = "Institution / University", value = eduInstitution, onValueChange = { eduInstitution = it })
                ResumeTextField(label = "Graduation Year", value = eduYear, onValueChange = { eduYear = it })
            }

            // Skills Section
            ResumeSectionCard(title = "CORE SKILLS") {
                ResumeTextField(
                    label = "Skills (comma separated)",
                    value = skillsInput,
                    onValueChange = { skillsInput = it },
                    singleLine = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom Action Bar: Generate Resume PDF Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                onClick = {
                    if (!isGenerating && fullName.isNotBlank()) {
                        isGenerating = true
                        scope.launch(Dispatchers.IO) {
                            val profile = ResumePdfGenerator.ResumeProfile(
                                fullName = fullName,
                                title = jobTitle,
                                email = email,
                                phone = phone,
                                location = location,
                                summary = summary,
                                experiences = listOf(
                                    ResumePdfGenerator.ExperienceItem(expRole, expCompany, expDuration, expDetails)
                                ),
                                educations = listOf(
                                    ResumePdfGenerator.EducationItem(eduDegree, eduInstitution, eduYear)
                                ),
                                skills = skillsInput.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                template = selectedTemplate
                            )

                            val res = ResumePdfGenerator.generatePdf(context, profile)
                            withContext(Dispatchers.Main) {
                                isGenerating = false
                                res.onSuccess { pdfFile ->
                                    onPdfGenerated(pdfFile)
                                }
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFDC2626),
                border = BorderStroke(1.2.dp, Color(0xFFFF5252)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Generate Resume PDF",
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeSectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF140D14),
        border = BorderStroke(0.8.dp, Color(0x35EF4444)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontFamily = GoogleSansFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444),
                letterSpacing = 1.sp
            )
            content()
        }
    }
}

@Composable
private fun ResumeTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8)) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color(0xFFE2E8F0),
            focusedBorderColor = Color(0xFFEF4444),
            unfocusedBorderColor = Color(0x30FFFFFF),
            focusedContainerColor = Color(0xFF0E0E16),
            unfocusedContainerColor = Color(0xFF0E0E16)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
