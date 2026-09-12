package com.qazar.pdfviewer.data.tools

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.util.Log
import com.qazar.pdfviewer.data.QazarStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Enterprise-Grade Inbuilt Resume PDF Generator
 * Renders 4 distinct industry-standard resume designs natively to PDF.
 */
object ResumePdfGenerator {

    private const val TAG = "ResumePdfGenerator"

    enum class ResumeTemplate {
        MODERN_EXECUTIVE, // Bold crimson header band, dual-column structured layout
        TECH_MINIMALIST,  // Clean, modern typography with tag chips and clean lines
        ACADEMIC_CLASSIC, // Traditional serif layout with balanced margins
        CREATIVE_PORTFOLIO // Vibrant sidebar rail with skills & projects showcase
    }

    data class ExperienceItem(
        val role: String,
        val company: String,
        val duration: String,
        val details: String
    )

    data class EducationItem(
        val degree: String,
        val institution: String,
        val year: String
    )

    data class ResumeProfile(
        val fullName: String,
        val title: String,
        val email: String,
        val phone: String,
        val location: String,
        val summary: String,
        val experiences: List<ExperienceItem>,
        val educations: List<EducationItem>,
        val skills: List<String>,
        val template: ResumeTemplate = ResumeTemplate.MODERN_EXECUTIVE
    )

    suspend fun generatePdf(
        context: Context,
        profile: ResumeProfile
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val baseName = profile.fullName.replace(" ", "_").ifBlank { "Professional" }
            val docsDir = QazarStorageManager.getDocumentsDir(context)
            val outFile = File(docsDir, "${baseName}_Resume.pdf")

            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 points
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            when (profile.template) {
                ResumeTemplate.MODERN_EXECUTIVE -> drawModernExecutive(canvas, profile)
                ResumeTemplate.TECH_MINIMALIST -> drawTechMinimalist(canvas, profile)
                ResumeTemplate.ACADEMIC_CLASSIC -> drawAcademicClassic(canvas, profile)
                ResumeTemplate.CREATIVE_PORTFOLIO -> drawCreativePortfolio(canvas, profile)
            }

            doc.finishPage(page)
            FileOutputStream(outFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()

            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf("application/pdf"),
                null
            )

            Log.i(TAG, "Resume PDF successfully generated: ${outFile.absolutePath}")
            Result.success(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Resume PDF", e)
            Result.failure(e)
        }
    }

    private fun drawModernExecutive(canvas: android.graphics.Canvas, p: ResumeProfile) {
        // Page background
        canvas.drawColor(Color.WHITE)

        // Dark obsidian top banner
        val bannerPaint = Paint().apply { color = Color.parseColor("#140D12") }
        canvas.drawRect(0f, 0f, 595f, 105f, bannerPaint)

        // Crimson accent stripe
        val stripePaint = Paint().apply { color = Color.parseColor("#DC2626") }
        canvas.drawRect(0f, 105f, 595f, 109f, stripePaint)

        // Name
        val namePaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(p.fullName.uppercase(), 40f, 48f, namePaint)

        // Title
        val titlePaint = Paint().apply {
            color = Color.parseColor("#EF4444")
            textSize = 12f
            isAntiAlias = true
        }
        canvas.drawText(p.title, 40f, 70f, titlePaint)

        // Contact info in banner
        val contactPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 9f
            isAntiAlias = true
        }
        val contactLine = listOfNotNull(
            p.email.ifBlank { null },
            p.phone.ifBlank { null },
            p.location.ifBlank { null }
        ).joinToString("  |  ")
        canvas.drawText(contactLine, 40f, 88f, contactPaint)

        var curY = 135f

        // Summary Section
        if (p.summary.isNotBlank()) {
            curY = drawSectionHeader(canvas, "EXECUTIVE SUMMARY", 40f, curY, 515f)
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#334155")
                textSize = 10f
                isAntiAlias = true
            }
            curY = drawWrappedText(canvas, p.summary, 40f, curY + 14f, 515f, bodyPaint)
            curY += 16f
        }

        // Experience Section
        if (p.experiences.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "PROFESSIONAL EXPERIENCE", 40f, curY, 515f)
            curY += 16f

            for (exp in p.experiences) {
                // Role & Duration
                val rolePaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    textSize = 11f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas.drawText(exp.role, 40f, curY, rolePaint)

                val durPaint = Paint().apply {
                    color = Color.parseColor("#64748B")
                    textSize = 9.5f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText(exp.duration, 555f, curY, durPaint)
                curY += 13f

                // Company
                val compPaint = Paint().apply {
                    color = Color.parseColor("#DC2626")
                    textSize = 10f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas.drawText(exp.company, 40f, curY, compPaint)
                curY += 13f

                // Details
                if (exp.details.isNotBlank()) {
                    val detPaint = Paint().apply {
                        color = Color.parseColor("#475569")
                        textSize = 9f
                        isAntiAlias = true
                    }
                    curY = drawWrappedText(canvas, exp.details, 48f, curY, 507f, detPaint)
                }
                curY += 14f
            }
        }

        // Skills Section
        if (p.skills.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "CORE COMPETENCIES", 40f, curY, 515f)
            curY += 16f

            val skillPaint = Paint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 9.5f
                isAntiAlias = true
            }
            val skillsStr = p.skills.joinToString("   â€¢   ")
            curY = drawWrappedText(canvas, skillsStr, 40f, curY, 515f, skillPaint)
            curY += 18f
        }

        // Education Section
        if (p.educations.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "EDUCATION", 40f, curY, 515f)
            curY += 16f

            for (edu in p.educations) {
                val degPaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    textSize = 10.5f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas.drawText(edu.degree, 40f, curY, degPaint)

                val yrPaint = Paint().apply {
                    color = Color.parseColor("#64748B")
                    textSize = 9f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText(edu.year, 555f, curY, yrPaint)
                curY += 12f

                val instPaint = Paint().apply {
                    color = Color.parseColor("#475569")
                    textSize = 9.5f
                    isAntiAlias = true
                }
                canvas.drawText(edu.institution, 40f, curY, instPaint)
                curY += 16f
            }
        }
    }

    private fun drawTechMinimalist(canvas: android.graphics.Canvas, p: ResumeProfile) {
        canvas.drawColor(Color.WHITE)

        val namePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(p.fullName, 44f, 60f, namePaint)

        val titlePaint = Paint().apply {
            color = Color.parseColor("#2563EB")
            textSize = 11.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(p.title.uppercase(), 44f, 78f, titlePaint)

        val contactPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 9f
            isAntiAlias = true
        }
        val contact = "${p.email}   |   ${p.phone}   |   ${p.location}"
        canvas.drawText(contact, 44f, 95f, contactPaint)

        val divPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        canvas.drawLine(44f, 108f, 551f, 108f, divPaint)

        var curY = 130f
        if (p.summary.isNotBlank()) {
            curY = drawSectionHeader(canvas, "SUMMARY", 44f, curY, 507f)
            val bp = Paint().apply { color = Color.parseColor("#334155"); textSize = 9.5f; isAntiAlias = true }
            curY = drawWrappedText(canvas, p.summary, 44f, curY + 14f, 507f, bp) + 14f
        }

        if (p.experiences.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "EXPERIENCE", 44f, curY, 507f) + 14f
            for (exp in p.experiences) {
                val rp = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawText("${exp.role} @ ${exp.company}", 44f, curY, rp)
                val dp = Paint().apply { color = Color.parseColor("#64748B"); textSize = 9f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
                canvas.drawText(exp.duration, 551f, curY, dp)
                curY += 14f
                if (exp.details.isNotBlank()) {
                    val detp = Paint().apply { color = Color.parseColor("#475569"); textSize = 8.8f; isAntiAlias = true }
                    curY = drawWrappedText(canvas, exp.details, 52f, curY, 499f, detp)
                }
                curY += 12f
            }
        }

        if (p.skills.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "SKILLS", 44f, curY, 507f) + 14f
            val sp = Paint().apply { color = Color.parseColor("#1E293B"); textSize = 9f; isAntiAlias = true }
            curY = drawWrappedText(canvas, p.skills.joinToString("   â€¢   "), 44f, curY, 507f, sp) + 14f
        }

        if (p.educations.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "EDUCATION", 44f, curY, 507f) + 14f
            for (edu in p.educations) {
                val ep = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawText("${edu.degree} - ${edu.institution}", 44f, curY, ep)
                val yp = Paint().apply { color = Color.parseColor("#64748B"); textSize = 9f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
                canvas.drawText(edu.year, 551f, curY, yp)
                curY += 16f
            }
        }
    }

    private fun drawAcademicClassic(canvas: android.graphics.Canvas, p: ResumeProfile) {
        canvas.drawColor(Color.WHITE)
        val namePaint = Paint().apply { color = Color.parseColor("#1C1917"); textSize = 22f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        canvas.drawText(p.fullName, 297.5f, 54f, namePaint)

        val cp = Paint().apply { color = Color.parseColor("#57534E"); textSize = 9f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        canvas.drawText("${p.title}  â€¢  ${p.email}  â€¢  ${p.phone}  â€¢  ${p.location}", 297.5f, 72f, cp)

        val sep = Paint().apply { color = Color.parseColor("#A8A29E"); strokeWidth = 0.8f }
        canvas.drawLine(40f, 85f, 555f, 85f, sep)

        var curY = 110f
        if (p.summary.isNotBlank()) {
            curY = drawSectionHeader(canvas, "Objective", 40f, curY, 515f)
            val bp = Paint().apply { color = Color.parseColor("#292524"); textSize = 9.5f; isAntiAlias = true }
            curY = drawWrappedText(canvas, p.summary, 40f, curY + 12f, 515f, bp) + 14f
        }
        if (p.experiences.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "Experience", 40f, curY, 515f) + 14f
            for (exp in p.experiences) {
                val rp = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawText("${exp.role}, ${exp.company}", 40f, curY, rp)
                val dp = Paint().apply { color = Color.parseColor("#78716C"); textSize = 9f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
                canvas.drawText(exp.duration, 555f, curY, dp)
                curY += 12f
                if (exp.details.isNotBlank()) {
                    val detp = Paint().apply { color = Color.parseColor("#44403C"); textSize = 8.8f; isAntiAlias = true }
                    curY = drawWrappedText(canvas, exp.details, 48f, curY, 507f, detp)
                }
                curY += 12f
            }
        }
        if (p.educations.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "Education", 40f, curY, 515f) + 14f
            for (edu in p.educations) {
                val ep = Paint().apply { color = Color.BLACK; textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawText("${edu.degree}, ${edu.institution}", 40f, curY, ep)
                val yp = Paint().apply { color = Color.parseColor("#78716C"); textSize = 8.8f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
                canvas.drawText(edu.year, 555f, curY, yp)
                curY += 14f
            }
        }
        if (p.skills.isNotEmpty()) {
            curY = drawSectionHeader(canvas, "Skills & Proficiencies", 40f, curY, 515f) + 14f
            val sp = Paint().apply { color = Color.parseColor("#292524"); textSize = 9f; isAntiAlias = true }
            curY = drawWrappedText(canvas, p.skills.joinToString(", "), 40f, curY, 515f, sp)
        }
    }

    private fun drawCreativePortfolio(canvas: android.graphics.Canvas, p: ResumeProfile) {
        canvas.drawColor(Color.WHITE)

        // Left sidebar
        val railPaint = Paint().apply { color = Color.parseColor("#140D12") }
        canvas.drawRect(0f, 0f, 185f, 842f, railPaint)

        // Left rail text
        val namePaint = Paint().apply { color = Color.WHITE; textSize = 17f; isFakeBoldText = true; isAntiAlias = true }
        canvas.drawText(p.fullName, 20f, 50f, namePaint)

        val titlePaint = Paint().apply { color = Color.parseColor("#EF4444"); textSize = 10f; isAntiAlias = true }
        canvas.drawText(p.title, 20f, 68f, titlePaint)

        var railY = 110f
        railY = drawSectionHeader(canvas, "CONTACT", 20f, railY, 145f, textColor = Color.parseColor("#EF4444")) + 14f
        val cp = Paint().apply { color = Color.parseColor("#E2E8F0"); textSize = 8.5f; isAntiAlias = true }
        if (p.email.isNotBlank()) { canvas.drawText(p.email, 20f, railY, cp); railY += 14f }
        if (p.phone.isNotBlank()) { canvas.drawText(p.phone, 20f, railY, cp); railY += 14f }
        if (p.location.isNotBlank()) { canvas.drawText(p.location, 20f, railY, cp); railY += 24f }

        if (p.skills.isNotEmpty()) {
            railY = drawSectionHeader(canvas, "SKILLS", 20f, railY, 145f, textColor = Color.parseColor("#EF4444")) + 14f
            val sp = Paint().apply { color = Color.parseColor("#CBD5E1"); textSize = 8.5f; isAntiAlias = true }
            for (s in p.skills) {
                canvas.drawText("â€¢ $s", 20f, railY, sp)
                railY += 13f
            }
        }

        // Right body content
        var rightY = 50f
        if (p.summary.isNotBlank()) {
            rightY = drawSectionHeader(canvas, "PROFILE", 205f, rightY, 350f)
            val bp = Paint().apply { color = Color.parseColor("#334155"); textSize = 9.5f; isAntiAlias = true }
            rightY = drawWrappedText(canvas, p.summary, 205f, rightY + 14f, 350f, bp) + 16f
        }

        if (p.experiences.isNotEmpty()) {
            rightY = drawSectionHeader(canvas, "EXPERIENCE", 205f, rightY, 350f) + 14f
            for (exp in p.experiences) {
                val rp = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawText(exp.role, 205f, rightY, rp)
                val dp = Paint().apply { color = Color.parseColor("#DC2626"); textSize = 8.5f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
                canvas.drawText(exp.duration, 555f, rightY, dp)
                rightY += 12f

                val cp2 = Paint().apply { color = Color.parseColor("#64748B"); textSize = 9.5f; isAntiAlias = true }
                canvas.drawText(exp.company, 205f, rightY, cp2)
                rightY += 12f

                if (exp.details.isNotBlank()) {
                    val detp = Paint().apply { color = Color.parseColor("#475569"); textSize = 8.8f; isAntiAlias = true }
                    rightY = drawWrappedText(canvas, exp.details, 210f, rightY, 345f, detp)
                }
                rightY += 14f
            }
        }
    }

    private fun drawSectionHeader(
        canvas: android.graphics.Canvas,
        title: String,
        x: Float,
        y: Float,
        width: Float,
        textColor: Int = Color.parseColor("#DC2626")
    ): Float {
        val hp = Paint().apply {
            color = textColor
            textSize = 10.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(title, x, y, hp)

        val lp = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 0.8f
        }
        val textW = hp.measureText(title)
        canvas.drawLine(x + textW + 8f, y - 4f, x + width, y - 4f, lp)

        return y
    }

    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint
    ): Float {
        var curY = startY
        val words = text.split(" ")
        var line = StringBuilder()

        for (w in words) {
            val testLine = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(testLine) <= maxWidth) {
                line.append(if (line.isEmpty()) w else " $w")
            } else {
                canvas.drawText(line.toString(), x, curY, paint)
                curY += paint.textSize + 4f
                line = StringBuilder(w)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, curY, paint)
            curY += paint.textSize
        }
        return curY
    }
}
