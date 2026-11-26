package net.hordeslop.studio.domain

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherIconManager {
    private const val MANIFEST_NAMESPACE = "net.hordeslop.studio"

    fun componentClassName(choice: LauncherIconChoice): String =
        MANIFEST_NAMESPACE + choice.aliasClassSuffix

    fun applyLauncherIcon(context: Context, choice: LauncherIconChoice) {
        val pm = context.packageManager
        val pkg = context.packageName

        val targetComponent = ComponentName(pkg, componentClassName(choice))
        runCatching {
            if (pm.getComponentEnabledSetting(targetComponent) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }

        for (entry in LauncherIconChoice.entries) {
            if (entry == choice) continue
            val component = ComponentName(pkg, componentClassName(entry))
            runCatching {
                if (pm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }
}
