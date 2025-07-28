package service.accessibility.bridge.grpc

import android.graphics.Rect
import com.google.protobuf.Empty
import service.accessibility.bridge.ActionManager
import service.accessibility.bridge.ActionManagerGrpc
import service.accessibility.bridge.Finger
import service.accessibility.bridge.IAccessibilityService
import service.accessibility.bridge.ViewNode
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.util.concurrent.CancellationException

private const val DEFAULT_CLICK_DURATION = 100L

class ActionManager(private val accessibilityService: IAccessibilityService) : ActionManagerGrpc.ActionManagerImplBase() {

    override fun screenDump(request: Empty, responseObserver: StreamObserver<ActionManager.ScreenView>) {
        val view = requireScreen(responseObserver)
        responseObserver.onNext(prepareScreenView(view))
        responseObserver.onCompleted()
    }

    override fun performClick(request: ActionManager.ActionClick, responseObserver: StreamObserver<Empty>) {
        val view = requireScreen(responseObserver)

        val selectedView: ViewNode = when {
            request.hasClickElement() -> findViewBySelector(request.clickElement, view)
                ?: run {
                    respondError(responseObserver, Status.NOT_FOUND, "View not found by selector")
                }

            request.hasClickPoint() -> ViewNode(
                "", "", 0, "",
                Rect(
                    request.clickPoint.x,
                    request.clickPoint.y,
                    request.clickPoint.x + 1,
                    request.clickPoint.y + 1
                ),
                emptyList(), ""
            )

            else -> run {
                respondError(responseObserver, Status.INVALID_ARGUMENT, "Point not declared")
            }
        }

        val duration = if (request.duration > 0) request.duration.toLong() else DEFAULT_CLICK_DURATION

        if (!accessibilityService.performClick(
                selectedView.bounds.centerX(),
                selectedView.bounds.centerY(),
                duration
            )
        ) {
            respondError(responseObserver, Status.INTERNAL, "Action not completed")
        }

        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun typeText(request: ActionManager.ActionTypeText, responseObserver: StreamObserver<Empty>) {
        val view = requireScreen(responseObserver)

        val selectedView = findViewBySelector(request.selector, view)
            ?: run {
                respondError(responseObserver, Status.NOT_FOUND, "View not found by selector")
            }

        if (!accessibilityService.performClick(
                selectedView.bounds.centerX(),
                selectedView.bounds.centerY(),
                DEFAULT_CLICK_DURATION
            )
        ) {
            respondError(responseObserver, Status.INTERNAL, "Click before typing failed")
        }

        if (!accessibilityService.performTextType(request.text)) {
            respondError(responseObserver, Status.INTERNAL, "Text input failed")
        }

        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun performMultiTouch(request: ActionManager.ActionMultiTouch, responseObserver: StreamObserver<Empty>) {
        val fingers: Array<Finger> = arrayOf()

        for (fingerElement in request.fingerList) {
            val (x1, y1) = resolveFingerPoint(fingerElement, true, responseObserver)
            val (x2, y2) = resolveFingerPoint(fingerElement, false, responseObserver)

            val finger = Finger(
                fingerElement.fingerId,
                x1, y1, x2, y2,
                fingerElement.duration.toLong(),
                fingerElement.keepDown
            )

            fingers.plus(finger)
        }

        if (!accessibilityService.performMultiTouch(fingers)) {
            respondError(responseObserver, Status.INTERNAL, "MultiTouch failed")
        }

        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun performSwipe(request: ActionManager.ActionSwipe, responseObserver: StreamObserver<Empty>) {
        val (x1, y1) = resolveFingerPoint(request.finger, true, responseObserver)
        val (x2, y2) = resolveFingerPoint(request.finger, false, responseObserver)

        val finger = Finger(
            request.finger.fingerId,
            x1, y1, x2, y2,
            request.finger.duration.toLong(),
            request.finger.keepDown
        )

        if (!accessibilityService.performSwipe(finger)) {
            respondError(responseObserver, Status.INTERNAL, "Swipe failed")
        }

        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun performAction(request: ActionManager.ActionKey, responseObserver: StreamObserver<Empty>) {
        if (!accessibilityService.performSystemAction(request.keyValue)) {
            respondError(responseObserver, Status.INTERNAL, "System action failed")
        }

        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    private fun requireScreen(responseObserver: StreamObserver<*>): ViewNode {
        return accessibilityService.getCurrentScreen()
            ?: respondError(responseObserver, Status.INTERNAL, "Cannot get current screen")
    }

    private fun <T> respondError(observer: StreamObserver<T>, status: Status, message: String): Nothing {
        observer.onError(status.withDescription(message).asRuntimeException())
        throw CancellationException(message)
    }

    private fun resolveFingerPoint(
        finger: ActionManager.Finger,
        isStart: Boolean,
        responseObserver: StreamObserver<*>
    ): Pair<Int, Int> {
        return when {
            isStart && finger.hasStartPoint() -> finger.startPoint.x to finger.startPoint.y
            !isStart && finger.hasEndPoint() -> finger.endPoint.x to finger.endPoint.y

            isStart && finger.hasStartElement() -> {
                val view = requireScreen(responseObserver)
                val node = findViewBySelector(finger.startElement, view)
                    ?: respondError(responseObserver, Status.NOT_FOUND, "Start view not found")
                node.bounds.centerX() to node.bounds.centerY()
            }

            !isStart && finger.hasEndElement() -> {
                val view = requireScreen(responseObserver)
                val node = findViewBySelector(finger.endElement, view)
                    ?: respondError(responseObserver, Status.NOT_FOUND, "End view not found")
                node.bounds.centerX() to node.bounds.centerY()
            }

            else -> respondError(responseObserver, Status.INVALID_ARGUMENT, if (isStart) "Start point missing" else "End point missing")
        }
    }
}
