package com.ai.assistance.operit.core.commonbase

data class CommonBaseCardBuiltinAccess(
    val customEnabled: Boolean,
    val effectiveBuiltinToolVisibility: Map<String, Boolean>
)

/**
 * Common-base character-card tool access: catalog tools intersected with the card's own builtin
 * allow-list. Does not enumerate packages, MCP servers, skills, scripts, or the terminal.
 */
object CommonBaseCharacterCardToolAccess {
    fun resolve(
        customEnabled: Boolean,
        allowedBuiltinTools: Collection<String>,
        globalToolVisibility: Map<String, Boolean> = emptyMap(),
        catalogToolNames: Set<String> = CommonBaseToolCatalog.registeredToolNames
    ): CommonBaseCardBuiltinAccess {
        val allowed = allowedBuiltinTools
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val effectiveBuiltinToolVisibility =
            catalogToolNames.associateWith { toolName ->
                val globallyVisible = globalToolVisibility[toolName] ?: true
                if (!customEnabled) {
                    globallyVisible
                } else {
                    globallyVisible && allowed.contains(toolName)
                }
            }
        return CommonBaseCardBuiltinAccess(
            customEnabled = customEnabled,
            effectiveBuiltinToolVisibility = effectiveBuiltinToolVisibility
        )
    }
}
