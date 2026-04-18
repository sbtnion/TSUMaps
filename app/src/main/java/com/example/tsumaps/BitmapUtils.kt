package com.example.tsumaps

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor

object BitmapUtils {
    fun loadGridFromBitmap(bitmap: Bitmap): Array<IntArray> {
        return Array(bitmap.height) { y ->
            IntArray(bitmap.width) { x ->
                val pixel = bitmap.getPixel(x, y)
                val gray = (AndroidColor.red(pixel) + AndroidColor.green(pixel) + AndroidColor.blue(pixel)) / 3
                if (gray < 128) 1 else 0
            }
        }
    }
    fun readAssetFile(context: android.content.Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

}
