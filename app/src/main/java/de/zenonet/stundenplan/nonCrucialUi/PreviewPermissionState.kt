@file:OptIn(ExperimentalPermissionsApi::class)

package de.zenonet.stundenplan.nonCrucialUi

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.shouldShowRationale

class PreviewPermissionState : PermissionState {
    override val permission: String
        get() = "PreviewPermission"
    override var status: PermissionStatus by mutableStateOf(PermissionStatus.Granted)

    override fun launchPermissionRequest() {
    }

    private var launcher: ActivityResultLauncher<String>? = null

    internal fun refreshPermissionStatus() {
        status = PermissionStatus.Granted
    }

    private fun getPermissionStatus() {
        PermissionStatus.Granted
    }
}