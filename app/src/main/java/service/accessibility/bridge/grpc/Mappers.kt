package service.accessibility.bridge.grpc

import service.accessibility.bridge.ActionManager
import service.accessibility.bridge.ViewNode

fun prepareScreenView(input: ViewNode?): ActionManager.ScreenView {
    val builder = ActionManager.ScreenView.newBuilder()
    val boundsBuilder = ActionManager.ScreenView.Bounds.newBuilder()

    if (input == null) {
        return builder.build()
    }

    boundsBuilder.setLeft(input.bounds.left)
    boundsBuilder.setRight(input.bounds.right)
    boundsBuilder.setTop(input.bounds.top)
    boundsBuilder.setBottom(input.bounds.bottom)

    builder.setBounds(boundsBuilder.build())
    builder.setClassName(input.className)
    builder.setText(input.text)
    builder.setId(input.resourceID)
    builder.setUniqueId(input.uniqueID)

    input.children.forEach {
        val children = prepareScreenView(it)
        builder.addChildren(children)
    }

    return builder.build()
}
