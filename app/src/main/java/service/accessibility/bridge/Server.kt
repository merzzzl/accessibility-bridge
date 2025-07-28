package service.accessibility.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.core.app.NotificationCompat
import service.accessibility.bridge.grpc.ActionManager
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.okhttp.OkHttpServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService

class Server : Service() {
    private var server: Server? = null
    private var accessibilityService: IAccessibilityService? = null

    private val binder = object : IServerController.Stub() {
        @Throws(RemoteException::class)
        override fun isActive(): Boolean {
            return server?.let { !it.isShutdown } ?: false
        }
    }

    private val accessibilityServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            accessibilityService = IAccessibilityService.Stub.asInterface(service)
            Log.d("gRPC", "Accessibility service connected")
            startGrpcServer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            accessibilityService = null
        }
    }

    override fun onCreate() {
        super.onCreate()

        val intentAccessibilityService = Intent(this, AccessibilityServiceBind::class.java)
        bindService(intentAccessibilityService, accessibilityServiceConnection, Context.BIND_AUTO_CREATE)

        startForegroundService()
    }

    private fun startGrpcServer() {
        val service = accessibilityService
        if (service == null) {
            Log.d("gRPC", "Services are not ready")
            return
        }

        if (server?.isShutdown == false) {
            Log.d("gRPC", "Server is already running, shutting it down")
            server?.shutdown()
            server?.awaitTermination()
        }

        val credentials = InsecureServerCredentials.create()

        server = OkHttpServerBuilder.forPort(52000, credentials)
            .addService(ProtoReflectionService.newInstance())
            .addService(ActionManager(service))
            .build()
            .start()

        Log.d("gRPC", "Server started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundService() {
        val channel = NotificationChannel(
            "service.accessibility.bridge",
            "Accessibility Bridge Notifications",
            NotificationManager.IMPORTANCE_HIGH
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, "service.accessibility.bridge")
            .setContentTitle("Accessibility Bridge")
            .setContentText(this.getString(R.string.grpc_server))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()

        server?.shutdown()
        server?.awaitTermination()
        unbindService(accessibilityServiceConnection)

        Log.d("gRPC", "Server killed")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}
