package service.accessibility.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.provider.Settings

class AccessCheck(private val context: Context) {
    private var accessibilityService: IAccessibilityService? = null
    private var serverController: IServerController? = null

    private val accessibilityServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            accessibilityService = IAccessibilityService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            accessibilityService = null
        }
    }

    private val serverServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serverController = IServerController.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serverController = null
        }
    }

    fun bindServices() {
        val intent = Intent(context, AccessibilityServiceBind::class.java)

        context.bindService(intent, accessibilityServiceConnection, Context.BIND_AUTO_CREATE)

        Intent(context, Server::class.java).also { serverIntent ->
            context.startForegroundService(serverIntent)
            context.bindService(serverIntent, serverServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindServices() {
        context.unbindService(accessibilityServiceConnection)
    }

    fun enableAccessibilityService() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val component = ComponentName(context.packageName, AccessibilityServiceProvider::class.java.name).flattenToString()
        intent.putExtra(":settings:fragment_args_key", component)

        context.startActivity(intent)
    }

    fun checkAccessibilityService(): Boolean {
        val serviceString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedService = "${context.packageName}/${AccessibilityServiceProvider::class.java.canonicalName}"
        return serviceString.split(":").any { it == expectedService }
    }

    fun checkServerController(): Boolean {
        return serverController?.isActive ?: false
    }
}