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

// Fine and coarse are requested together (required on Android 12+); either one counts as allowed,
// since people can still choose "Approximate" in the prompt.
private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
private const val PREFS = "location_permission"
private const val KEY_ASKED = "asked"
private const val KEY_ASKED_PRECISE = "asked_precise"

@Stable
class LocationPermissionState internal constructor(
    private val context: Context,
    private val launch: () -> Unit,
    private val shouldShowRationale: () -> Boolean,
) {
    var granted by mutableStateOf(isGranted())
        internal set

    /** False when the user picked "Approximate". */
    var precise by mutableStateOf(isPrecise())
        internal set

    /** Whether the app has ever shown the system prompt (persisted, so it isn't shown again at every launch). */
    var askedBefore by mutableStateOf(prefs().getBoolean(KEY_ASKED, false))
        private set

    /**
     * Whether the user has seen a prompt that offered precise location. Versions before 0.9 asked for
     * approximate only, so testers who allowed that are asked once more.
     */
    var askedPrecise by mutableStateOf(prefs().getBoolean(KEY_ASKED_PRECISE, false))
        private set

    /**
     * True once Android will no longer show the prompt (declined twice, or "Don't ask again"),
     * so the only way to turn location on is the app's system settings page.
     */
    val needsSettings: Boolean get() = !granted && askedBefore && !shouldShowRationale()

    fun request() {
        prefs().edit { putBoolean(KEY_ASKED, true).putBoolean(KEY_ASKED_PRECISE, true) }
        askedBefore = true
        askedPrecise = true
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
        precise = isPrecise()
    }

    private fun isGranted() = PERMISSIONS.any(::has)
    private fun isPrecise() = has(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Location permission (precise requested, approximate accepted), re-checked whenever the screen resumes (e.g. back from Settings). */
@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var holder: LocationPermissionState? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        holder?.refresh()
    }
    val state = remember {
        LocationPermissionState(
            context = context,
            launch = { launcher.launch(PERMISSIONS) },
            shouldShowRationale = { PERMISSIONS.any { activity?.shouldShowRequestPermissionRationale(it) == true } },
        )
    }
    holder = state
    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose {}
    }
    return state
}
