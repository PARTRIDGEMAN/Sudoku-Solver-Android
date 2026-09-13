package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImageRect
import com.partridgeman.sudokusolver.vision.PixelImage
import org.junit.Assert.*
import org.junit.Test

class CellRecognitionTest {
    private fun crop(background: Int = -1, marks: (Int, Int) -> Int? = { _, _ -> null }) =
        PixelImage(42, 42, IntArray(42 * 42) { marks(it % 42, it / 42) ?: background })
    private val black = 0xff000000.toInt()

    @Test fun blankLightDarkAndColoredCells() {
        for (color in listOf(-1, black, 0xffb0d8ee.toInt())) {
            val ink = CellInkAnalyzer.analyze(crop(color))
            assertEquals(InkKind.BLANK, ink.kind)
            assertEquals(color, ink.background)
        }
    }

    @Test fun fullSizeGlyphInBothThemes() {
        for ((background, foreground) in listOf(-1 to black, black to -1)) {
            assertEquals(InkKind.DIGIT, CellInkAnalyzer.analyze(crop(background) { x, y ->
                if (x in 18..22 && y in 8..33) foreground else null
            }).kind)
        }
    }

    @Test fun faintMarksAreNeverBlank() {
        assertEquals(InkKind.AMBIGUOUS, CellInkAnalyzer.analyze(crop { x, y ->
            if (x in 18..22 && y in 8..33) 0xffeeeeee.toInt() else null
        }).kind)
    }

    @Test fun notesAndClippedDigitsAreRejected() {
        val images = listOf(
            crop { x, y -> if (x in 5..7 && y in 5..12) black else null },
            crop { x, y -> if ((x in 5..7 || x in 30..32) && y in 8..33) black else null },
            crop { x, y -> if (x in 0..4 && y in 8..33) black else null },
        )
        images.forEach { assertEquals(InkKind.AMBIGUOUS, CellInkAnalyzer.analyze(it).kind) }
    }

    @Test fun strictAgreementStillRequiresTwoMatchingStrongDigits() {
        assertEquals(7, RecognitionPolicy.agree(DigitReading("7", .99f), DigitReading("7", .95f)).value)
        for ((first, second) in listOf(
            DigitReading("7", .99f) to DigitReading("1", .99f),
            DigitReading("7", .99f) to DigitReading("7", .5f),
            DigitReading("12", .99f) to DigitReading("12", .99f),
            DigitReading("0", .99f) to DigitReading("0", .99f),
            DigitReading("7", Float.NaN) to DigitReading("7", .99f),
            null to DigitReading("7", .99f),
        )) assertNull(RecognitionPolicy.agree(first, second).value)
    }

    @Test fun contextualFusionUsesAgreementAndStrongSinglePasses() {
        val agreed = RecognitionPolicy.resolve(DigitReading("4", .50f), DigitReading("4", .55f))
        assertEquals(4, agreed.value)
        assertTrue(agreed.accepted)

        assertEquals(8, RecognitionPolicy.resolve(DigitReading("8", .82f), null).value)
        assertNull(RecognitionPolicy.resolve(DigitReading("8", .60f), null).value)
        assertNull(RecognitionPolicy.resolve(DigitReading("3", .88f), DigitReading("8", .84f)).value)
        assertEquals(3, RecognitionPolicy.resolve(DigitReading("3", .99f), DigitReading("8", .30f)).value)
    }

    @Test fun ocrMapperUsesGridGeometryAndRejectsNotesAndConflicts() {
        val lines = (0..9).map { it * 100.0 }
        val geometry = BoardGeometry(lines, lines)
        val tokens = listOf(
            OcrToken("2", .91f, ImageRect(32.0, 20.0, 68.0, 82.0)),             // R1C1
            OcrToken("7", .88f, ImageRect(132.0, 18.0, 168.0, 84.0)),           // R1C2
            OcrToken("9", .99f, ImageRect(230.0, 4.0, 250.0, 24.0)),            // tiny note: reject
            OcrToken("4", .90f, ImageRect(332.0, 18.0, 368.0, 82.0)),           // conflict pair
            OcrToken("5", .86f, ImageRect(334.0, 20.0, 370.0, 84.0)),
            OcrToken("6", .95f, ImageRect(432.0, 20.0, 468.0, 82.0)),           // duplicate same digit okay
            OcrToken("6", .80f, ImageRect(434.0, 22.0, 470.0, 84.0)),
            OcrToken("3", .99f, ImageRect(950.0, 20.0, 980.0, 80.0)),           // outside board
        )
        val mapped = BoardOcrMapper.map(tokens, geometry)
        assertEquals("2", mapped.getValue(0).text)
        assertEquals("7", mapped.getValue(1).text)
        assertFalse(mapped.containsKey(2))
        assertFalse(mapped.containsKey(3))
        assertEquals("6", mapped.getValue(4).text)
        assertEquals(3, mapped.size)
    }

    @Test fun everyCellMustBeRecognizedWithoutInventingBlanks() {
        val cells = List(81) { CellReading(0, 1.0, "blank") }
        assertNotNull(RecognitionPolicy.board(cells))
        assertNull(RecognitionPolicy.board(cells.dropLast(1)))
        for (bad in listOf(CellReading(null, 1.0, "unknown"), CellReading(4, .64, "low"),
            CellReading(10, 1.0, "invalid"), CellReading(1, Double.NaN, "invalid"))) {
            assertNull(RecognitionPolicy.board(cells.toMutableList().also { it[40] = bad }))
        }
    }
}
