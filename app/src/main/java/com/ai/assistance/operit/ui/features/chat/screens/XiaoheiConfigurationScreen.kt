package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.R
import kotlinx.coroutines.delay

/**
 * COMMON_BASE empty-config welcome. Does not embed keys, pick a vendor, or start network I/O.
 * Navigation still uses [onNavigateToModelConfig].
 */
@Composable
fun XiaoheiConfigurationScreen(
        isSaving: Boolean,
        onNavigateToModelConfig: () -> Unit
) {
        var prepExpanded by remember { mutableStateOf(false) }
        var navigationLocked by remember { mutableStateOf(false) }
        val editionNote = XiaoheiWelcomeContent.editionNote(BuildConfig.COMMON_STORE)

        LaunchedEffect(navigationLocked) {
                if (!navigationLocked) {
                        return@LaunchedEffect
                }
                delay(NAVIGATION_LOCK_MS)
                navigationLocked = false
        }

        BoxWithConstraints(
                modifier =
                        Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)

        ) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = maxHeight)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        XiaoheiConfigurationMark()

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                                text = XiaoheiWelcomeContent.TITLE,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text = XiaoheiWelcomeContent.SUBTITLE,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                                text = editionNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                                onClick = {
                                        if (isSaving || navigationLocked) {
                                                return@Button
                                        }
                                        navigationLocked = true
                                        onNavigateToModelConfig()
                                },
                                enabled = !isSaving && !navigationLocked,
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .semantics {
                                                        contentDescription =
                                                                XiaoheiWelcomeContent.PRIMARY_ACTION
                                                }
                        ) {
                                if (isSaving) {
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = LocalContentColor.current,
                                                strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                        text =
                                                if (isSaving) XiaoheiWelcomeContent.SAVING_LABEL
                                                else XiaoheiWelcomeContent.PRIMARY_ACTION,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        PrepHelpCard(
                                expanded = prepExpanded,
                                onToggle = { prepExpanded = !prepExpanded }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = XiaoheiWelcomeContent.AFTER_CONFIG_HINT,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                        )
                }
        }
}

@Composable
private fun XiaoheiConfigurationMark() {
        Box(
                // painterResource cannot load the launcher's <shape> drawable. Use a
                // themed surface instead, keeping the bitmap mark and avoiding a first-run crash.
                modifier = Modifier.size(96.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
        ) {
                Image(
                        painter = painterResource(id = R.drawable.ic_launcher_xiaohei_foreground),
                        contentDescription = XiaoheiWelcomeContent.LOGO_DESCRIPTION,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        contentScale = ContentScale.Fit
                )
        }
}

@Composable
private fun PrepHelpCard(expanded: Boolean, onToggle: () -> Unit) {
        OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                        ),
                border = CardDefaults.outlinedCardBorder()
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .clickable(
                                                        onClickLabel =
                                                                if (expanded)
                                                                        XiaoheiWelcomeContent
                                                                                .COLLAPSE_LABEL
                                                                else
                                                                        XiaoheiWelcomeContent
                                                                                .EXPAND_LABEL
                                                ) {
                                                        onToggle()
                                                }
                                                .padding(horizontal = 16.dp)
                                                .semantics(mergeDescendants = true) {
                                                        contentDescription =
                                                                XiaoheiWelcomeContent.PREP_TITLE
                                                        stateDescription =
                                                                if (expanded)
                                                                        XiaoheiWelcomeContent
                                                                                .EXPANDED_STATE
                                                                else
                                                                        XiaoheiWelcomeContent
                                                                                .COLLAPSED_STATE
                                                },
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = XiaoheiWelcomeContent.PREP_TITLE,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                        imageVector =
                                                if (expanded) Icons.Filled.KeyboardArrowUp
                                                else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        AnimatedVisibility(visible = expanded) {
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                start = 16.dp,
                                                                end = 16.dp,
                                                                bottom = 16.dp
                                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Text(
                                                text = XiaoheiWelcomeContent.PREP_ITEM_ENDPOINT,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                                text = XiaoheiWelcomeContent.PREP_ITEM_MODEL,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                                text = XiaoheiWelcomeContent.PREP_ITEM_KEY,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                        }
                }
        }
}

private const val NAVIGATION_LOCK_MS = 800L
