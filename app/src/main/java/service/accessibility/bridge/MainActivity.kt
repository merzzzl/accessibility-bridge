package service.accessibility.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var accessCheck: AccessCheck

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        accessCheck = AccessCheck(this)

        accessCheck.bindServices()

        if (!accessCheck.checkAccessibilityService()) {
            accessCheck.enableAccessibilityService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accessCheck.unbindServices()
    }
}
