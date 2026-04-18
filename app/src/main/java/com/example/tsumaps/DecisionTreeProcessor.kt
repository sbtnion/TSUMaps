package com.example.tsumaps

import kotlin.math.log2

data class DecisionNode(
    val attribute: String? = null,
    val children: Map<String, DecisionNode> = emptyMap(),
    val result: String? = null
)

class DecisionTreeProcessor {

    fun train(csv: String): DecisionTreeModel {
        val data = parseCsv(csv)
        val targetColumn = "recommended_place"

        if (data.isEmpty()) return DecisionTreeModel(DecisionNode(result = "Ошибка: Нет данных"))

        val header = data.first().keys.toList()

        fun build(currentData: List<Map<String, String>>, attrs: List<String>): DecisionNode {
            val targets = currentData.mapNotNull { it[targetColumn] }

            if (targets.distinct().size <= 1) {
                return DecisionNode(result = targets.firstOrNull() ?: "Неизвестно")
            }

            if (attrs.isEmpty()) {
                val mostFrequent = targets.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                return DecisionNode(result = mostFrequent ?: "Неизвестно")
            }

            val bestAttr = attrs.minByOrNull { attr ->
                val subsets = currentData.groupBy { it[attr] ?: "" }
                subsets.values.sumOf { subset ->
                    val p = subset.size.toDouble() / currentData.size
                    val counts = subset.mapNotNull { it[targetColumn] }.groupingBy { it }.eachCount().values
                    val entropy = counts.sumOf { count ->
                        val prob = count.toDouble() / subset.size
                        -prob * log2(prob)
                    }
                    p * entropy
                }
            } ?: return DecisionNode(result = targets.firstOrNull())

            val branches = currentData.groupBy { it[bestAttr] ?: "N/A" }.mapValues { entry ->
                build(entry.value, attrs.filter { it != bestAttr })
            }

            return DecisionNode(attribute = bestAttr, children = branches)
        }

        val root = build(data, header.filter { it != targetColumn })
        return DecisionTreeModel(root)
    }

    private fun parseCsv(csv: String): List<Map<String, String>> {
        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.size < 2) return emptyList()

        val header = lines.first().split(",").map { it.trim().removeSurrounding("\"") }

        return lines.drop(1).mapNotNull { line ->
            val values = line.split(",").map { it.trim().removeSurrounding("\"") }
            if (values.size == header.size) {
                header.zip(values).toMap()
            } else null
        }
    }
}

class DecisionTreeModel(val root: DecisionNode) {
    fun predict(input: Map<String, String>): Pair<String, List<String>> {
        val stepPath = mutableListOf<String>()
        var currentNode: DecisionNode? = root

        while (currentNode?.result == null && currentNode != null) {
            val attrName = currentNode.attribute ?: break
            val userVal = input[attrName] ?: "unknown"
            stepPath.add("$attrName:$userVal")
            currentNode = currentNode.children[userVal] ?: currentNode.children.values.firstOrNull()
        }

        return (currentNode?.result ?: "Не найдено") to stepPath
    }
}
