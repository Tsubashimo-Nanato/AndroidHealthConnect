package com.example.healthconnectandroid.hc

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient

/**
 * Build an Intent to show the Health Connect permission sheet.
 * Works across client versions by using reflection; if unavailable,
 * falls back to App Settings.
 */
fun ComponentActivity.buildHcPermissionIntent(
    client: HealthConnectClient,
    permissions: Set<Any /* HealthPermission */>
): Intent {
    return try {
        val controller = client.permissionController
        val m = controller::class.java.getMethod(
            "createRequestPermissionIntent", Set::class.java
        )
        @Suppress("UNCHECKED_CAST")
        m.invoke(controller, permissions) as Intent
    } catch (_: Throwable) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }
}
