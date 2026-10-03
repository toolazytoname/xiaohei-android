package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonBaseEnhancedProfileTest {

    @Test
    fun enhancedDevice_onlyLegalCommonNonStoreCombinationIsOpen() {
        assertTrue(
            CommonBaseProfile.resolveEnhancedDevice(
                commonBase = true,
                commonStore = false,
                commonEnhanced = true
            )
        )
        assertFalse(
            CommonBaseProfile.resolveEnhancedDevice(
                commonBase = false,
                commonStore = false,
                commonEnhanced = true
            )
        )
        assertFalse(
            CommonBaseProfile.resolveEnhancedDevice(
                commonBase = true,
                commonStore = true,
                commonEnhanced = true
            )
        )
        assertFalse(
            CommonBaseProfile.resolveEnhancedDevice(
                commonBase = true,
                commonStore = false,
                commonEnhanced = false
            )
        )
        assertFalse(
            CommonBaseProfile.resolveEnhancedDevice(
                commonBase = false,
                commonStore = true,
                commonEnhanced = true
            )
        )
    }

    @Test
    fun storeCatalog_excludesDeviceTools_enhancedCatalog_includesThem() {
        val store = CommonBaseToolCatalog.toolNames(enhancedDevice = false)
        val enhanced = CommonBaseToolCatalog.toolNames(enhancedDevice = true)
        assertEquals(
            linkedSetOf(CommonBaseToolCatalog.START_APP, CommonBaseToolCatalog.EXECUTE_INTENT),
            store
        )
        assertFalse(store.contains(CommonBaseToolCatalog.GET_DEVICE_STATUS))
        assertFalse(store.contains(CommonBaseToolCatalog.SET_MEDIA_VOLUME))
        assertTrue(enhanced.containsAll(store))
        assertTrue(enhanced.contains(CommonBaseToolCatalog.GET_DEVICE_STATUS))
        assertTrue(enhanced.contains(CommonBaseToolCatalog.SET_MEDIA_VOLUME))
        assertEquals(store.size + 2, enhanced.size)
    }

    @Test
    fun storePrompts_doNotNameDeviceTools_enhancedPrompts_do() {
        val storeNames =
            CommonBaseToolCatalog.promptCategories(useEnglish = true, enhancedDevice = false)
                .flatMap { category -> category.tools.map { it.name } }
                .toSet()
        val enhancedNames =
            CommonBaseToolCatalog.promptCategories(useEnglish = false, enhancedDevice = true)
                .flatMap { category -> category.tools.map { it.name } }
                .toSet()
        assertEquals(CommonBaseToolCatalog.storeToolNames, storeNames)
        assertEquals(CommonBaseToolCatalog.toolNames(enhancedDevice = true), enhancedNames)
        assertFalse(
            CommonBaseToolCatalog.usageGuidelines(useEnglish = true, enhancedDevice = false)
                .contains(CommonBaseToolCatalog.GET_DEVICE_STATUS)
        )
        assertTrue(
            CommonBaseToolCatalog.usageGuidelines(useEnglish = true, enhancedDevice = true)
                .contains(CommonBaseToolCatalog.SET_MEDIA_VOLUME)
        )
    }

    @Test
    fun forceExplicitConfirmation_matchesPromisedCatalogPerProfile() {
        assertTrue(
            CommonBaseToolCatalog.forceExplicitConfirmation(
                CommonBaseToolCatalog.START_APP,
                enabled = true,
                enhancedDevice = false
            )
        )
        assertFalse(
            CommonBaseToolCatalog.forceExplicitConfirmation(
                CommonBaseToolCatalog.GET_DEVICE_STATUS,
                enabled = true,
                enhancedDevice = false
            )
        )
        assertTrue(
            CommonBaseToolCatalog.forceExplicitConfirmation(
                CommonBaseToolCatalog.GET_DEVICE_STATUS,
                enabled = true,
                enhancedDevice = true
            )
        )
        assertTrue(
            CommonBaseToolCatalog.forceExplicitConfirmation(
                CommonBaseToolCatalog.SET_MEDIA_VOLUME,
                enabled = true,
                enhancedDevice = true
            )
        )
        assertFalse(
            CommonBaseToolCatalog.forceExplicitConfirmation(
                CommonBaseToolCatalog.SET_MEDIA_VOLUME,
                enabled = false,
                enhancedDevice = true
            )
        )
        assertFalse(
            CommonBaseToolCatalog.forceExplicitConfirmation(
                "execute_shell",
                enabled = true,
                enhancedDevice = true
            )
        )
    }
}

class CommonBaseEnhancedPolicyTest {

    @Test
    fun storeProfile_rejectsDirectDeviceToolCallsEvenWithConfirmation() {
        assertDenied(
            tool(CommonBaseToolCatalog.GET_DEVICE_STATUS),
            enhancedDevice = false
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "40"),
            enhancedDevice = false
        )
        val denied =
            CommonBaseCapabilityPolicy.evaluateExecution(
                enabled = true,
                request =
                    CommonBaseCapabilityRequest(
                        toolName = CommonBaseToolCatalog.GET_DEVICE_STATUS,
                        confirmation = CommonBaseConfirmation.ExplicitUserGrant,
                        enhancedDevice = false
                    )
            )
        assertTrue(denied is CommonBaseDecision.Deny)
        val reason = (denied as CommonBaseDecision.Deny).reason.lowercase()
        assertTrue(reason.contains("get_device_status") || reason.contains("not available"))
        assertFalse(reason.contains("serial"))
    }

    @Test
    fun storeProfile_stillAllowsConfirmedSettings() {
        assertAllowed(
            tool(
                CommonBaseToolCatalog.START_APP,
                "package_name" to CommonBaseToolCatalog.SETTINGS_PACKAGE
            ),
            enhancedDevice = false
        )
    }

    @Test
    fun enhancedProfile_deviceTools_requireExplicitConfirmation() {
        assertDenied(
            tool(CommonBaseToolCatalog.GET_DEVICE_STATUS),
            confirmation = CommonBaseConfirmation.None,
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "20"),
            confirmation = CommonBaseConfirmation.None,
            enhancedDevice = true
        )
        assertAllowed(tool(CommonBaseToolCatalog.GET_DEVICE_STATUS), enhancedDevice = true)
        assertAllowed(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "0"),
            enhancedDevice = true
        )
        assertAllowed(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "100"),
            enhancedDevice = true
        )
    }

    @Test
    fun enhancedProfile_rejectsUnknownDuplicateEmptyAndIllegalPercent() {
        assertDenied(
            tool(CommonBaseToolCatalog.GET_DEVICE_STATUS, "foo" to "1"),
            enhancedDevice = true
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.SET_MEDIA_VOLUME,
                CommonBaseToolCatalog.PERCENT to "40",
                CommonBaseToolCatalog.PERCENT to "50"
            ),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to ""),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to " 40"),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "01"),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "101"),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "-1"),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "50.0"),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "+50"),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "50%"),
            enhancedDevice = true
        )
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, "volume" to "40"),
            enhancedDevice = true
        )
        assertDenied(tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME), enhancedDevice = true)
        assertDenied(
            tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "40", "stream" to "3"),
            enhancedDevice = true
        )
        val catalog =
            CommonBaseCapabilityPolicy.evaluateCatalog(
                enabled = true,
                toolName = "execute_shell",
                enhancedDevice = true
            )
        assertTrue(catalog is CommonBaseDecision.Deny)
    }

    @Test
    fun parsePercent_acceptsCanonicalIntegersOnly() {
        assertEquals(0, CommonBaseDeviceVolume.parsePercent("0"))
        assertEquals(40, CommonBaseDeviceVolume.parsePercent("40"))
        assertEquals(100, CommonBaseDeviceVolume.parsePercent("100"))
        assertNull(CommonBaseDeviceVolume.parsePercent(""))
        assertNull(CommonBaseDeviceVolume.parsePercent("00"))
        assertNull(CommonBaseDeviceVolume.parsePercent("010"))
        assertNull(CommonBaseDeviceVolume.parsePercent("1000"))
        assertNull(CommonBaseDeviceVolume.parsePercent("0x10"))
        assertNull(CommonBaseDeviceVolume.parsePercent("40 "))
        assertNull(CommonBaseDeviceVolume.parsePercent("40\n"))
    }

    @Test
    fun enhancedCharacterCard_canExposeDeviceTools_storeCardCannot() {
        val storeAccess =
            CommonBaseCharacterCardToolAccess.resolve(
                customEnabled = false,
                allowedBuiltinTools = emptyList(),
                catalogToolNames = CommonBaseToolCatalog.toolNames(enhancedDevice = false)
            )
        assertFalse(
            storeAccess.effectiveBuiltinToolVisibility.containsKey(
                CommonBaseToolCatalog.GET_DEVICE_STATUS
            )
        )
        val enhancedAccess =
            CommonBaseCharacterCardToolAccess.resolve(
                customEnabled = true,
                allowedBuiltinTools =
                    listOf(
                        CommonBaseToolCatalog.START_APP,
                        CommonBaseToolCatalog.GET_DEVICE_STATUS
                    ),
                catalogToolNames = CommonBaseToolCatalog.toolNames(enhancedDevice = true)
            )
        assertEquals(
            true,
            enhancedAccess.effectiveBuiltinToolVisibility[CommonBaseToolCatalog.GET_DEVICE_STATUS]
        )
        assertEquals(
            false,
            enhancedAccess.effectiveBuiltinToolVisibility[CommonBaseToolCatalog.SET_MEDIA_VOLUME]
        )
        assertFalse(
            enhancedAccess.effectiveBuiltinToolVisibility.containsKey("execute_shell")
        )
    }

    private fun assertAllowed(
        tool: AITool,
        confirmation: CommonBaseConfirmation = CommonBaseConfirmation.ExplicitUserGrant,
        enhancedDevice: Boolean
    ) {
        assertNull(
            CommonBaseExecutionGuard.resultFor(
                tool = tool,
                confirmation = confirmation,
                enabled = true,
                enhancedDevice = enhancedDevice
            )
        )
    }

    private fun assertDenied(
        tool: AITool,
        confirmation: CommonBaseConfirmation = CommonBaseConfirmation.ExplicitUserGrant,
        enhancedDevice: Boolean
    ) {
        val denied =
            CommonBaseExecutionGuard.resultFor(
                tool = tool,
                confirmation = confirmation,
                enabled = true,
                enhancedDevice = enhancedDevice
            )
        assertNotNull(denied)
        assertFalse(denied!!.success)
        assertFalse(denied.error.isNullOrBlank())
    }
}

class CommonBaseDeviceVolumeExecutorTest {

    @Test
    fun applyPercent_quantizesAndSucceedsOnlyWhenReadbackMatchesTargetIndex() {
        val port = FakeVolumePort(max = 15, current = 3)
        val change = CommonBaseDeviceVolume.applyPercent(37, port)
        val applied = change as CommonBaseVolumeChange.Applied
        assertEquals(6, applied.targetIndex)
        assertEquals(6, applied.actualIndex)
        assertEquals(6, port.lastSet)
        assertEquals(40, applied.actualPercent)
        assertEquals(3, applied.streamIndex)
        assertEquals(37, applied.requestedPercent)
        assertTrue(applied.actualPercent != applied.requestedPercent)
    }

    @Test
    fun applyPercent_readbackMismatch_isNotSuccess() {
        val port = FakeVolumePort(max = 15, current = 3, stickyReadback = 3)
        val change = CommonBaseDeviceVolume.applyPercent(37, port)
        val missed = change as CommonBaseVolumeChange.NotApplied
        assertEquals(6, missed.targetIndex)
        assertEquals(3, missed.actualIndex)
        assertEquals(6, port.lastSet)
        assertTrue(missed.reason.contains("readback"))
    }

    @Test
    fun applyPercent_setException_failsOnceWithoutRetryOrShell() {
        val port =
            FakeVolumePort(
                max = 15,
                current = 3,
                failSet = IllegalStateException("stream busy")
            )
        val change = CommonBaseDeviceVolume.applyPercent(50, port)
        val missed = change as CommonBaseVolumeChange.NotApplied
        assertEquals(1, port.setCount)
        assertNull(port.lastSet)
        assertTrue(missed.reason.contains("setStreamVolume"))
        assertFalse(missed.reason.contains("shell"))
        assertFalse(missed.reason.contains("adb"))
    }

    @Test
    fun applyPercent_maxUnavailableOrZero_fails() {
        val zeroMax = CommonBaseDeviceVolume.applyPercent(40, FakeVolumePort(max = 0, current = 0))
        assertTrue(zeroMax is CommonBaseVolumeChange.NotApplied)
        val boom =
            CommonBaseDeviceVolume.applyPercent(
                40,
                FakeVolumePort(failMax = IllegalStateException("no audio"))
            )
        assertTrue(boom is CommonBaseVolumeChange.NotApplied)
    }

    @Test
    fun executor_getDeviceStatus_returnsOnlyAllowListedKeys() {
        val executor =
            CommonBaseDeviceToolExecutor(
                statusPort =
                    FakeStatusPort(
                        batteryPercent = 87,
                        charging = true,
                        screenInteractive = true,
                        keyguardLocked = false
                    ),
                volumePort = FakeVolumePort(max = 15, current = 6)
            )
        val result = executor.getDeviceStatus(tool(CommonBaseToolCatalog.GET_DEVICE_STATUS))
        assertTrue(result.success)
        val body = result.result.toString()
        assertEquals(
            CommonBaseDeviceStatus.allowedKeys,
            CommonBaseDeviceStatus.parseKeys(body)
        )
        CommonBaseDeviceStatus.forbiddenKeys.forEach { key ->
            assertFalse(body.contains(key))
        }
        assertTrue(body.contains("battery_percent=87"))
        assertTrue(body.contains("charging=true"))
        assertTrue(body.contains("media_volume_index=6"))
        assertTrue(body.contains("screen_interactive=true"))
        assertTrue(body.contains("keyguard_locked=false"))
    }

    @Test
    fun executor_getDeviceStatus_portFailure_isError() {
        val executor =
            CommonBaseDeviceToolExecutor(
                statusPort = FakeStatusPort(failBattery = IllegalStateException("battery gone")),
                volumePort = FakeVolumePort(max = 15, current = 6)
            )
        val result = executor.getDeviceStatus(tool(CommonBaseToolCatalog.GET_DEVICE_STATUS))
        assertFalse(result.success)
        assertFalse(result.error.isNullOrBlank())
        assertFalse(result.result.toString().contains("serial"))
    }

    @Test
    fun executor_setMediaVolume_successFailureAndBadPercent() {
        val successPort = FakeVolumePort(max = 15, current = 1)
        val successExecutor =
            CommonBaseDeviceToolExecutor(FakeStatusPort(), successPort)
        val success =
            successExecutor.setMediaVolume(
                tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "37")
            )
        assertTrue(success.success)
        val successBody = success.result.toString()
        assertTrue(successBody.contains("actual_index=6"))
        assertTrue(successBody.contains("actual_percent=40"))
        assertTrue(successBody.contains("stream_index=3"))

        val mismatchExecutor =
            CommonBaseDeviceToolExecutor(
                FakeStatusPort(),
                FakeVolumePort(max = 15, current = 1, stickyReadback = 1)
            )
        val mismatch =
            mismatchExecutor.setMediaVolume(
                tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "37")
            )
        assertFalse(mismatch.success)
        assertTrue(mismatch.error.orEmpty().contains("readback"))

        val bad =
            successExecutor.setMediaVolume(
                tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "01")
            )
        assertFalse(bad.success)

        val throwPort =
            FakeVolumePort(max = 15, current = 1, failSet = RuntimeException("audio died"))
        val throwExecutor = CommonBaseDeviceToolExecutor(FakeStatusPort(), throwPort)
        val threw =
            throwExecutor.setMediaVolume(
                tool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, CommonBaseToolCatalog.PERCENT to "20")
            )
        assertFalse(threw.success)
        assertEquals(1, throwPort.setCount)
        assertTrue(threw.error.orEmpty().contains("setStreamVolume"))
    }
}

private fun tool(name: String, vararg parameters: Pair<String, String>): AITool {
    return AITool(
        name = name,
        parameters = parameters.map { parameter -> ToolParameter(parameter.first, parameter.second) }
    )
}

private class FakeVolumePort(
    override val streamIndex: Int = 3,
    var max: Int = 15,
    var current: Int = 3,
    var failSet: Exception? = null,
    var failMax: Exception? = null,
    var failCurrent: Exception? = null,
    var stickyReadback: Int? = null
) : CommonBaseMediaVolumePort {
    var lastSet: Int? = null
    var setCount: Int = 0

    override fun maxIndex(): Int {
        failMax?.let { throw it }
        return max
    }

    override fun currentIndex(): Int {
        failCurrent?.let { throw it }
        return stickyReadback ?: current
    }

    override fun setIndex(index: Int) {
        setCount += 1
        failSet?.let { throw it }
        lastSet = index
        current = index
    }
}

private class FakeStatusPort(
    var batteryPercent: Int = 50,
    var charging: Boolean = false,
    var screenInteractive: Boolean = true,
    var keyguardLocked: Boolean = false,
    var failBattery: Exception? = null
) : CommonBaseDeviceStatusPort {
    override fun batteryPercent(): Int {
        failBattery?.let { throw it }
        return batteryPercent
    }

    override fun charging(): Boolean = charging

    override fun screenInteractive(): Boolean = screenInteractive

    override fun keyguardLocked(): Boolean = keyguardLocked
}
