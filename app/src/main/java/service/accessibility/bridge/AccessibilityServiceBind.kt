package service.accessibility.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.runBlocking

class AccessibilityServiceBind : Service() {
    private val binder = object : IAccessibilityService.Stub() {
        @Throws(RemoteException::class)
        override fun getCurrentScreen(): ViewNode? {
            return AccessibilityServiceProvider.instance?.getCurrentScreen()
        }

        @Throws(RemoteException::class)
        override fun performClick(x: Int, y: Int, duration: Long): Boolean {
            return runBlocking {
                AccessibilityServiceProvider.instance?.performClick(x, y, duration) ?: false
            }
        }

        @Throws(RemoteException::class)
        override fun performTextType(text: String): Boolean {
            return runBlocking {
                AccessibilityServiceProvider.instance?.performTextType(text) ?: false
            }
        }

        @Throws(RemoteException::class)
        override fun performSwipe(finger: Finger): Boolean {
            return runBlocking {
                AccessibilityServiceProvider.instance?.performSwipe(finger) ?: false
            }
        }

        @Throws(RemoteException::class)
        override fun performMultiTouch(fingers: Array<Finger>): Boolean {
            return runBlocking {
                AccessibilityServiceProvider.instance?.performMultiTouch(fingers.asList()) ?: false
            }
        }

        @Throws(RemoteException::class)
        override fun performSystemAction(action: Int): Boolean {
            return runBlocking {
                AccessibilityServiceProvider.instance?.performSystemAction(action) ?: false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}
