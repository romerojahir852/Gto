package com.example.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log

/**
 * PokerImageProcessor: Motor de procesamiento y recorte de imagen para visión artificial.
 *
 * Filtra la contaminación visual (avatares, animaciones, chat, HUDs y marcos externos),
 * recortando y ensamblando con precisión quirúrgica únicamente las 2 áreas de interés matemático:
 * 1. Zona Central Comunitaria: Cartas comunitarias (Flop/Turn/River) y Bote Central.
 * 2. Zona Inferior Hero: Cartas propias del jugador (Hole cards) y monto de apuesta.
 */
object PokerImageProcessor {

    private const val TAG = "PokerImageProcessor"

    /**
     * Recorta y ensambla las regiones de interés matemático de la mesa de póker.
     * Genera un nuevo Bitmap de alta resolución compuesto exclusivamente por:
     * - Mitad superior: Cartas comunitarias y bote central (sin avatares laterales ni chat).
     * - Mitad inferior: Cartas propias del jugador (sin botones de acción ni avatares inferiores).
     */
    fun cropPokerTableRegions(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height

            if (width < 50 || height < 50) {
                Log.w(TAG, "Source bitmap too small to crop ($width x $height), returning original")
                return source
            }

            // 1. Zona Comunitaria + Bote (Franja central):
            // x: del 10% al 90% del ancho (elimina los avatares laterales izquierdo/derecho y chat)
            // y: del 30% al 63% del alto (abarca las cartas comunitarias Flop/Turn/River y el bote central)
            val centerLeft = (width * 0.10f).toInt().coerceAtLeast(0)
            val centerTop = (height * 0.30f).toInt().coerceAtLeast(0)
            val centerWidth = (width * 0.80f).toInt().coerceAtMost(width - centerLeft)
            val centerHeight = (height * 0.33f).toInt().coerceAtMost(height - centerTop)

            // 2. Zona Cartas Propias (Hero) (Cuadro inferior centrado en el borde inferior):
            // x: del 18% al 82% del ancho (centrado directamente en las cartas de la mano del usuario)
            // y: del 64% al 97% del alto (aislamiento estricto de las 2 cartas propias en la parte inferior)
            val heroLeft = (width * 0.18f).toInt().coerceAtLeast(0)
            val heroTop = (height * 0.64f).toInt().coerceAtLeast(0)
            val heroWidth = (width * 0.64f).toInt().coerceAtMost(width - heroLeft)
            val heroHeight = (height * 0.33f).toInt().coerceAtMost(height - heroTop)

            // 3. Zona Mesa Completa (Mesa elíptica, jugadores y botón Dealer 'D'):
            // x: del 5% al 95% del ancho, y: del 15% al 90% del alto
            val tableLeft = (width * 0.05f).toInt().coerceAtLeast(0)
            val tableTop = (height * 0.15f).toInt().coerceAtLeast(0)
            val tableWidth = (width * 0.90f).toInt().coerceAtMost(width - tableLeft)
            val tableHeight = (height * 0.75f).toInt().coerceAtMost(height - tableTop)

            // Validar que las sub-regiones sean válidas
            if (centerWidth <= 0 || centerHeight <= 0 || heroWidth <= 0 || heroHeight <= 0) {
                return source
            }

            val centerRegion = Bitmap.createBitmap(source, centerLeft, centerTop, centerWidth, centerHeight)
            val heroRegion = Bitmap.createBitmap(source, heroLeft, heroTop, heroWidth, heroHeight)
            val tableRegion = if (tableWidth > 0 && tableHeight > 0) {
                val rawTable = Bitmap.createBitmap(source, tableLeft, tableTop, tableWidth, tableHeight)
                // Escalar visión panorámica de la mesa a tamaño compacto
                val scale = 0.5f
                Bitmap.createScaledBitmap(rawTable, (tableWidth * scale).toInt().coerceAtLeast(10), (tableHeight * scale).toInt().coerceAtLeast(10), true).also {
                    if (it != rawTable) rawTable.recycle()
                }
            } else null

            // Ensamblar verticalmente:
            // Superior: Cartas Comunitarias & Bote
            // Medio: Cartas Hero Propias
            // Inferior: Panorama de la Mesa (para detectar botón Dealer y contar jugadores activos)
            val tableW = tableRegion?.width ?: 0
            val tableH = tableRegion?.height ?: 0
            val targetWidth = maxOf(centerWidth, heroWidth, tableW)
            val targetHeight = centerHeight + heroHeight + tableH

            val compositeBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(compositeBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Fondo neutro oscuro para contraste óptimo de cartas
            canvas.drawColor(android.graphics.Color.BLACK)

            // Dibujar zona comunitaria en la parte superior
            val centerOffsetX = ((targetWidth - centerWidth) / 2f).coerceAtLeast(0f)
            canvas.drawBitmap(centerRegion, centerOffsetX, 0f, paint)

            // Dibujar zona de cartas hero en la parte media
            val heroOffsetX = ((targetWidth - heroWidth) / 2f).coerceAtLeast(0f)
            canvas.drawBitmap(heroRegion, heroOffsetX, centerHeight.toFloat(), paint)

            // Dibujar panorama de mesa en la parte inferior para detección de Dealer y jugadores
            if (tableRegion != null) {
                val tableOffsetX = ((targetWidth - tableW) / 2f).coerceAtLeast(0f)
                canvas.drawBitmap(tableRegion, tableOffsetX, (centerHeight + heroHeight).toFloat(), paint)
                tableRegion.recycle()
            }

            // Liberar bitmaps intermedios
            centerRegion.recycle()
            heroRegion.recycle()

            Log.d(TAG, "Successfully cropped & assembled clean table regions: ${compositeBitmap.width}x${compositeBitmap.height}")
            compositeBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping table regions, returning original bitmap safely", e)
            source
        }
    }
}
