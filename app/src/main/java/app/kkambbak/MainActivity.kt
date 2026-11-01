package app.kkambbak

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kkambbak.ui.KkambbakApp

class MainActivity : ComponentActivity() {

    private val controller get() = (application as KkambbakApplication).controller

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[FUSED_LOCATION] == true) controller.onLocationPermissionGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = controller.state.collectAsStateWithLifecycle()
            KkambbakApp(state.value, controller::onAction)
        }
        val need = listOf(FUSED_LOCATION, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) permissions.launch(need.toTypedArray())
    }

    companion object {
        /** FusedLocation SDK 권한(protection level: dangerous) */
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}
