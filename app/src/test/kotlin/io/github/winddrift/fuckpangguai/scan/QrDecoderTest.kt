package io.github.winddrift.fuckpangguai.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {

    private val payload = "https://h5.qiekj.com/skip?NQT=abc123"

    @Test
    fun `decodes camera luma frame`() {
        val matrix = qrMatrix()
        val frame = Frame(matrix.toArgb(), matrix.width, matrix.height)

        val decoded = QrDecoder().decodeLuma(
            frame.pixels.toLuma(frame.width, frame.height),
            frame.width,
            frame.height,
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun `decodes rotated camera frame without rotating luminance source`() {
        val matrix = qrMatrix()
        val rotated = Frame(matrix.toArgb(), matrix.width, matrix.height).rotateClockwise()

        val decoded = QrDecoder().decodeLuma(
            rotated.pixels.toLuma(rotated.width, rotated.height),
            rotated.width,
            rotated.height,
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun `decodes album image`() {
        val matrix = qrMatrix()
        val frame = Frame(matrix.toArgb(), matrix.width, matrix.height)

        val decoded = QrDecoder().decodePhotoArgb(frame.pixels, frame.width, frame.height)

        assertEquals(payload, decoded)
    }

    @Test
    fun `decodes small qr inside a phone screenshot`() {
        val matrix = qrMatrix(size = 600)
        val frame = embed(matrix, canvasWidth = 1080, canvasHeight = 2400, left = 300, top = 1500, size = 480)

        val decoded = QrDecoder().decodePhotoArgb(frame.pixels, frame.width, frame.height)

        assertEquals(payload, decoded)
    }

    @Test
    fun `decodes inverted qr`() {
        val matrix = qrMatrix()
        val normal = Frame(matrix.toArgb(), matrix.width, matrix.height)
        val inverted = Frame(IntArray(normal.pixels.size) { i ->
            if (normal.pixels[i] == BLACK) WHITE else BLACK
        }, normal.width, normal.height)

        val decoded = QrDecoder().decodePhotoArgb(inverted.pixels, inverted.width, inverted.height)

        assertEquals(payload, decoded)
    }

    @Test
    fun `returns null when image has no qr`() {
        val blank = IntArray(200 * 200) { WHITE }

        assertNull(QrDecoder().decodePhotoArgb(blank, 200, 200))
    }

    private fun qrMatrix(size: Int = 420): BitMatrix =
        QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 2),
        )

    private fun BitMatrix.toArgb(): IntArray = IntArray(width * height) { index ->
        if (get(index % width, index / width)) BLACK else WHITE
    }

    private fun embed(
        matrix: BitMatrix,
        canvasWidth: Int,
        canvasHeight: Int,
        left: Int,
        top: Int,
        size: Int,
    ): Frame {
        val pixels = IntArray(canvasWidth * canvasHeight) { WHITE }
        for (y in 0 until size) {
            val moduleY = y * matrix.height / size
            for (x in 0 until size) {
                val moduleX = x * matrix.width / size
                if (matrix.get(moduleX, moduleY)) {
                    pixels[(top + y) * canvasWidth + (left + x)] = BLACK
                }
            }
        }
        return Frame(pixels, canvasWidth, canvasHeight)
    }

    private fun IntArray.toLuma(width: Int, height: Int): ByteArray =
        ByteArray(width * height) { index ->
            val pixel = this[index]
            val red = (pixel shr 16) and 0xff
            val green = (pixel shr 8) and 0xff
            val blue = pixel and 0xff
            ((red + 2 * green + blue) / 4).toByte()
        }

    private fun Frame.rotateClockwise(): Frame {
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[x * height + (height - 1 - y)] = pixels[y * width + x]
            }
        }
        return Frame(out, height, width)
    }

    private data class Frame(val pixels: IntArray, val width: Int, val height: Int)

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
