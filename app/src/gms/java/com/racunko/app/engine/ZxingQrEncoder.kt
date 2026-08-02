package com.racunko.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.racunko.platform.QrEncoder

/**
 * NBS IPS QR encoder via ZXing. Both flavors encode with ZXing (only decode/OCR
 * differ), so this class is duplicated verbatim in the gms and foss source sets
 * — they are never compiled together, and this keeps the engine out of the
 * engine-free main source set without a shared-srcDir dependency. Error-
 * correction level M and a quiet zone per the IPS QR spec; the app requests
 * >= 660 px so it stays scannable from the gallery.
 */
class ZxingQrEncoder : QrEncoder {

    override fun encode(payload: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
