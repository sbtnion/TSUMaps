package com.example.tsumaps

import java.util.PriorityQueue
import kotlin.math.abs

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
}
