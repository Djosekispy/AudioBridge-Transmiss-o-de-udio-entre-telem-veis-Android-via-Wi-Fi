package com.example.ui.components

import android.content.ClipData
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun ReceiverScreen(
    localIp: String,
    listenPort: String,
    isReceiving: Boolean,
    volume: Float,
    latencyMs: Long,
    packetLoss: Int,
    packetsCount: Int,
    errorMessage: String?,
    isPublicMode: Boolean,
    channelName: String,
    isAnnouncing: Boolean,
    onPortChange: (String) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPublicModeToggle: (Boolean) -> Unit,
    onChannelNameChange: (String) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onRefreshIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAdvancedOptions by remember { mutableStateOf(false) }

    val isActuallyReceiving = isReceiving && packetsCount > 0

    val statusColor by animateColorAsState(
        targetValue = when {
            isActuallyReceiving -> Color(0xFF10B981)
            isReceiving -> Color(0xFFF59E0B)
            errorMessage != null -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "receiverStatusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseReceiver")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isReceiving) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScaleReceiver"
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
                    // Layout Adaptativo em 2 Colunas para Telas Largas (Tablets)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Coluna 1: Modo Público/Privado, IP Card & Volume
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PublicModeToggleRow(
                                isPublicMode = isPublicMode,
                                onPublicModeToggle = onPublicModeToggle
                            )

                            if (isPublicMode) {
                                OutlinedTextField(
                                    value = channelName,
                                    onValueChange = onChannelNameChange,
                                    label = { Text("Nome do Canal") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            ReceiverIpCard(
                                localIp = localIp,
                                onRefreshIp = onRefreshIp,
                                context = context
                            )

                            VolumeCard(
                                volume = volume,
                                onVolumeChange = onVolumeChange
                            )
                        }

                        // Coluna 2: Botão Principal, Estado & Métricas
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ReceiverActionButton(
                                isReceiving = isReceiving,
                                pulseScale = pulseScale,
                                onStartClick = onStartClick,
                                onStopClick = onStopClick
                            )

                            ReceiverStatusSection(
                                statusColor = statusColor,
                                isActuallyReceiving = isActuallyReceiving,
                                isReceiving = isReceiving,
                                isAnnouncing = isAnnouncing,
                                errorMessage = errorMessage,
                                latencyMs = latencyMs,
                                packetLoss = packetLoss,
                                packetsCount = packetsCount
                            )

                            ReceiverAdvancedOptions(
                                showAdvancedOptions = showAdvancedOptions,
                                listenPort = listenPort,
                                isReceiving = isReceiving,
                                onToggleAdvanced = { showAdvancedOptions = !showAdvancedOptions },
                                onPortChange = onPortChange
                            )
                        }
                    }
                } else {
                    // Layout Vertical Compacto para Telemóveis
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PublicModeToggleRow(
                            isPublicMode = isPublicMode,
                            onPublicModeToggle = onPublicModeToggle
                        )

                        if (isPublicMode) {
                            OutlinedTextField(
                                value = channelName,
                                onValueChange = onChannelNameChange,
                                label = { Text("Nome do Canal") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ReceiverIpCard(
                            localIp = localIp,
                            onRefreshIp = onRefreshIp,
                            context = context
                        )

                        ReceiverActionButton(
                            isReceiving = isReceiving,
                            pulseScale = pulseScale,
                            onStartClick = onStartClick,
                            onStopClick = onStopClick
                        )

                        ReceiverStatusSection(
                            statusColor = statusColor,
                            isActuallyReceiving = isActuallyReceiving,
                            isReceiving = isReceiving,
                            isAnnouncing = isAnnouncing,
                            errorMessage = errorMessage,
                            latencyMs = latencyMs,
                            packetLoss = packetLoss,
                            packetsCount = packetsCount
                        )

                        VolumeCard(
                            volume = volume,
                            onVolumeChange = onVolumeChange
                        )

                        ReceiverAdvancedOptions(
                            showAdvancedOptions = showAdvancedOptions,
                            listenPort = listenPort,
                            isReceiving = isReceiving,
                            onToggleAdvanced = { showAdvancedOptions = !showAdvancedOptions },
                            onPortChange = onPortChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicModeToggleRow(
    isPublicMode: Boolean,
    onPublicModeToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isPublicMode) "Modo Público (Anunciar IP)" else "Modo Privado (Oculto)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Switch(
            checked = isPublicMode,
            onCheckedChange = onPublicModeToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("public_mode_switch")
        )
    }
}

@Composable
private fun ReceiverIpCard(
    localIp: String,
    onRefreshIp: () -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.local_ip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                IconButton(onClick = onRefreshIp, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Atualizar IP",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = localIp,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("local_ip_text")
            )

            Surface(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("IP AudioBridge", localIp)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "IP copiado!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copiar Endereço IP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiverActionButton(
    isReceiving: Boolean,
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
        if (isReceiving) {
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
                if (isReceiving) onStopClick() else onStartClick()
            },
            shape = CircleShape,
            color = if (isReceiving) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(115.dp)
                .testTag(if (isReceiving) "stop_receiver_button" else "start_receiver_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isReceiving) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isReceiving) "PARAR" else "OUVIR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ReceiverStatusSection(
    statusColor: Color,
    isActuallyReceiving: Boolean,
    isReceiving: Boolean,
    isAnnouncing: Boolean,
    errorMessage: String?,
    latencyMs: Long,
    packetLoss: Int,
    packetsCount: Int
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
                    isActuallyReceiving -> "A Receber Áudio"
                    isReceiving && isAnnouncing -> "A Anunciar Canal..."
                    isReceiving -> "À espera de emissor..."
                    errorMessage != null -> "Erro de Ligação"
                    else -> "Receptor Desactivado"
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

        AnimatedVisibility(visible = isReceiving) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Latência",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "~$latencyMs ms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            latencyMs < 100 -> Color(0xFF10B981)
                            latencyMs < 250 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Pacotes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$packetsCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Perdidos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$packetLoss",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (packetLoss > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onVolumeChange(if (volume > 0f) 0f else 1f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeDown,
                        contentDescription = "Mute",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .testTag("receiver_volume_slider")
                )

                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ReceiverAdvancedOptions(
    showAdvancedOptions: Boolean,
    listenPort: String,
    isReceiving: Boolean,
    onToggleAdvanced: () -> Unit,
    onPortChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleAdvanced() }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Opções Avançadas",
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

        AnimatedVisibility(visible = showAdvancedOptions) {
            OutlinedTextField(
                value = listenPort,
                onValueChange = onPortChange,
                label = { Text(stringResource(R.string.target_port)) },
                enabled = !isReceiving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receiver_port_input")
            )
        }
    }
}
