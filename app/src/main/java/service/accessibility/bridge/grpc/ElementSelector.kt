package service.accessibility.bridge.grpc

import service.accessibility.bridge.ActionManager
import service.accessibility.bridge.ViewNode

fun findViewBySelector(selector: ActionManager.ElementSelector, root: ViewNode): ViewNode? {
    val results = mutableListOf<ViewNode>()

    if (selector.uniqueId.isNotEmpty()) {
        findViewByUniqueId(selector.uniqueId, root)?.let { results.add(it) } ?: return null
    }

    if (selector.pathCount > 0) {
        findViewByPath(selector.pathList, root)?.let { results.add(it) } ?: return null
    }

    if (selector.regex.isNotEmpty()) {
        findViewByRegex(selector.regex, root)?.let { results.add(it) } ?: return null
    }

    if (results.isEmpty()) {
        return null
    }

    val firstUniqueId = results.first().uniqueID
    val allMatch = results.all { it.uniqueID == firstUniqueId }

    return if (allMatch) results.first() else null
}

fun findViewByUniqueId(uniqueId: String, root: ViewNode): ViewNode? {
    if (root.uniqueID == uniqueId) {
        return root
    }

    root.children.forEach { child ->
        val result = findViewByUniqueId(uniqueId, child)
        if (result != null) {
            return result
        }
    }

    return null
}

fun findViewByPath(path: List<Int>, root: ViewNode): ViewNode? {
    if (path.isEmpty() || path[0] != root.resourceID) {
        return null
    }

    var current = root

    for (i in 1 until path.size) {
        val child = current.children.find { it.resourceID == path[i] }
        if (child == null) {
            return null
        }
        current = child
    }

    return current
}

fun findViewByRegex(regex: String, root: ViewNode): ViewNode? {
    val pattern = regex.toRegex()

    fun traverse(node: ViewNode): ViewNode? {
        if (node.text.isNotEmpty() && pattern.matches(node.text)) {
            return node
        }

        node.children.forEach { child ->
            val result = traverse(child)
            if (result != null) {
                return result
            }
        }

        return null
    }

    return traverse(root)
}
