package com.example.tsumaps

import kotlin.math.hypot

class ClusterManager {
    fun performKMeansClustering(points: List<PointOfInterest>, k: Int) {
        if (points.size < k) return
        val centers = points.shuffled().take(k).map { it.x.toFloat() to it.y.toFloat() }.toMutableList()
        repeat(10) {
            points.forEach { p ->
                p.clusterIndex = centers.indices.minBy { hypot(p.x - centers[it].first, p.y - centers[it].second) }
            }
            points.groupBy { it.clusterIndex }.forEach { (i, group) ->
                centers[i] = group.map { it.x }.average().toFloat() to group.map { it.y }.average().toFloat()
            }
        }
    }
}
