package com.grocerylist.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect

private const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
private const val PREFS = "location_permission"
private const val KEY_ASKED = "asked"

@Stable
class LocationPermissionState internal constructor(
    private val context: Context,
    private val launch: () -> Unit,
    private val shouldShowRationale: () -> Boolean,
) {
    var granted by mutableStateOf(isGranted())
        internal set

    /** Whether the app has ever shown the system prompt (persisted, so it isn't shown again at every launch). */
    var askedBefore by mutableStateOf(prefs().getBoolean(KEY_ASKED, false))
        private set

    /**
     * True once Android will no longer show the prompt (declined twice, or "Don't ask again"),
     * so the only way to turn location on is the app's system settings page.
     */
    val needsSettings: Boolean get() = !granted && askedBefore && !shouldShowRationale()

    fun request() {
        prefs().edit { putBoolean(KEY_ASKED, true) }
        askedBefore = true
        launch()
    }

    fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal fun refresh() {
        granted = isGranted()
    }

    private fun isGranted() = ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Approximate-location permission, re-checked whenever the screen resumes (e.g. back from Settings). */
@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var holder: LocationPermissionState? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        holder?.refresh()
    }
    val state = remember {
        LocationPermissionState(
            context = context,
            launch = { launcher.launch(PERMISSION) },
            shouldShowRationale = { activity?.shouldShowRequestPermissionRationale(PERMISSION) == true },
        )
    }
    holder = state
    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose {}
    }
    return state
}
