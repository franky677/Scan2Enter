package com.scan2enter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scan2enter.ui.screens.CameraScreen
import com.scan2enter.ui.theme.Scan2EnterTheme
import com.scan2enter.viewmodel.MainViewModel

class ScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Scan2EnterTheme {
                val vm: MainViewModel = viewModel()

                CameraScreen(
                    viewModel = vm
                )
            }
        }
    }
}