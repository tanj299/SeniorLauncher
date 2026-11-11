package com.example.seniorlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

object AvatarGenerator {
    private val COLOR_PALETTE = intArrayOf(
        0xFF1976D2.toInt(), 0xFF2E7D32.toInt(), 0xFFD32F2F.toInt(),
        0xFF7B1FA2.toInt(), 0xFFE65100.toInt(), 0xFF00838F.toInt()
    )

    fun createInitialAvatar(name: String, sizePx: Int = 144): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val initial = extractInitial(name)
        val colorIndex = Math.abs(name.hashCode()) % COLOR_PALETTE.size

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PALETTE[colorIndex]
            style = Paint.Style.FILL
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, backgroundPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.45f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textBounds = Rect()
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        val yOffset = radius - textBounds.exactCenterY()
        canvas.drawText(initial, radius, yOffset, textPaint)

        return bitmap
    }

    private fun extractInitial(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "?"
        val codePoint = trimmed.codePointAt(0)
        val firstChar = String(Character.toChars(codePoint))
        return if (trimmed.length > firstChar.length && (codePoint in 0x1F600..0x1F64F || codePoint in 0x1F300..0x1F5FF)) {
            val secondCodePoint = trimmed.codePointAt(firstChar.length)
            String(Character.toChars(secondCodePoint)).uppercase()
        } else {
            firstChar.uppercase()
        }
    }
}
