package com.scan2enter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scan2enter.overlay.OverlayService
import com.scan2enter.api.DueRetailApiTest
import com.scan2enter.ui.screens.CameraScreen
import com.scan2enter.ui.theme.Scan2EnterTheme
import com.scan2enter.viewmodel.MainViewModel
import com.scan2enter.ui.screens.HomeScreen
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("DueRetailApi", "MAIN ACTIVITY AVVIATA")
        DueRetailApiTest.run()
        Log.d("DueRetailApi", "TEST API LANCIATO")

        // Richiede il permesso Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (!Settings.canDrawOverlays(this)) {

                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )

                startActivity(intent)

            } else {

                startService(
                    Intent(this, OverlayService::class.java)
                )
            }

        } else {

            startService(
                Intent(this, OverlayService::class.java)
            )
        }

        enableEdgeToEdge()

        setContent {

            Scan2EnterTheme {

                HomeScreen()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("Scan2Enter", "MainActivity -> onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Scan2Enter", "MainActivity -> onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Scan2Enter", "MainActivity -> onDestroy")
    }
}