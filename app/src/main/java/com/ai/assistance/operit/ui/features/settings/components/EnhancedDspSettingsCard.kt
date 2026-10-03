package com.ai.assistance.operit.ui.features.settings.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.commonbase.CommonBaseProfile
import com.ai.assistance.operit.core.devicebridge.DspAndroidBridge
import com.ai.assistance.operit.core.devicebridge.DspEnablementDecision
import com.ai.assistance.operit.core.devicebridge.DspUserSettings
import kotlinx.coroutines.launch

@Composable
fun EnhancedDspSettingsCard(containerColor: Color) {
    if (!CommonBaseProfile.enhancedDevice) {
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { DspUserSettings(context) }
    var enabled by remember { mutableStateOf(settings.isUserEnabled()) }
    var status by remember { mutableStateOf(DspAndroidBridge.describeStatus(context)) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        enabled = settings.isUserEnabled()
        status = DspAndroidBridge.describeStatus(context)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Assistant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "设备增强 · DSP",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "低功耗 DSP 唤醒",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "默认关闭。开启前须已安装匹配签名的 Companion，且本应用是系统所选助手。不会用 root 授权，也不会打开 CPU 常驻唤醒。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = !busy,
                        onCheckedChange = { checked ->
                            if (busy) {
                                return@Switch
                            }
                            busy = true
                            enabled = checked
                            status = "正在切换 DSP…"
                            scope.launch {
                                try {
                                    val decision: DspEnablementDecision =
                                        DspAndroidBridge.setUserEnabled(context, checked)
                                    enabled = DspUserSettings(context).isUserEnabled()
                                    status = DspAndroidBridge.describeStatus(context)
                                    if (checked && !decision.ok) {
                                        Toast.makeText(
                                            context,
                                            decision.errorMessage ?: status,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (_: Exception) {
                                    enabled = DspUserSettings(context).isUserEnabled()
                                    status = DspAndroidBridge.describeStatus(context)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        try {
                            context.startActivity(DspAndroidBridge.voiceInputSettingsIntent())
                        } catch (_: Exception) {
                            Toast.makeText(context, "无法打开系统语音输入设置", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("选择系统助手")
                }
                TextButton(onClick = { refresh() }, modifier = Modifier.align(Alignment.End)) {
                    Text("刷新状态")
                }
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = "锁屏唤起仍须系统解锁后才能进入现有语音入口，不是免解锁息屏问答。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
