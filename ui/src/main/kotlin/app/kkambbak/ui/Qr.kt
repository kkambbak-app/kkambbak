package app.kkambbak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 사고 기록지를 휴대폰으로 옮기는 QR. 서버 없이 기록 요약과 해시를 그대로 담는다. */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
        )
    }
    Canvas(modifier.size(size)) {
        drawRect(Color.White)
        val n = matrix.width
        val cell = this.size.width / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
