package se.koditoriet.snout.codec

import android.graphics.Bitmap
import android.media.Image
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.math.min

class QrCodeReader {
    private val reader: MultiFormatReader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    /**
     * Try to scan an image from camera.
     * Image will be cropped to the center 2/3rds.
     */
    fun tryScanImage(image: Image): String? = try {
        val bitmap = createBinaryBitmap(image)
        reader.decode(bitmap)?.text
    } catch (_: Exception) {
        null
    }

    /**
     * Try to scan a bitmap image.
     * The entire image is scanned; no cropping.
     */
    fun tryScanBitmap(bitmap: Bitmap): String? = try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        reader.decode(binaryBitmap)?.text
    } catch (_: Exception) {
        null
    }

    private fun createBinaryBitmap(image: Image): BinaryBitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = image.width
        val height = image.height

        val size = min(width, height) * 2 / 3
        val left = (width - size) / 2
        val top = (height - size) / 2

        val source = PlanarYUVLuminanceSource(
            bytes, width, height,
            left, top, size, size, false
        )
        return BinaryBitmap(HybridBinarizer(source))
    }
}