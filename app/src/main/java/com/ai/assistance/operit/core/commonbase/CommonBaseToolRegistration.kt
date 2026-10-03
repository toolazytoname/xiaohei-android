package com.ai.assistance.operit.core.commonbase

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardIntentToolExecutor
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardSystemOperationTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Common-base start_app must use [StandardSystemOperationTools], not
 * [com.ai.assistance.operit.core.tools.defaultTool.ToolGetter.getSystemOperationTools], which
 * switches to Root/Debugger implementations from the user permission-level preference.
 */
object CommonBaseToolBacking {
    fun systemOperationToolsType(): Class<out StandardSystemOperationTools> {
        return StandardSystemOperationTools::class.java
    }

    fun systemOperationTools(context: Context): StandardSystemOperationTools {
        return StandardSystemOperationTools(context)
    }

    fun intentToolExecutor(context: Context): StandardIntentToolExecutor {
        return StandardIntentToolExecutor(context)
    }
}

fun registerCommonBaseTools(handler: AIToolHandler, context: Context) {
    fun s(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    val systemOperationTools = CommonBaseToolBacking.systemOperationTools(context)
    val intentTool = CommonBaseToolBacking.intentToolExecutor(context)

    handler.registerTool(
        name = CommonBaseToolCatalog.EXECUTE_INTENT,
        descriptionGenerator = { tool ->
            val action = tool.parameters.find { it.name == "action" }?.value
            val packageName = tool.parameters.find { it.name == "package" }?.value
            val component = tool.parameters.find { it.name == "component" }?.value
            val type = tool.parameters.find { it.name == "type" }?.value ?: "activity"
            when {
                !component.isNullOrBlank() ->
                    s(R.string.toolreg_execute_intent_component_desc, component, type)
                !packageName.isNullOrBlank() && !action.isNullOrBlank() ->
                    s(
                        R.string.toolreg_execute_intent_action_package_desc,
                        action,
                        packageName,
                        type
                    )
                !action.isNullOrBlank() -> s(R.string.toolreg_execute_intent_action_desc, action, type)
                else -> s(R.string.toolreg_execute_android_intent_desc, type)
            }
        },
        executor = { tool ->
            runBlocking(Dispatchers.IO) { intentTool.invoke(tool) }
        }
    )

    handler.registerTool(
        name = CommonBaseToolCatalog.START_APP,
        descriptionGenerator = { tool ->
            val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
            s(R.string.toolreg_start_app_desc, packageName)
        },
        executor = { tool ->
            runBlocking(Dispatchers.IO) { systemOperationTools.startApp(tool) }
        }
    )

    if (!CommonBaseProfile.enhancedDevice) {
        return
    }

    val deviceTools = commonBaseDeviceToolExecutor(context)
    handler.registerTool(
        name = CommonBaseToolCatalog.GET_DEVICE_STATUS,
        descriptionGenerator = {
            CommonBaseToolCatalog.permissionDescription(CommonBaseToolCatalog.GET_DEVICE_STATUS)
        },
        executor = { tool -> deviceTools.getDeviceStatus(tool) }
    )
    handler.registerTool(
        name = CommonBaseToolCatalog.SET_MEDIA_VOLUME,
        descriptionGenerator = { tool ->
            val percent = tool.parameters.find { it.name == CommonBaseToolCatalog.PERCENT }?.value
            CommonBaseToolCatalog.permissionDescription(
                CommonBaseToolCatalog.SET_MEDIA_VOLUME,
                percent = percent
            )
        },
        executor = { tool -> deviceTools.setMediaVolume(tool) }
    )
}
