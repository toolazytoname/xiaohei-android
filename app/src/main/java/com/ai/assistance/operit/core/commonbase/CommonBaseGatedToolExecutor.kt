package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CommonBaseGatedToolExecutor(private val delegate: ToolExecutor) : ToolExecutor {
    override fun invoke(tool: AITool): ToolResult {
        CommonBaseExecutionGuard.resultFor(tool)?.let { return it }
        return delegate.invoke(tool)
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
        val denied = CommonBaseExecutionGuard.resultFor(tool)
        if (denied != null) {
            emit(denied)
            return@flow
        }
        delegate.invokeAndStream(tool).collect { result -> emit(result) }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        return delegate.validateParameters(tool)
    }
}
