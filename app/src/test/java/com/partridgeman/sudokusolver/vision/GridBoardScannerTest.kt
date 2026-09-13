package com.partridgeman.sudokusolver.vision

import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GridBoardScannerTest {
    private data class Fixture(val name: String, val width: Int, val height: Int, val bounds: ImageRect, val tolerance: Double)
    private val fixtures: List<Fixture> get() = resource("geometry.csv").bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val values = line.split(',')
            Fixture(values[0], values[1].toInt(), values[2].toInt(), ImageRect(values[3].toDouble(), values[4].toDouble(), values[5].toDouble(), values[6].toDouble()), values[7].toDouble())
        }.toList()
    }
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sudoku/fixtures/approved/$name")) { "Missing fixture $name" }
    private fun load(fixture: Fixture): PixelImage {
        val image = resource(fixture.name).use { ImageIO.read(it) }
        assertEquals(fixture.width, image.width)
        assertEquals(fixture.height, image.height)
        return PixelImage(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }

    @Test fun approvedFixtureChecksums() {
        val pinnedNames = mutableListOf<String>()
        resource("SHA256SUMS.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                val (expected, path) = line.trim().split(Regex("\\s+"), limit = 2)
                pinnedNames.add(path.removePrefix("./"))
                val bytes = resource(path.removePrefix("./")).use { it.readBytes() }
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                assertEquals(path, expected, actual)
            }
        }
        assertEquals("Every pinned fixture must have independently reviewed geometry", pinnedNames.sorted(), fixtures.map { it.name }.sorted())
    }

    private fun verify(image: PixelImage, bounds: ImageRect, tolerance: Double, label: String): BoardDetection.Detected {
        val result = GridBoardScanner().scan(image)
        println("$label: $result")
        assertTrue("$label: $result", result is BoardDetection.Detected)
        result as BoardDetection.Detected
        val actual = result.geometry.bounds
        for ((a, b) in listOf(actual.left to bounds.left, actual.top to bounds.top, actual.right to bounds.right, actual.bottom to bounds.bottom)) {
            assertEquals("$label expected=$bounds actual=$actual diagnostics=${result.candidates}", b, a, tolerance)
        }
        assertEquals(81, result.geometry.cells.size)
        for (cell in result.geometry.cells) {
            assertTrue(actual.contains(cell.center))
            assertTrue(cell.bounds.contains(cell.center))
            assertTrue(cell.bounds.left >= 0 && cell.bounds.top >= 0 && cell.bounds.right <= image.width && cell.bounds.bottom <= image.height)
            val expected = ImagePoint(bounds.left + (cell.column + 0.5) * bounds.width / 9, bounds.top + (cell.row + 0.5) * bounds.height / 9)
            assertEquals(expected.x, cell.center.x, tolerance)
            assertEquals(expected.y, cell.center.y, tolerance)
        }
        result.geometry.cells.forEachIndexed { index, cell ->
            assertEquals(index / 9, cell.row)
            assertEquals(index % 9, cell.column)
            if (cell.column < 8) assertEquals(cell.bounds.right, result.geometry.cells[index + 1].bounds.left, 0.0)
            if (cell.row < 8) assertEquals(cell.bounds.bottom, result.geometry.cells[index + 9].bounds.top, 0.0)
        }
        assertEquals(450, result.normalized.width)
        assertEquals(450, result.normalized.height)
        assertTrue(result.confidence in 0.0..1.0)
        return result
    }

    @Test fun detectsReviewedRealScreenshots() { fixtures.forEach { fixture ->
        val image = load(fixture)
        val detection = verify(image, fixture.bounds, fixture.tolerance, fixture.name)
        val overlay = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        overlay.setRGB(0, 0, image.width, image.height, image.copyPixels(), 0, image.width)
        val graphics = overlay.createGraphics()
        graphics.color = java.awt.Color.GREEN
        val b = detection.geometry.bounds
        detection.geometry.horizontal.forEach { y -> graphics.drawLine(b.left.roundToInt(), y.roundToInt(), b.right.roundToInt(), y.roundToInt()) }
        detection.geometry.vertical.forEach { x -> graphics.drawLine(x.roundToInt(), b.top.roundToInt(), x.roundToInt(), b.bottom.roundToInt()) }
        graphics.color = java.awt.Color.MAGENTA
        detection.geometry.cells.forEach { graphics.fillOval(it.center.x.roundToInt() - 2, it.center.y.roundToInt() - 2, 5, 5) }
        graphics.dispose()
        val directory = File("build/reports/vision").apply { mkdirs() }
        ImageIO.write(overlay, "png", File(directory, fixture.name + "-overlay.png"))
        val normalized = BufferedImage(450, 450, BufferedImage.TYPE_INT_RGB)
        normalized.setRGB(0, 0, 450, 450, detection.normalized.copyPixels(), 0, 450)
        ImageIO.write(normalized, "png", File(directory, fixture.name + "-normalized.png"))
    } }

    @Test fun translationAndPadding() { fixtures.forEach { fixture ->
        val source = load(fixture)
        val dx = 47
        val dy = 83
        val width = source.width + 131
        val height = source.height + 169
        val pixels = IntArray(width * height) { 0xff435369.toInt() }
        for (y in 0 until source.height) for (x in 0 until source.width) pixels[(y + dy) * width + x + dx] = source[x, y]
        val b = fixture.bounds
        verify(PixelImage(width, height, pixels), ImageRect(b.left + dx, b.top + dy, b.right + dx, b.bottom + dy), fixture.tolerance + 2, fixture.name + " padded")
    } }

    @Test fun scales() { fixtures.forEach { fixture ->
        for (factor in listOf(0.65, 1.35)) {
            val source = load(fixture)
            val image = source.resize((source.width * factor).roundToInt(), (source.height * factor).roundToInt())
            val sx = image.width.toDouble() / source.width
            val sy = image.height.toDouble() / source.height
            val b = fixture.bounds
            verify(image, ImageRect(b.left * sx, b.top * sy, b.right * sx, b.bottom * sy), fixture.tolerance * factor + 2, fixture.name + " scale $factor")
        }
    } }

    @Test fun luminanceContrastAndInvertedTheme() { fixtures.forEach { fixture ->
        for ((contrast, brightness) in listOf(0.65 to 35, 1.1 to -20, -1.0 to 255)) {
            val source = load(fixture)
            val pixels = source.copyPixels().map { pixel ->
                var out = 0xff000000.toInt()
                for (shift in listOf(0, 8, 16)) out = out or ((((pixel ushr shift) and 255) * contrast + brightness).roundToInt().coerceIn(0, 255) shl shift)
                out
            }.toIntArray()
            verify(PixelImage(source.width, source.height, pixels), fixture.bounds, fixture.tolerance + 2, fixture.name + " luminance $contrast/$brightness")
        }
    } }

    @Test fun lightBlurAndNoise() { fixtures.forEach { fixture ->
        val source = load(fixture)
        val random = Random(731)
        val pixels = IntArray(source.width * source.height) { index ->
            val x = index % source.width
            val y = index / source.width
            var result = 0xff000000.toInt()
            val noise = random.nextInt(-4, 5)
            for (shift in listOf(0, 8, 16)) {
                var total = 0
                for (dy in -1..1) for (dx in -1..1) total += (source[(x + dx).coerceIn(0, source.width - 1), (y + dy).coerceIn(0, source.height - 1)] ushr shift) and 255
                result = result or ((total / 9 + noise).coerceIn(0, 255) shl shift)
            }
            result
        }
        verify(PixelImage(source.width, source.height, pixels), fixture.bounds, fixture.tolerance + 2, fixture.name + " blur/noise")
    } }

    @Test fun deterministicGeometryAndConfidence() { fixtures.forEach { fixture ->
        val image = load(fixture)
        val first = verify(image, fixture.bounds, fixture.tolerance, fixture.name)
        val second = GridBoardScanner().scan(image) as BoardDetection.Detected
        assertEquals(first.geometry.horizontal, second.geometry.horizontal)
        assertEquals(first.geometry.vertical, second.geometry.vertical)
        assertEquals(first.confidence, second.confidence, 0.0)
        assertArrayEquals(first.normalized.copyPixels(), second.normalized.copyPixels())
    } }

    private fun synthetic(grid: Int, vertical: Boolean = true): PixelImage {
        val image = BufferedImage(540, 720, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = java.awt.Color.BLACK
        graphics.drawRect(45, 135, 450, 450)
        if (grid > 0) for (index in 1 until grid) {
            val offset = 450 * index / grid
            graphics.drawLine(45, 135 + offset, 495, 135 + offset)
            if (vertical) graphics.drawLine(45 + offset, 135, 45 + offset, 585)
        }
        graphics.dispose()
        return PixelImage(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }

    @Test fun rejectsNonBoards() {
        val random = Random(942)
        val cases = listOf(PixelImage(360, 640, IntArray(360 * 640) { -1 }),
            PixelImage(360, 640, IntArray(360 * 640) { 0xff000000.toInt() or random.nextInt(0x1000000) }),
            synthetic(0), synthetic(8), synthetic(10), synthetic(9, false),
            synthetic(9).let { PixelImage(it.width, it.height, it.copyPixels().map { pixel -> pixel and 0x00ffffff }.toIntArray()) })
        cases.forEachIndexed { index, image -> assertTrue("negative $index", GridBoardScanner().scan(image) is BoardDetection.NoBoard) }
    }

    @Test fun syntheticNineByNineGridIsStructurallyValid() { verify(synthetic(9), ImageRect(45.0, 135.0, 495.0, 585.0), 3.0, "synthetic grid") }

    @Test fun rejectsTwoPlausibleBoards() {
        val source = synthetic(9)
        val width = source.width * 2
        val image = PixelImage(width, source.height, IntArray(width * source.height) { source[it % width % source.width, it / width] })
        val result = GridBoardScanner().scan(image)
        assertTrue(result.toString(), result is BoardDetection.NoBoard)
        assertTrue((result as BoardDetection.NoBoard).reason.contains("Multiple"))
    }

    @Test fun landscapeRotation() { fixtures.forEach { fixture ->
        val source = load(fixture)
        val rotated = PixelImage(source.height, source.width, IntArray(source.width * source.height) { index ->
            source[index / source.height, source.height - 1 - index % source.height]
        })
        val b = fixture.bounds
        verify(rotated, ImageRect(source.height - b.bottom, b.left, source.height - b.top, b.right), fixture.tolerance + 2, fixture.name + " rotated")
    } }
}
