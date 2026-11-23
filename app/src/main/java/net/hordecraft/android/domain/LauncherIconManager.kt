package net.hordecraft.android.domain

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherIconManager {
    fun applyLauncherIcon(context: Context, choice: LauncherIconChoice) {
        val pm = context.packageManager
        val pkg = context.packageName

        for (entry in LauncherIconChoice.entries) {
            val component = ComponentName(pkg, pkg + entry.aliasClassSuffix)
            val targetState = if (entry == choice) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                component,
                targetState,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
