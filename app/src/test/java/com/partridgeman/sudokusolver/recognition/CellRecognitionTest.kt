package com.partridgeman.sudokusolver.recognition

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
    @Test fun agreementRequiresStrictDigitAndBothConfidences() {
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
    @Test fun everyCellMustBeRecognizedWithoutInventingBlanks() {
        val cells = List(81) { CellReading(0, 1.0, "blank") }
        assertNotNull(RecognitionPolicy.board(cells))
        assertNull(RecognitionPolicy.board(cells.dropLast(1)))
        for (bad in listOf(CellReading(null, 1.0, "unknown"), CellReading(4, .89, "low"),
            CellReading(10, 1.0, "invalid"), CellReading(1, Double.NaN, "invalid"))) {
            assertNull(RecognitionPolicy.board(cells.toMutableList().also { it[40] = bad }))
        }
    }
}
