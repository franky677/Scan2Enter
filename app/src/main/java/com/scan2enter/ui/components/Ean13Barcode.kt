package com.scan2enter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Ean13Barcode(
    code: String,
    modifier: Modifier = Modifier
) {
    val modules = remember(code) { encodeEan13Modules(code) }

    Canvas(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (modules.isEmpty()) return@Canvas

        val quietModules = 10
        val totalModules = modules.length + quietModules * 2
        val moduleWidth = size.width / totalModules.toFloat()

        modules.forEachIndexed { index, bit ->
            if (bit != '1') return@forEachIndexed
            val isGuard = index in 0..2 || index in 45..49 || index in 92..94
            drawRect(
                color = Color.Black,
                topLeft = Offset((index + quietModules) * moduleWidth, 0f),
                size = Size(
                    moduleWidth + 0.5f,
                    if (isGuard) size.height else size.height * 0.82f
                )
            )
        }
    }
}

private fun encodeEan13Modules(code: String): String {
    if (code.length != 13 || !code.all(Char::isDigit)) return ""
    val l = arrayOf("0001101","0011001","0010011","0111101","0100011","0110001","0101111","0111011","0110111","0001011")
    val g = arrayOf("0100111","0110011","0011011","0100001","0011101","0111001","0000101","0010001","0001001","0010111")
    val r = arrayOf("1110010","1100110","1101100","1000010","1011100","1001110","1010000","1000100","1001000","1110100")
    val parity = arrayOf("LLLLLL","LLGLGG","LLGGLG","LLGGGL","LGLLGG","LGGLLG","LGGGLL","LGLGLG","LGLGGL","LGGLGL")
    val first = code[0].digitToInt()
    val result = StringBuilder(95)
    result.append("101")
    for (index in 1..6) {
        val digit = code[index].digitToInt()
        result.append(if (parity[first][index - 1] == 'L') l[digit] else g[digit])
    }
    result.append("01010")
    for (index in 7..12) result.append(r[code[index].digitToInt()])
    result.append("101")
    return result.toString()
}
