package com.ai.assistance.operit.data.preferences

data class ResolvedCharacterCardToolAccess(
    val customEnabled: Boolean,
    val effectiveBuiltinToolVisibility: Map<String, Boolean>,
    val allowedPackageNames: Set<String>,
    val allowedSkillNames: Set<String>,
    val allowedMcpServerNames: Set<String>,
    val canUsePackageSystem: Boolean,
    val hasAnyAllowedExternalSource: Boolean
) {
    fun isBuiltinToolAllowed(toolName: String): Boolean {
        if (!customEnabled) {
            return effectiveBuiltinToolVisibility[toolName] ?: true
        }
        return when (toolName) {
            "package_proxy" -> hasAnyAllowedExternalSource
            else -> effectiveBuiltinToolVisibility[toolName] == true
        }
    }

    fun isExternalSourceAllowed(sourceName: String): Boolean {
        if (!customEnabled) return true
        if (!canUsePackageSystem) return false
        return allowedPackageNames.contains(sourceName) ||
            allowedSkillNames.contains(sourceName) ||
            allowedMcpServerNames.contains(sourceName)
    }
}
