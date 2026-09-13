package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.vision.*
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

/** Values transcribed by visual inspection, independently of the recognizer. */
class RealCellInkTest {
    private val puzzles = mapOf(
        "approved-04.jpg" to "902870000010240069000500000500300280000900156001005943104637000007000020350482000",
        "approved-10.jpg" to "401706300003402800200000004090103040100084003340607090804309005006201400009805200",
    )
    @Test fun visibleDigitsAndPencilNotesAreNeverClassifiedAsEmpty() {
        for ((name, puzzle) in puzzles) {
            val image = requireNotNull(javaClass.getResourceAsStream("/sudoku/fixtures/approved/$name")).use { ImageIO.read(it) }
            val pixels = PixelImage(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
            val detected = GridBoardScanner().scan(pixels) as BoardDetection.Detected
            val normalized = detected.geometry.normalize(pixels, 900)
            val kinds = (0 until 81).map { CellCropAnalyzer.analyze(normalized, it).kind }
            for ((index, digit) in puzzle.withIndex()) if (digit != '0') {
                assertNotEquals("$name R${index / 9 + 1}C${index % 9 + 1} must not be overwritten", InkKind.BLANK, kinds[index])
            }
            if (name == "approved-10.jpg") for (index in listOf(4, 13, 21)) {
                assertEquals("Pencil notes must prevent autofill", InkKind.AMBIGUOUS, kinds[index])
            }
            println("$name cell segmentation: ${kinds.groupingBy { it }.eachCount()}")
            assertTrue("Must recognize some clear blank cells", kinds.count { it == InkKind.BLANK } >= 20)
            assertTrue("Must pass full-sized glyphs to OCR", kinds.count { it == InkKind.DIGIT } >= 20)
        }
    }
}
