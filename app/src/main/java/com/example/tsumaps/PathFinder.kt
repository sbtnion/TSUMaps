package com.example.tsumaps

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

class PathFinder(private val grid: Array<IntArray>) {
    private val rows = grid.size
    private val cols = if (rows > 0) grid[0].size else 0

    fun findPathAStar(start: Pair<Int, Int>, end: Pair<Int, Int>): List<Pair<Int, Int>>? {
        data class Node(val r: Int, val c: Int, var g: Int, val h: Int, var parent: Node? = null) : Comparable<Node> {
            val f get() = g + h
            override fun compareTo(other: Node) = f.compareTo(other.f)
        }

        val openQueue = PriorityQueue<Node>()
        val visitedMap = mutableMapOf<Pair<Int, Int>, Int>()
        openQueue.add(Node(start.first, start.second, 0, abs(start.first - end.first) + abs(start.second - end.second)))

        while (openQueue.isNotEmpty()) {
            val current = openQueue.poll()!!
            if (current.r == end.first && current.c == end.second) {
                return generateSequence(current) { it.parent }.map { it.r to it.c }.toList().reversed()
            }
            if ((visitedMap[current.r to current.c] ?: Int.MAX_VALUE) <= current.g) continue
            visitedMap[current.r to current.c] = current.g

            for ((dr, dc) in listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
                val nr = current.r + dr; val nc = current.c + dc
                if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc] == 0) {
                    openQueue.add(Node(nr, nc, current.g + 1, abs(nr - end.first) + abs(nc - end.second), current))
                }
            }
        }
        return null
    }

    fun solveTSPWithAntColony(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val n = points.size
        if (n < 2) return emptyList()
        val distMatrix = Array(n) { DoubleArray(n) }
        val pathData = Array(n) { arrayOfNulls<List<Pair<Int, Int>>>(n) }

        for (i in 0 until n) for (j in i + 1 until n) {
            findPathAStar(points[i], points[j])?.let {
                distMatrix[i][j] = it.size.toDouble(); distMatrix[j][i] = it.size.toDouble()
                pathData[i][j] = it; pathData[j][i] = it.reversed()
            }
        }

        val pheromones = Array(n) { DoubleArray(n) { 1.0 } }
        var (bestOrder, bestDistance) = (0 until n).toList() to Double.MAX_VALUE

        repeat(30) {
            val ants = List(8) {
                val order = mutableListOf(0); val visited = mutableSetOf(0)
                while (visited.size < n) {
                    val i = order.last(); var totalProb = 0.0
                    val probs = DoubleArray(n) { j ->
                        if (j in visited) 0.0 else (pheromones[i][j].pow(1.0) * (1.0 / distMatrix[i][j]).pow(2.0)).also { totalProb += it }
                    }
                    if (totalProb == 0.0) break
                    var randomVal = Random.nextDouble() * totalProb
                    for (j in 0 until n) if (j !in visited) { randomVal -= probs[j]; if (randomVal <= 0) { order.add(j); visited.add(j); break } }
                }
                order
            }
            for (i in 0 until n) for (j in 0 until n) pheromones[i][j] *= 0.6
            for (ant in ants) {
                if (ant.size < n) continue
                val d = (0 until n - 1).sumOf { distMatrix[ant[it]][ant[it + 1]] } + distMatrix[ant.last()][ant[0]]
                if (d < bestDistance) { bestDistance = d; bestOrder = ant }
                for (k in 0 until n - 1) pheromones[ant[k]][ant[k + 1]] += 5.0 / d
            }
        }
        return bestOrder.indices.flatMap { pathData[bestOrder[it]][bestOrder[(it + 1) % n]] ?: emptyList() }
    }
}
