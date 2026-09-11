package com.example.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log

/**
 * PokerImageProcessor: Motor de procesamiento visual multirresolución y realce de contraste.
 *
 * Genera vistas ópticas complementarias para modelos multimodales (Gemini 3.8 Flash):
 * 1. Vista Macro Panorámica: Mesa completa con asientos, fichas, botón Dealer y pozo.
 * 2. Vista Micro-Zoom Mesa: Recorte de alta densidad óptica de cartas comunitarias (Flop/Turn/River).
 * 3. Vista Micro-Zoom Hero: Recorte de alta densidad de las cartas de Hero en la mitad inferior (sin recortar esquinas).
 */
object PokerImageProcessor {

    private const val TAG = "PokerImageProcessor"

    /**
     * Recorta o realza regiones de la mesa para compatibilidad con llamadas existentes.
     */
    fun cropPokerTableRegions(source: Bitmap): Bitmap {
        return enhanceContrast(source)
    }

    /**
     * Genera las 3 perspectivas ópticas para la visión multimodal de Gemini 3.8 Flash.
     */
    fun createMultiresolutionVisionParts(source: Bitmap): List<Bitmap> {
        val parts = mutableListOf<Bitmap>()
        try {
            val width = source.width
            val height = source.height

            if (width < 60 || height < 60) {
                parts.add(source)
                return parts
            }

            // 1. Vista Macro: Mesa Completa (Escalada a 960p máximo para visión global ultrarrápida)
            val maxMacroDim = 960
            val maxSourceDim = maxOf(width, height)
            val scaledMacro = if (maxSourceDim > maxMacroDim) {
                val scale = maxMacroDim.toFloat() / maxSourceDim.toFloat()
                val targetW = (width * scale).toInt().coerceAtLeast(1)
                val targetH = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(source, targetW, targetH, true)
            } else {
                source
            }
            val macroBitmap = enhanceContrast(scaledMacro, contrast = 1.15f, brightness = 5f)
            if (scaledMacro != source && scaledMacro != macroBitmap) scaledMacro.recycle()
            parts.add(macroBitmap)

            // 2. Micro-Zoom Cartas Comunitarias (Mesa / Flop / Turn / River: 5 cartas completas)
            // x: 8% a 92% del ancho, y: 34% a 65% del alto
            val boardLeft = (width * 0.08f).toInt().coerceIn(0, width - 1)
            val boardTop = (height * 0.34f).toInt().coerceIn(0, height - 1)
            val boardWidth = (width * 0.84f).toInt().coerceIn(10, width - boardLeft)
            val boardHeight = (height * 0.30f).toInt().coerceIn(10, height - boardTop)

            val rawBoard = Bitmap.createBitmap(source, boardLeft, boardTop, boardWidth, boardHeight)
            val enhancedBoard = enhanceContrast(rawBoard, contrast = 1.25f, brightness = 8f)
            if (enhancedBoard != rawBoard) rawBoard.recycle()

            val maxCropDim = 640
            val finalBoard = if (maxOf(enhancedBoard.width, enhancedBoard.height) > maxCropDim) {
                val scale = maxCropDim.toFloat() / maxOf(enhancedBoard.width, enhancedBoard.height).toFloat()
                val targetW = (enhancedBoard.width * scale).toInt().coerceAtLeast(1)
                val targetH = (enhancedBoard.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(enhancedBoard, targetW, targetH, true)
                if (scaled != enhancedBoard) enhancedBoard.recycle()
                scaled
            } else {
                enhancedBoard
            }
            parts.add(finalBoard)

            // 3. Micro-Zoom Cartas Hero (Mitad inferior completa para abarcar asientos esquineros como Jr699 y centrales)
            // x: 0% a 100% del ancho, y: 62% a 97% del alto
            val heroLeft = 0
            val heroTop = (height * 0.62f).toInt().coerceIn(0, height - 1)
            val heroWidth = width
            val heroHeight = (height * 0.35f).toInt().coerceIn(10, height - heroTop)

            val rawHero = Bitmap.createBitmap(source, heroLeft, heroTop, heroWidth, heroHeight)
            val enhancedHero = enhanceContrast(rawHero, contrast = 1.25f, brightness = 8f)
            if (enhancedHero != rawHero) rawHero.recycle()

            val finalHero = if (maxOf(enhancedHero.width, enhancedHero.height) > maxCropDim) {
                val scale = maxCropDim.toFloat() / maxOf(enhancedHero.width, enhancedHero.height).toFloat()
                val targetW = (enhancedHero.width * scale).toInt().coerceAtLeast(1)
                val targetH = (enhancedHero.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(enhancedHero, targetW, targetH, true)
                if (scaled != enhancedHero) enhancedHero.recycle()
                scaled
            } else {
                enhancedHero
            }
            parts.add(finalHero)

            Log.d(TAG, "Successfully generated 3 lightweight vision parts: Macro, BoardZoom, HeroZoom")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating vision parts, falling back to original bitmap", e)
            if (parts.isEmpty()) parts.add(source)
        }
        return parts
    }

    /**
     * Realza dinámicamente el contraste y brillo de las cartas para resaltar palos y números.
     */
    fun enhanceContrast(source: Bitmap, contrast: Float = 1.2f, brightness: Float = 5f): Bitmap {
        return try {
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Matriz de ajuste de contraste y brillo
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            output
        } catch (e: Exception) {
            Log.w(TAG, "Contrast enhancement failed, returning source", e)
            source
        }
    }
}
