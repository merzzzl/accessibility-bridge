package service.accessibility.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs


class AccessibilityServiceProvider : AccessibilityService() {
    private var currentRootNodes: List<AccessibilityNodeInfo> = emptyList()
    private val activeStrokes = mutableMapOf<Int, GestureDescription.StrokeDescription>()

    companion object {
        @Volatile
        private var _instance: AccessibilityServiceProvider? = null
        val instance: AccessibilityServiceProvider?
            get() = _instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        _instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (windows.size > 0) {
            windows.mapNotNull { it.root }.let { rootNodes ->
                currentRootNodes = rootNodes
            }
        } else if (rootInActiveWindow != null) {
            currentRootNodes = listOf(rootInActiveWindow)
        }
    }

    override fun onInterrupt() {}

    fun getCurrentScreen(): ViewNode {
        val realScreenSize = getRealScreenSize()

        return ViewNode(
            className = "android.widget.FrameLayout",
            text = "",
            resourceID = 0,
            uniqueID = "0",
            bounds = Rect(0, 0, realScreenSize.first, realScreenSize.second),
            children = currentRootNodes.mapIndexed { index, node ->
                buildViewHierarchy(node, index + 1)
            },
            ""
        )
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun buildViewHierarchy(node: AccessibilityNodeInfo, index: Int = 1, parentID: String = ""): ViewNode {
        val uniqueID = if (parentID.isEmpty()) "$index" else "$parentID:$index"

        val direction = getLayoutDirection(node)

        val childNodes = mutableListOf<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                childNodes.add(child)

                if (child.className?.contains("WebView") == true) {
                    child.refresh()
                    for (j in 0 until child.childCount) {
                        child.getChild(j)?.let { webChild ->
                            childNodes.add(webChild)
                        }
                    }
                }
            }
        }

        val sortedNodes = when (direction) {
            LayoutDirection.VERTICAL -> childNodes.sortedBy { bounds ->
                val rect = Rect()
                bounds.getBoundsInScreen(rect)
                rect.top
            }

            LayoutDirection.HORIZONTAL -> childNodes.sortedBy { bounds ->
                val rect = Rect()
                bounds.getBoundsInScreen(rect)
                rect.left
            }

            LayoutDirection.UNKNOWN -> childNodes
        }

        val children = sortedNodes.mapIndexed { i, childNode ->
            buildViewHierarchy(childNode, i + 1, uniqueID)
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        return ViewNode(
            packageName = node.packageName?.toString() ?: "",
            className = node.className?.toString() ?: "",
            text = node.text?.toString() ?: "",
            resourceID = index,
            uniqueID = uniqueID,
            bounds = bounds,
            children = children
        )
    }

    private fun getLayoutDirection(node: AccessibilityNodeInfo): LayoutDirection {
        if (node.childCount < 2) return LayoutDirection.UNKNOWN

        val childBounds = mutableListOf<Rect>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val bounds = Rect()
                child.getBoundsInScreen(bounds)
                childBounds.add(bounds)
            }
        }

        var horizontalCount = 0
        var verticalCount = 0

        for (i in 0 until childBounds.size - 1) {
            val current = childBounds[i]
            val next = childBounds[i + 1]

            if (abs(current.centerY() - next.centerY()) < 20) {
                horizontalCount++
            }
            if (abs(current.centerX() - next.centerX()) < 20) {
                verticalCount++
            }
        }

        return when {
            horizontalCount > verticalCount -> LayoutDirection.HORIZONTAL
            verticalCount > horizontalCount -> LayoutDirection.VERTICAL
            else -> LayoutDirection.UNKNOWN
        }
    }

    private enum class LayoutDirection {
        HORIZONTAL,
        VERTICAL,
        UNKNOWN
    }

    suspend fun performTextType(text: String): Boolean {
        if (text.isEmpty()) return false

        for (rootNode in currentRootNodes) {
            val focus = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue

            return suspendCoroutine { continuation ->
                val bundle = Bundle()
                bundle.putString(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )

                val success = focus.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    bundle
                )

                continuation.resume(success)
            }
        }

        return false
    }

    suspend fun performClick(x: Int, y: Int, duration: Long): Boolean {
        val gestureBuilder = GestureDescription.Builder()
        val path = Path()

        path.moveTo(x.toFloat(), y.toFloat())
        path.lineTo(x.toFloat(), y.toFloat())

        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            duration,
        )

        activeStrokes[0] = stroke

        gestureBuilder.addStroke(
            stroke
        )

        val gesture = gestureBuilder.build()

        return suspendCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }

            if (!dispatchGesture(gesture, callback, null)) {
                continuation.resume(false)
            }
        }
    }

    suspend fun performSwipe(finger: Finger): Boolean {
        val gestureBuilder = GestureDescription.Builder()
        val path = Path()

        path.moveTo(finger.x1.toFloat(), finger.y1.toFloat())
        path.lineTo(finger.x2.toFloat(), finger.y2.toFloat())

        val stroke = activeStrokes[finger.id]?.continueStroke(
            path,
            0,
            finger.duration,
            finger.keepDown
        ) ?: GestureDescription.StrokeDescription(
            path,
            0,
            finger.duration,
            finger.keepDown
        )

        if (finger.keepDown) {
            activeStrokes[finger.id] = stroke
        } else {
            activeStrokes.remove(finger.id)
        }

        gestureBuilder.addStroke(
            stroke
        )

        return suspendCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }

            if (!dispatchGesture(gestureBuilder.build(), callback, null)) {
                continuation.resume(false)
            }
        }
    }

    suspend fun performMultiTouch(fingers: List<Finger>): Boolean {
        val gestureBuilder = GestureDescription.Builder()

        for (finger in fingers) {
            val path = Path()

            path.moveTo(finger.x1.toFloat(), finger.y1.toFloat())
            path.lineTo(finger.x2.toFloat(), finger.y2.toFloat())

            val stroke = activeStrokes[finger.id]?.continueStroke(
                path,
                0,
                finger.duration,
                finger.keepDown
            ) ?: GestureDescription.StrokeDescription(
                path,
                0,
                finger.duration,
                finger.keepDown
            )

            activeStrokes[finger.id] = stroke

            gestureBuilder.addStroke(stroke)
        }

        return suspendCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }

            if (!dispatchGesture(gestureBuilder.build(), callback, null)) {
                continuation.resume(false)
            }
        }
    }

    suspend fun performSystemAction(action: Int): Boolean {
        return suspendCoroutine { continuation ->
            val success = when (action) {
                GLOBAL_ACTION_BACK,
                GLOBAL_ACTION_HOME,
                GLOBAL_ACTION_RECENTS,
                GLOBAL_ACTION_NOTIFICATIONS,
                GLOBAL_ACTION_QUICK_SETTINGS,
                GLOBAL_ACTION_POWER_DIALOG,
                GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN,
                GLOBAL_ACTION_LOCK_SCREEN,
                GLOBAL_ACTION_TAKE_SCREENSHOT,
                GLOBAL_ACTION_KEYCODE_HEADSETHOOK,
                GLOBAL_ACTION_ACCESSIBILITY_BUTTON,
                GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER,
                GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT,
                GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS,
                GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE,
                GLOBAL_ACTION_DPAD_UP,
                GLOBAL_ACTION_DPAD_DOWN,
                GLOBAL_ACTION_DPAD_LEFT,
                GLOBAL_ACTION_DPAD_RIGHT,
                GLOBAL_ACTION_DPAD_CENTER -> performGlobalAction(action)

                else -> false
            }
            continuation.resume(success)
        }
    }
}

