package com.example.tsumaps

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

object MapRenderer {
    fun DrawScope.drawPathLine(
        points: List<Pair<Int, Int>>,
        ratio: Float,
        color: Color,
        width: Float
    ) {
        for (i in 0 until points.size - 1) {
            drawLine(
                color = color,
                start = Offset(points[i].second * ratio, points[i].first * ratio),
                end = Offset(points[i+1].second * ratio, points[i+1].first * ratio),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
    }

    fun DrawScope.drawVoronoiZones(
        poiList: List<PointOfInterest>,
        ratio: Float,
        colorProvider: (Int) -> Color,
        gridHeight: Int,
        gridWidth: Int
    ) {
        val centroids = poiList.groupBy { it.clusterIndex }
            .filter { it.key != -1 }
            .mapValues { entry ->
                Offset(
                    entry.value.map { it.x }.average().toFloat() * ratio,
                    entry.value.map { it.y }.average().toFloat() * ratio
                )
            }
        val step = 20f
        for (y in 0 until (gridHeight * ratio / step).toInt()) {
            for (x in 0 until (gridWidth * ratio / step).toInt()) {
                val cur = Offset(x * step, y * step)
                centroids.minByOrNull { (it.value - cur).getDistanceSquared() }?.key?.let {
                    drawRect(colorProvider(it).copy(alpha = 0.4f), cur, Size(step, step))
                }
            }
        }
    }
}
