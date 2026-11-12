package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.service.AudioSourceType
import com.example.utils.NetworkUtils

@Composable
fun SenderScreen(
    targetIp: String,
    targetPort: String,
    isTransmitting: Boolean,
    audioPeakLevel: Float,
    sentPacketsCount: Long,
    errorMessage: String?,
    isTestToneEnabled: Boolean,
    audioSourceType: AudioSourceType,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTestToneToggle: (Boolean) -> Unit,
    onAudioSourceChange: (AudioSourceType) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isValidIp = NetworkUtils.isValidIpAddress(targetIp)
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var showDrmInfo by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> Color(0xFF10B981)
            errorMessage != null -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "senderStatusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTransmitting) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWideScreen = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isWideScreen) 32.dp else 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = if (isWideScreen) 900.dp else 500.dp)
                    .fillMaxWidth()
            ) {
                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AudioSourceSelector(
                                audioSourceType = audioSourceType,
                                isTransmitting = isTransmitting,
                                onAudioSourceChange = onAudioSourceChange
                            )

                            SenderIpCard(
                                targetIp = targetIp,
                                isValidIp = isValidIp,
                                isTransmitting = isTransmitting,
                                onIpChange = onIpChange,
                                context = context
                            )

                            TestToneRow(
                                isTestToneEnabled = isTestToneEnabled,
                                onTestToneToggle = onTestToneToggle
                            )

                            SenderActionButton(
                                isTransmitting = isTransmitting,
                                isValidIp = isValidIp,
                                pulseScale = pulseScale,
                                onStartClick = onStartClick,
                                onStopClick = onStopClick
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SenderStatusSection(
                                statusColor = statusColor,
                                isTransmitting = isTransmitting,
                                isTestToneEnabled = isTestToneEnabled,
                                audioSourceType = audioSourceType,
                                errorMessage = errorMessage,
                                audioPeakLevel = audioPeakLevel,
                                sentPacketsCount = sentPacketsCount
                            )

                            SenderOptionsSection(
                                showDrmInfo = showDrmInfo,
                                showAdvancedOptions = showAdvancedOptions,
                                targetPort = targetPort,
                                isTransmitting = isTransmitting,
                                onDrmInfoToggle = { showDrmInfo = !showDrmInfo },
                                onAdvancedToggle = { showAdvancedOptions = !showAdvancedOptions },
                                onPortChange = onPortChange
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AudioSourceSelector(
                            audioSourceType = audioSourceType,
                            isTransmitting = isTransmitting,
                            onAudioSourceChange = onAudioSourceChange
                        )

                        SenderIpCard(
                            targetIp = targetIp,
                            isValidIp = isValidIp,
                            isTransmitting = isTransmitting,
                            onIpChange = onIpChange,
                            context = context
                        )

                        TestToneRow(
                            isTestToneEnabled = isTestToneEnabled,
                            onTestToneToggle = onTestToneToggle
                        )

                        SenderActionButton(
                            isTransmitting = isTransmitting,
                            isValidIp = isValidIp,
                            pulseScale = pulseScale,
                            onStartClick = onStartClick,
                            onStopClick = onStopClick
                        )

                        SenderStatusSection(
                            statusColor = statusColor,
                            isTransmitting = isTransmitting,
                            isTestToneEnabled = isTestToneEnabled,
                            audioSourceType = audioSourceType,
                            errorMessage = errorMessage,
                            audioPeakLevel = audioPeakLevel,
                            sentPacketsCount = sentPacketsCount
                        )

                        SenderOptionsSection(
                            showDrmInfo = showDrmInfo,
                            showAdvancedOptions = showAdvancedOptions,
                            targetPort = targetPort,
                            isTransmitting = isTransmitting,
                            onDrmInfoToggle = { showDrmInfo = !showDrmInfo },
                            onAdvancedToggle = { showAdvancedOptions = !showAdvancedOptions },
                            onPortChange = onPortChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSourceSelector(
    audioSourceType: AudioSourceType,
    isTransmitting: Boolean,
    onAudioSourceChange: (AudioSourceType) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            val isSystemSelected = audioSourceType == AudioSourceType.SYSTEM_AUDIO
            val sysBgColor by animateColorAsState(
                targetValue = if (isSystemSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "sysBg"
            )
            val sysTextColor by animateColorAsState(
                targetValue = if (isSystemSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "sysText"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(sysBgColor)
                    .clickable(enabled = !isTransmitting) { onAudioSourceChange(AudioSourceType.SYSTEM_AUDIO) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = sysTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Áudio do Sistema",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = sysTextColor
                    )
                }
            }

            val isMicSelected = audioSourceType == AudioSourceType.MICROPHONE
            val micBgColor by animateColorAsState(
                targetValue = if (isMicSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "micBg"
            )
            val micTextColor by animateColorAsState(
                targetValue = if (isMicSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "micText"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(micBgColor)
                    .clickable(enabled = !isTransmitting) { onAudioSourceChange(AudioSourceType.MICROPHONE) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = micTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Microfone (Voz)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = micTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SenderIpCard(
    targetIp: String,
    isValidIp: Boolean,
    isTransmitting: Boolean,
    onIpChange: (String) -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IP do Receptor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val pasted = clipData.getItemAt(0).text.toString().trim()
                            onIpChange(pasted)
                            Toast.makeText(context, "IP colado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isTransmitting
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "Colar IP",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            OutlinedTextField(
                value = targetIp,
                onValueChange = onIpChange,
                placeholder = { Text("Ex: 192.168.1.15", fontSize = 18.sp) },
                enabled = !isTransmitting,
                isError = targetIp.isNotEmpty() && !isValidIp,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_ip_input")
            )

            if (targetIp.isNotEmpty() && !isValidIp) {
                Text(
                    text = "Endereço IP inválido",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TestToneRow(
    isTestToneEnabled: Boolean,
    onTestToneToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Gerar Tom de Teste (440Hz)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Envia sinal sonoro contínuo para testar ligação",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = isTestToneEnabled,
            onCheckedChange = onTestToneToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("test_tone_switch")
        )
    }
}

@Composable
private fun SenderActionButton(
    isTransmitting: Boolean,
    isValidIp: Boolean,
    pulseScale: Float,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .size(150.dp)
    ) {
        if (isTransmitting) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
            )
        }

        Surface(
            onClick = {
                if (isTransmitting) onStopClick() else onStartClick()
            },
            enabled = isTransmitting || isValidIp,
            shape = CircleShape,
            color = if (isTransmitting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(115.dp)
                .testTag(if (isTransmitting) "stop_sender_button" else "start_sender_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isTransmitting) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isTransmitting) "PARAR" else "EMITIR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SenderStatusSection(
    statusColor: Color,
    isTransmitting: Boolean,
    isTestToneEnabled: Boolean,
    audioSourceType: AudioSourceType,
    errorMessage: String?,
    audioPeakLevel: Float,
    sentPacketsCount: Long
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(statusColor.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isTransmitting && isTestToneEnabled -> "A Transmitir Tom de Teste"
                    isTransmitting && audioSourceType == AudioSourceType.MICROPHONE -> "A Transmitir Microfone (Voz)"
                    isTransmitting -> "A Transmitir Áudio do Sistema"
                    errorMessage != null -> "Erro de Ligação"
                    else -> "Pronto a Emitir"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        AnimatedVisibility(
            visible = isTransmitting,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sinal Capturado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$sentPacketsCount pacotes",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(audioPeakLevel.coerceIn(0.05f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        Color(0xFF10B981)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SenderOptionsSection(
    showDrmInfo: Boolean,
    showAdvancedOptions: Boolean,
    targetPort: String,
    isTransmitting: Boolean,
    onDrmInfoToggle: () -> Unit,
    onAdvancedToggle: () -> Unit,
    onPortChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDrmInfoToggle() }
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Aviso DRM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAdvancedToggle() }
                    .padding(8.dp)
            ) {
                Text(
                    text = "Avançado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = showDrmInfo) {
            DrmWarningCard()
        }

        AnimatedVisibility(visible = showAdvancedOptions) {
            OutlinedTextField(
                value = targetPort,
                onValueChange = onPortChange,
                label = { Text(stringResource(R.string.target_port)) },
                enabled = !isTransmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_port_input")
            )
        }
    }
}
