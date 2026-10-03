package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/**
 * Common-base labeled voice-session actions. 48dp minimum, text always visible.
 * Callers must wire [onStopAnswering] to the existing interrupt path and
 * [onEndVoice] to exitWaveMode. Mute is not a stop control.
 */
@Composable
fun VoiceSessionActionBar(
    stopAnsweringEnabled: Boolean,
    showListenContinuesHint: Boolean,
    onStopAnswering: () -> Unit,
    onEndVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stopLabel = stringResource(R.string.xiaohei_voice_stop_answering)
    val endLabel = stringResource(R.string.xiaohei_voice_end_session)
    val listenContinues = stringResource(R.string.xiaohei_voice_stop_answering_listen_continues)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStopAnswering,
                enabled = stopAnsweringEnabled,
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(min = 48.dp)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = stopLabel },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.60f)
                    )
            ) {
                Text(
                    text = stopLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onEndVoice,
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(min = 48.dp)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = endLabel },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
            ) {
                Text(
                    text = endLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showListenContinuesHint) {
            Text(
                text = listenContinues,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
