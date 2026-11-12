package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AudioSourceType
import com.example.ui.components.ChannelsScreen
import com.example.ui.components.ReceiverScreen
import com.example.ui.components.SenderScreen
import com.example.ui.theme.AudioBridgeTheme
import com.example.viewmodel.AppMode
import com.example.viewmodel.AudioBridgeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AudioBridgeViewModel by viewModels()

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startSender(result.resultCode, result.data)
        } else {
            Toast.makeText(
                this,
                "Permissão de captura de ecrã recusada.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            if (viewModel.audioSourceType.value == AudioSourceType.MICROPHONE) {
                viewModel.startSender(-1, null)
            } else {
                requestMediaProjection()
            }
        } else {
            Toast.makeText(
                this,
                "Permissão de gravação de áudio é necessária.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AudioBridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AudioBridgeApp(
                        viewModel = viewModel,
                        onRequestStartSender = { checkPermissionsAndStartSender() },
                        onRequestStopSender = { viewModel.stopSender() },
                        onRequestStartReceiver = { viewModel.startReceiver() },
                        onRequestStopReceiver = { viewModel.stopReceiver() }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStartSender() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            if (viewModel.audioSourceType.value == AudioSourceType.MICROPHONE) {
                viewModel.startSender(-1, null)
            } else {
                requestMediaProjection()
            }
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioBridgeApp(
    viewModel: AudioBridgeViewModel,
    onRequestStartSender: () -> Unit,
    onRequestStopSender: () -> Unit,
    onRequestStartReceiver: () -> Unit,
    onRequestStopReceiver: () -> Unit
) {
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val targetIp by viewModel.targetIp.collectAsStateWithLifecycle()
    val targetPort by viewModel.targetPort.collectAsStateWithLifecycle()
    val localIp by viewModel.localIp.collectAsStateWithLifecycle()
    val receiverVolume by viewModel.receiverVolume.collectAsStateWithLifecycle()

    val isPublicMode by viewModel.isPublicMode.collectAsStateWithLifecycle()
    val channelName by viewModel.channelName.collectAsStateWithLifecycle()
    val discoveredChannels by viewModel.discoveredChannels.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val isAnnouncing by viewModel.isAnnouncing.collectAsStateWithLifecycle()
    val isTestToneEnabled by viewModel.isTestToneEnabled.collectAsStateWithLifecycle()
    val audioSourceType by viewModel.audioSourceType.collectAsStateWithLifecycle()

    val isTransmitting by viewModel.isTransmitting.collectAsStateWithLifecycle()
    val senderPeak by viewModel.senderAudioPeak.collectAsStateWithLifecycle()
    val senderPackets by viewModel.senderPacketsCount.collectAsStateWithLifecycle()
    val senderError by viewModel.senderError.collectAsStateWithLifecycle()

    val isReceiving by viewModel.isReceiving.collectAsStateWithLifecycle()
    val receiverLatency by viewModel.receiverLatencyMs.collectAsStateWithLifecycle()
    val receiverLoss by viewModel.receiverPacketLoss.collectAsStateWithLifecycle()
    val receiverPackets by viewModel.receiverPacketsCount.collectAsStateWithLifecycle()
    val receiverError by viewModel.receiverError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "AudioBridge",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
                    .testTag("mode_tab_row")
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val isSenderSelected = selectedMode == AppMode.SENDER
                    val senderBgColor by animateColorAsState(
                        targetValue = if (isSenderSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        label = "senderBg"
                    )
                    val senderTextColor by animateColorAsState(
                        targetValue = if (isSenderSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "senderText"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(senderBgColor)
                            .clickable { viewModel.selectMode(AppMode.SENDER) }
                            .testTag("sender_tab")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = null,
                                tint = senderTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.sender_mode),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = senderTextColor
                            )
                        }
                    }

                    val isReceiverSelected = selectedMode == AppMode.RECEIVER
                    val receiverBgColor by animateColorAsState(
                        targetValue = if (isReceiverSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        label = "receiverBg"
                    )
                    val receiverTextColor by animateColorAsState(
                        targetValue = if (isReceiverSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "receiverText"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(receiverBgColor)
                            .clickable { viewModel.selectMode(AppMode.RECEIVER) }
                            .testTag("receiver_tab")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SpeakerPhone,
                                contentDescription = null,
                                tint = receiverTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.receiver_mode),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = receiverTextColor
                            )
                        }
                    }

                    val isChannelsSelected = selectedMode == AppMode.CHANNELS
                    val channelsBgColor by animateColorAsState(
                        targetValue = if (isChannelsSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        label = "channelsBg"
                    )
                    val channelsTextColor by animateColorAsState(
                        targetValue = if (isChannelsSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "channelsText"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(channelsBgColor)
                            .clickable { viewModel.selectMode(AppMode.CHANNELS) }
                            .testTag("channels_tab")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = channelsTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Canais",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = channelsTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Crossfade(
                targetState = selectedMode,
                label = "modeCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { mode ->
                when (mode) {
                    AppMode.SENDER -> {
                        SenderScreen(
                            targetIp = targetIp,
                            targetPort = targetPort,
                            isTransmitting = isTransmitting,
                            audioPeakLevel = senderPeak,
                            sentPacketsCount = senderPackets,
                            errorMessage = senderError,
                            isTestToneEnabled = isTestToneEnabled,
                            audioSourceType = audioSourceType,
                            onIpChange = viewModel::setTargetIp,
                            onPortChange = viewModel::setTargetPort,
                            onTestToneToggle = viewModel::setTestToneEnabled,
                            onAudioSourceChange = viewModel::setAudioSourceType,
                            onStartClick = onRequestStartSender,
                            onStopClick = onRequestStopSender
                        )
                    }
                    AppMode.RECEIVER -> {
                        ReceiverScreen(
                            localIp = localIp,
                            listenPort = targetPort,
                            isReceiving = isReceiving,
                            volume = receiverVolume,
                            latencyMs = receiverLatency,
                            packetLoss = receiverLoss,
                            packetsCount = receiverPackets,
                            errorMessage = receiverError,
                            isPublicMode = isPublicMode,
                            channelName = channelName,
                            isAnnouncing = isAnnouncing,
                            onPortChange = viewModel::setTargetPort,
                            onVolumeChange = viewModel::setReceiverVolume,
                            onPublicModeToggle = viewModel::setPublicMode,
                            onChannelNameChange = viewModel::setChannelName,
                            onStartClick = onRequestStartReceiver,
                            onStopClick = onRequestStopReceiver,
                            onRefreshIp = viewModel::refreshLocalIp
                        )
                    }
                    AppMode.CHANNELS -> {
                        ChannelsScreen(
                            discoveredChannels = discoveredChannels,
                            isDiscovering = isDiscovering,
                            selectedIp = targetIp,
                            onSelectChannel = viewModel::selectDiscoveredChannel,
                            onRefresh = viewModel::startChannelDiscovery
                        )
                    }
                }
            }
        }
    }
}
