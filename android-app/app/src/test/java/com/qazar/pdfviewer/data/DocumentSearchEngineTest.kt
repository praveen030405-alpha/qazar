package com.qazar.pdfviewer.data

import androidx.compose.ui.geometry.Rect
import com.qazar.pdfviewer.bridge.ExtractedWord
import com.qazar.pdfviewer.bridge.PageTextHierarchy
import com.qazar.pdfviewer.ui.viewer.PdfPageModel
import com.qazar.pdfviewer.ui.viewer.text.TextExtractionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentSearchEngineTest {

    @Before
    fun setup() {
        DocumentSearchEngine.clearCache()
        TextExtractionEngine.clear()
    }

    @Test
    fun testAccurateWordSearchMatching() {
        val page = PdfPageModel(
            pageIndex = 0,
            widthPoints = 595f,
            heightPoints = 842f
        )

        // Mock exact extracted words from PDFium
        val words = listOf(
            ExtractedWord("Quantum", Rect(72f, 800f, 140f, 785f), 0, 7),
            ExtractedWord("Architecture", Rect(145f, 800f, 250f, 785f), 8, 20),
            ExtractedWord("for", Rect(255f, 800f, 275f, 785f), 21, 24),
            ExtractedWord("PDF", Rect(280f, 800f, 310f, 785f), 25, 28)
        )

        val hierarchy = PageTextHierarchy(
            pageIndex = 0,
            fullText = "Quantum Architecture for PDF",
            words = words,
            lines = emptyList()
        )

        TextExtractionEngine.registerPageText(
            pageIndex = 0,
            hierarchy = hierarchy,
            pageWidth = 595f,
            pageHeight = 842f
        )

        // 1. Single word exact match
        val hits = DocumentSearchEngine.search(
            filePath = "test_doc.pdf",
            pages = listOf(page),
            query = "Quantum"
        )

        assertEquals(1, hits.size)
        val hit = hits[0]
        assertEquals(0, hit.pageIndex)
        assertTrue(hit.snippet.contains("Quantum"))
        assertEquals(1, hit.highlightBounds.size)

        val bounds = hit.highlightBounds[0]
        assertEquals(72f, bounds.left, 0.1f)
        assertEquals(800f, bounds.top, 0.1f)
        assertEquals(140f, bounds.right, 0.1f)
        assertEquals(785f, bounds.bottom, 0.1f)

        // 2. Multi-word continuous phrase match
        val multiHits = DocumentSearchEngine.search(
            filePath = "test_doc.pdf",
            pages = listOf(page),
            query = "Quantum Architecture"
        )

        assertEquals(1, multiHits.size)
        val multiHit = multiHits[0]
        assertEquals(1, multiHit.highlightBounds.size)
        val multiBounds = multiHit.highlightBounds[0]

        // Should span continuously from "Quantum" left (72) to "Architecture" right (250)
        assertEquals(72f, multiBounds.left, 0.1f)
        assertEquals(250f, multiBounds.right, 0.1f)
        assertEquals(800f, multiBounds.top, 0.1f)
        assertEquals(785f, multiBounds.bottom, 0.1f)
    }

    @Test
    fun testCaseInsensitiveSearch() {
        val page = PdfPageModel(
            pageIndex = 0,
            widthPoints = 595f,
            heightPoints = 842f
        )

        val words = listOf(
            ExtractedWord("Enterprise", Rect(50f, 700f, 130f, 685f), 0, 10),
            ExtractedWord("Standard", Rect(135f, 700f, 210f, 685f), 11, 19)
        )

        val hierarchy = PageTextHierarchy(
            pageIndex = 0,
            fullText = "Enterprise Standard",
            words = words,
            lines = emptyList()
        )

        TextExtractionEngine.registerPageText(
            pageIndex = 0,
            hierarchy = hierarchy,
            pageWidth = 595f,
            pageHeight = 842f
        )

        val hits = DocumentSearchEngine.search(
            filePath = "test_doc2.pdf",
            pages = listOf(page),
            query = "enterprise"
        )

        assertEquals(1, hits.size)
        assertEquals(50f, hits[0].highlightBounds[0].left, 0.1f)
        assertEquals(130f, hits[0].highlightBounds[0].right, 0.1f)
    }
}
