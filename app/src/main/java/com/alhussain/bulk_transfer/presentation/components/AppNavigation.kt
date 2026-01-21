package com.alhussain.bulk_transfer.presentation.components

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alhussain.bulk_transfer.data.bluetooth.TransferState
import com.alhussain.bulk_transfer.presentation.BluetoothViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: BluetoothViewModel) {
    val context = LocalContext.current
    var serverStarted by remember { mutableStateOf(false) }

    val discoverableLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_CANCELED) {
                if (!serverStarted) {
                    serverStarted = true
                    viewModel.waitForIncomingConnections()
                }
            } else {
                Toast.makeText(context, "Discoverable mode required", Toast.LENGTH_SHORT).show()
            }
        }
    val permissionState =
        rememberMultiplePermissionsState(
            permissions =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                } else {
                    listOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                },
        )

    LaunchedEffect(key1 = true) {
        permissionState.launchMultiplePermissionRequest()
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.ModeSelection) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = currentScreen != Screen.ModeSelection,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text =
                                when (currentScreen) {
                                    Screen.Sender -> "Sender Dashboard"
                                    Screen.Receiver -> "Receiver Feed"
                                    else -> ""
                                },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.disconnect()
                            viewModel.stopScan()
                            currentScreen = Screen.ModeSelection
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (currentScreen) {
                Screen.ModeSelection -> {
                    ModeSelectionScreen(
                        onSenderSelected = {
                            viewModel.clearState()
                            currentScreen = Screen.Sender
                            viewModel.startScan()
                        },
                        onReceiverSelected = {
                            currentScreen = Screen.Receiver

                            discoverableLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 1600)
                                },
                            )
                        },
                    )
                }

                Screen.Sender -> {
                    if (state.isConnected) {
                        SenderConnectedScreenWithProgress(state.transferState) {
                            viewModel.sendVouchersWithProgress(it)
                        }
                    } else {
                        // Filtering devices to only show ones with a name to keep list clean
                        val filteredScannedDevices =
                            state.scannedDevices.filter { !it.name.isNullOrBlank() }
                        val filteredPairedDevices =
                            state.pairedDevices.filter { !it.name.isNullOrBlank() }

                        DeviceScreen(
                            state =
                                state.copy(
                                    scannedDevices = filteredScannedDevices,
                                    pairedDevices = filteredPairedDevices,
                                ),
                            onStartScan = { viewModel.startScan() },
                            onStopScan = { viewModel.stopScan() },
                            onDeviceClick = { device ->
                                viewModel.connectToDevice(device)
                            },
                            onStartServer = {},
                        )
                    }
                }

                Screen.Receiver -> {
                    if (state.isConnected || state.receivedVouchers.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            VoucherList(
                                vouchers = state.receivedVouchers,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        ReceiverWaitingScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun SenderConnectedScreenWithProgress(
    transferState: TransferState,
    onSendVouchers: (Int) -> Unit,
) {
    var quantity by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Check if transfer just completed
    val isCompleted =
        !transferState.isTransferring &&
            transferState.error == null &&
            transferState.progress == 1f &&
            transferState.totalItems > 0

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color =
                if (isCompleted) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector =
                        if (isCompleted) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.Devices
                        },
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint =
                        if (isCompleted) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text =
                if (isCompleted) {
                    "Transfer Complete!"
                } else {
                    "Device Connected!"
                },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (isCompleted) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
        )

        Text(
            text =
                if (isCompleted) {
                    "Successfully sent ${transferState.totalItems} vouchers"
                } else {
                    "Ready to transfer vouchers securely."
                },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Show progress if transferring
        if (transferState.isTransferring) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(
                    progress = { transferState.progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sending ${transferState.currentItem} / ${transferState.totalItems}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!isCompleted) {
            // Input field when not transferring and not completed
            OutlinedTextField(
                value = quantity,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        quantity = newValue
                        showError = false
                    }
                },
                label = { Text("Number of Vouchers") },
                placeholder = { Text("Enter quantity") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showError,
                supportingText = {
                    if (showError) {
                        Text(
                            text = "Please enter a valid quantity (1 or more)",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (transferState.error != null) {
                        Text(
                            text = transferState.error,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.ConfirmationNumber,
                        contentDescription = null,
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                shape = RoundedCornerShape(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isCompleted) {
            Button(
                onClick = {
                    val qty = quantity.toIntOrNull()
                    if (qty != null && qty > 0) {
                        onSendVouchers(qty)
                    } else {
                        showError = true
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                enabled = quantity.isNotEmpty() && !transferState.isTransferring,
            ) {
                if (transferState.isTransferring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (transferState.isTransferring) "Transferring..." else "Transfer Vouchers Now",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun ReceiverWaitingScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(140.dp),
                strokeWidth = 8.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Waiting for Connection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Make sure your Bluetooth is visible and the sender is scanning.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ModeSelectionScreen(
    onSenderSelected: () -> Unit,
    onReceiverSelected: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                            ),
                    ),
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Bulk Transfer",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = (-1).sp,
        )
        Text(
            text = "Fast. Secure. Peer-to-Peer.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )

        Spacer(modifier = Modifier.height(64.dp))

        ModeCard(
            title = "I want to Send",
            subtitle = "Scan and select a device to connect",
            icon = Icons.Rounded.Search,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            onClick = onSenderSelected,
        )

        Spacer(modifier = Modifier.height(20.dp))

        ModeCard(
            title = "I want to Receive",
            subtitle = "Wait for incoming connection",
            icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            onClick = onReceiverSelected,
        )
    }
}

@Composable
fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.2f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(text = subtitle, fontSize = 14.sp, color = contentColor.copy(alpha = 0.8f))
            }
        }
    }
}

// package com.alhussain.bulk_transfer.presentation.components
//
// import android.Manifest
// import android.app.Activity
// import android.bluetooth.BluetoothAdapter
// import android.content.Intent
// import android.os.Build
// import android.widget.Toast
// import androidx.activity.compose.rememberLauncherForActivityResult
// import androidx.activity.result.contract.ActivityResultContracts
// import androidx.compose.animation.*
// import androidx.compose.foundation.background
// import androidx.compose.foundation.border
// import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.shape.CircleShape
// import androidx.compose.foundation.shape.RoundedCornerShape
// import androidx.compose.foundation.text.KeyboardActions
// import androidx.compose.foundation.text.KeyboardOptions
// import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.automirrored.rounded.ArrowBack
// import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
// import androidx.compose.material.icons.automirrored.rounded.Send
// import androidx.compose.material.icons.rounded.ConfirmationNumber
// import androidx.compose.material.icons.rounded.Devices
// import androidx.compose.material.icons.rounded.Search
// import androidx.compose.material3.*
// import androidx.compose.runtime.*
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.draw.clip
// import androidx.compose.ui.graphics.Brush
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.graphics.vector.ImageVector
// import androidx.compose.ui.platform.LocalContext
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.text.input.ImeAction
// import androidx.compose.ui.text.input.KeyboardType
// import androidx.compose.ui.text.style.TextAlign
// import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp
// import androidx.lifecycle.compose.collectAsStateWithLifecycle
// import com.alhussain.bulk_transfer.MainActivity
// import com.alhussain.bulk_transfer.data.bluetooth.TransferState
// import com.alhussain.bulk_transfer.presentation.BluetoothViewModel
// import com.google.accompanist.permissions.ExperimentalPermissionsApi
// import com.google.accompanist.permissions.rememberMultiplePermissionsState
//
// @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
// @Composable
// fun AppNavigation(viewModel: BluetoothViewModel) {
//    val context = LocalContext.current
//    var serverStarted by remember { mutableStateOf(false) }
//
//    val discoverableLauncher =
//        rememberLauncherForActivityResult(
//            contract = ActivityResultContracts.StartActivityForResult(),
//        ) { result ->
//            if (result.resultCode != Activity.RESULT_CANCELED) {
//                if (!serverStarted) {
//                    serverStarted = true
//                    viewModel.waitForIncomingConnections()
//                }
//            } else {
//                Toast.makeText(context, "Discoverable mode required", Toast.LENGTH_SHORT).show()
//            }
//        }
//    val permissionState =
//        rememberMultiplePermissionsState(
//            permissions =
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                    listOf(
//                        Manifest.permission.BLUETOOTH_SCAN,
//                        Manifest.permission.BLUETOOTH_CONNECT,
//                        Manifest.permission.ACCESS_FINE_LOCATION,
//                        Manifest.permission.ACCESS_COARSE_LOCATION,
//                    )
//                } else {
//                    listOf(
//                        Manifest.permission.BLUETOOTH,
//                        Manifest.permission.BLUETOOTH_ADMIN,
//                        Manifest.permission.ACCESS_FINE_LOCATION,
//                        Manifest.permission.ACCESS_COARSE_LOCATION,
//                    )
//                },
//        )
//
//    LaunchedEffect(key1 = true) {
//        permissionState.launchMultiplePermissionRequest()
//    }
//
//    var currentScreen by remember { mutableStateOf<Screen>(Screen.ModeSelection) }
//    val state by viewModel.state.collectAsStateWithLifecycle()
//
//    Scaffold(
//        topBar = {
//            AnimatedVisibility(
//                visible = currentScreen != Screen.ModeSelection,
//                enter = fadeIn() + slideInVertically(),
//                exit = fadeOut() + slideOutVertically(),
//            ) {
//                CenterAlignedTopAppBar(
//                    title = {
//                        Text(
//                            text =
//                                when (currentScreen) {
//                                    Screen.Sender -> "Sender Dashboard"
//                                    Screen.Receiver -> "Receiver Feed"
//                                    else -> ""
//                                },
//                            fontWeight = FontWeight.Bold,
//                        )
//                    },
//                    navigationIcon = {
//                        IconButton(onClick = {
//                            viewModel.disconnect()
//                            viewModel.stopScan()
//                            currentScreen = Screen.ModeSelection
//                        }) {
//                            Icon(
//                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
//                                contentDescription = "Back",
//                            )
//                        }
//                    },
//                    colors =
//                        TopAppBarDefaults.centerAlignedTopAppBarColors(
//                            containerColor = Color.Transparent,
//                        ),
//                )
//            }
//        },
//    ) { padding ->
//        Box(
//            modifier =
//                Modifier
//                    .fillMaxSize()
//                    .padding(padding),
//        ) {
//            when (currentScreen) {
//                Screen.ModeSelection -> {
//                    ModeSelectionScreen(
//                        onSenderSelected = {
//                            currentScreen = Screen.Sender
//                            viewModel.startScan()
//                        },
//                        onReceiverSelected = {
//                            currentScreen = Screen.Receiver
//
//                            discoverableLauncher.launch(
//                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
//                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 1600)
//                                },
//                            )
// //                            viewModel.waitForIncomingConnections()
//                        },
//                    )
//                }
//
//                Screen.Sender -> {
//                    if (state.isConnected) {
//                        SenderConnectedScreenWithProgress(state.transferState) {
//                            viewModel.sendVouchersWithProgress(it)
//                        }
// //                        SenderConnectedScreen(
// //                            onSendVouchers = { viewModel.sendVouchers(it) },
// //                        )
//                    } else {
//                        // Filtering devices to only show ones with a name to keep list clean
//                        val filteredScannedDevices =
//                            state.scannedDevices.filter { !it.name.isNullOrBlank() }
//                        val filteredPairedDevices =
//                            state.pairedDevices.filter { !it.name.isNullOrBlank() }
//
//                        DeviceScreen(
//                            state =
//                                state.copy(
//                                    scannedDevices = filteredScannedDevices,
//                                    pairedDevices = filteredPairedDevices,
//                                ),
//                            onStartScan = { viewModel.startScan() },
//                            onStopScan = { viewModel.stopScan() },
//                            onDeviceClick = { device ->
//                                viewModel.connectToDevice(device)
//                            },
//                            onStartServer = {},
//                        )
//                    }
//                }
//
//                Screen.Receiver -> {
//                    if (state.isConnected) {
//                        Column(modifier = Modifier.fillMaxSize()) {
//                            VoucherList(
//                                vouchers = state.receivedVouchers,
//                                modifier = Modifier.weight(1f),
//                            )
//                        }
//                    } else {
//                        ReceiverWaitingScreen()
//                    }
//                }
//            }
//        }
//    }
// }
//
// @Composable
// fun SenderConnectedScreenWithProgress(
//    transferState: TransferState,
//    onSendVouchers: (Int) -> Unit,
// ) {
//    var quantity by remember { mutableStateOf("") }
//    var showError by remember { mutableStateOf(false) }
//
//    Column(
//        modifier =
//            Modifier
//                .fillMaxSize()
//                .padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center,
//    ) {
//        Surface(
//            modifier = Modifier.size(120.dp),
//            shape = CircleShape,
//            color = MaterialTheme.colorScheme.primaryContainer,
//        ) {
//            Box(contentAlignment = Alignment.Center) {
//                Icon(
//                    imageVector = Icons.Rounded.Devices,
//                    contentDescription = null,
//                    modifier = Modifier.size(64.dp),
//                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
//                )
//            }
//        }
//
//        Spacer(modifier = Modifier.height(24.dp))
//
//        Text(
//            text = "Device Connected!",
//            style = MaterialTheme.typography.headlineSmall,
//            fontWeight = FontWeight.Bold,
//            color = MaterialTheme.colorScheme.primary,
//        )
//
//        Text(
//            text = "Ready to transfer vouchers securely.",
//            style = MaterialTheme.typography.bodyLarge,
//            textAlign = TextAlign.Center,
//            color = MaterialTheme.colorScheme.onSurfaceVariant,
//        )
//
//        Spacer(modifier = Modifier.height(48.dp))
//
//        // Show progress if transferring
//        if (transferState.isTransferring) {
//            Column(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalAlignment = Alignment.CenterHorizontally,
//            ) {
//                LinearProgressIndicator(
//                    progress = transferState.progress,
//                    modifier =
//                        Modifier
//                            .fillMaxWidth()
//                            .height(8.dp)
//                            .clip(RoundedCornerShape(4.dp)),
//                )
//
//                Spacer(modifier = Modifier.height(8.dp))
//
//                Text(
//                    text = "Sending ${transferState.currentItem} / ${transferState.totalItems}",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant,
//                )
//            }
//        } else {
//            // Input field when not transferring
//            OutlinedTextField(
//                value = quantity,
//                onValueChange = { newValue ->
//                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
//                        quantity = newValue
//                        showError = false
//                    }
//                },
//                label = { Text("Number of Vouchers") },
//                placeholder = { Text("Enter quantity") },
//                modifier = Modifier.fillMaxWidth(),
//                singleLine = true,
//                isError = showError,
//                supportingText = {
//                    if (showError) {
//                        Text(
//                            text = "Please enter a valid quantity (1 or more)",
//                            color = MaterialTheme.colorScheme.error,
//                        )
//                    }
//                    if (transferState.error != null) {
//                        Text(
//                            text = transferState.error,
//                            color = MaterialTheme.colorScheme.error,
//                        )
//                    }
//                },
//                leadingIcon = {
//                    Icon(
//                        imageVector = Icons.Rounded.ConfirmationNumber,
//                        contentDescription = null,
//                    )
//                },
//                keyboardOptions =
//                    KeyboardOptions(
//                        keyboardType = KeyboardType.Number,
//                        imeAction = ImeAction.Done,
//                    ),
//                shape = RoundedCornerShape(16.dp),
//                enabled = !transferState.isTransferring,
//            )
//        }
//
//        Spacer(modifier = Modifier.height(24.dp))
//
//        Button(
//            onClick = {
//                val qty = quantity.toIntOrNull()
//                if (qty != null && qty > 0) {
//                    onSendVouchers(qty)
//                } else {
//                    showError = true
//                }
//            },
//            modifier =
//                Modifier
//                    .fillMaxWidth()
//                    .height(64.dp),
//            shape = RoundedCornerShape(20.dp),
//            contentPadding = PaddingValues(horizontal = 24.dp),
//            enabled = quantity.isNotEmpty() && !transferState.isTransferring,
//        ) {
//            if (transferState.isTransferring) {
//                CircularProgressIndicator(
//                    modifier = Modifier.size(24.dp),
//                    color = MaterialTheme.colorScheme.onPrimary,
//                )
//            } else {
//                Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = null)
//            }
//            Spacer(modifier = Modifier.width(12.dp))
//            Text(
//                text = if (transferState.isTransferring) "Transferring..." else "Transfer Vouchers Now",
//                fontSize = 18.sp,
//                fontWeight = FontWeight.Bold,
//            )
//        }
//    }
// }
//
// @Composable
// fun SenderConnectedScreen(onSendVouchers: (Int) -> Unit) {
//    var quantity by remember { mutableStateOf("") }
//    var showError by remember { mutableStateOf(false) }
//
//    Column(
//        modifier =
//            Modifier
//                .fillMaxSize()
//                .padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center,
//    ) {
//        Surface(
//            modifier = Modifier.size(120.dp),
//            shape = CircleShape,
//            color = MaterialTheme.colorScheme.primaryContainer,
//        ) {
//            Box(contentAlignment = Alignment.Center) {
//                Icon(
//                    imageVector = Icons.Rounded.Devices,
//                    contentDescription = null,
//                    modifier = Modifier.size(64.dp),
//                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
//                )
//            }
//        }
//
//        Spacer(modifier = Modifier.height(24.dp))
//
//        Text(
//            text = "Device Connected!",
//            style = MaterialTheme.typography.headlineSmall,
//            fontWeight = FontWeight.Bold,
//            color = MaterialTheme.colorScheme.primary,
//        )
//
//        Text(
//            text = "Ready to transfer vouchers securely.",
//            style = MaterialTheme.typography.bodyLarge,
//            textAlign = TextAlign.Center,
//            color = MaterialTheme.colorScheme.onSurfaceVariant,
//        )
//
//        Spacer(modifier = Modifier.height(48.dp))
//
//        // Quantity Input Field
//        OutlinedTextField(
//            value = quantity,
//            onValueChange = { newValue ->
//                // Only allow numbers
//                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
//                    quantity = newValue
//                    showError = false
//                }
//            },
//            label = { Text("Number of Vouchers") },
//            placeholder = { Text("Enter quantity") },
//            modifier = Modifier.fillMaxWidth(),
//            singleLine = true,
//            isError = showError,
//            supportingText = {
//                if (showError) {
//                    Text(
//                        text = "Please enter a valid quantity (1 or more)",
//                        color = MaterialTheme.colorScheme.error,
//                    )
//                }
//            },
//            leadingIcon = {
//                Icon(
//                    imageVector = Icons.Rounded.ConfirmationNumber,
//                    contentDescription = null,
//                )
//            },
//            keyboardOptions =
//                KeyboardOptions(
//                    keyboardType = KeyboardType.Number,
//                    imeAction = ImeAction.Done,
//                ),
//            keyboardActions =
//                KeyboardActions(
//                    onDone = {
//                        val qty = quantity.toIntOrNull()
//                        if (qty != null && qty > 0) {
//                            onSendVouchers(qty)
//                        } else {
//                            showError = true
//                        }
//                    },
//                ),
//            shape = RoundedCornerShape(16.dp),
//        )
//
//        Spacer(modifier = Modifier.height(24.dp))
//
//        Button(
//            onClick = {
//                val qty = quantity.toIntOrNull()
//                if (qty != null && qty > 0) {
//                    onSendVouchers(qty)
//                } else {
//                    showError = true
//                }
//            },
//            modifier =
//                Modifier
//                    .fillMaxWidth()
//                    .height(64.dp),
//            shape = RoundedCornerShape(20.dp),
//            contentPadding = PaddingValues(horizontal = 24.dp),
//            enabled = quantity.isNotEmpty(),
//        ) {
//            Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = null)
//            Spacer(modifier = Modifier.width(12.dp))
//            Text(text = "Transfer Vouchers Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
//        }
//    }
// }
//
// @Composable
// fun ReceiverWaitingScreen() {
//    Column(
//        modifier =
//            Modifier
//                .fillMaxSize()
//                .padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center,
//    ) {
//        Box(contentAlignment = Alignment.Center) {
//            CircularProgressIndicator(
//                modifier = Modifier.size(140.dp),
//                strokeWidth = 8.dp,
//                color = MaterialTheme.colorScheme.primary,
//                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
//            )
//            Icon(
//                imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
//                contentDescription = null,
//                modifier = Modifier.size(48.dp),
//                tint = MaterialTheme.colorScheme.primary,
//            )
//        }
//
//        Spacer(modifier = Modifier.height(40.dp))
//
//        Text(
//            text = "Waiting for Connection",
//            style = MaterialTheme.typography.headlineSmall,
//            fontWeight = FontWeight.Bold,
//        )
//
//        Spacer(modifier = Modifier.height(8.dp))
//
//        Text(
//            text = "Make sure your Bluetooth is visible and the sender is scanning.",
//            style = MaterialTheme.typography.bodyLarge,
//            textAlign = TextAlign.Center,
//            color = MaterialTheme.colorScheme.onSurfaceVariant,
//        )
//    }
// }
//
// @Composable
// fun ModeSelectionScreen(
//    onSenderSelected: () -> Unit,
//    onReceiverSelected: () -> Unit,
// ) {
//    Column(
//        modifier =
//            Modifier
//                .fillMaxSize()
//                .padding(24.dp)
//                .background(
//                    Brush.verticalGradient(
//                        colors =
//                            listOf(
//                                MaterialTheme.colorScheme.surface,
//                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
//                            ),
//                    ),
//                ),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center,
//    ) {
//        Text(
//            text = "Bulk Transfer",
//            fontSize = 40.sp,
//            fontWeight = FontWeight.Black,
//            color = MaterialTheme.colorScheme.primary,
//            letterSpacing = (-1).sp,
//        )
//        Text(
//            text = "Fast. Secure. Peer-to-Peer.",
//            style = MaterialTheme.typography.titleMedium,
//            color = MaterialTheme.colorScheme.secondary,
//        )
//
//        Spacer(modifier = Modifier.height(64.dp))
//
//        ModeCard(
//            title = "I want to Send",
//            subtitle = "Scan and select a device to connect",
//            icon = Icons.Rounded.Search,
//            containerColor = MaterialTheme.colorScheme.primary,
//            contentColor = MaterialTheme.colorScheme.onPrimary,
//            onClick = onSenderSelected,
//        )
//
//        Spacer(modifier = Modifier.height(20.dp))
//
//        ModeCard(
//            title = "I want to Receive",
//            subtitle = "Wait for incoming connection",
//            icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
//            containerColor = MaterialTheme.colorScheme.tertiary,
//            contentColor = MaterialTheme.colorScheme.onTertiary,
//            onClick = onReceiverSelected,
//        )
//    }
// }
//
// @Composable
// fun ModeCard(
//    title: String,
//    subtitle: String,
//    icon: ImageVector,
//    containerColor: Color,
//    contentColor: Color,
//    onClick: () -> Unit,
// ) {
//    Card(
//        onClick = onClick,
//        modifier =
//            Modifier
//                .fillMaxWidth()
//                .height(120.dp),
//        shape = RoundedCornerShape(28.dp),
//        colors =
//            CardDefaults.cardColors(
//                containerColor = containerColor,
//                contentColor = contentColor,
//            ),
//        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
//    ) {
//        Row(
//            modifier =
//                Modifier
//                    .fillMaxSize()
//                    .padding(24.dp),
//            verticalAlignment = Alignment.CenterVertically,
//        ) {
//            Surface(
//                modifier = Modifier.size(56.dp),
//                shape = CircleShape,
//                color = contentColor.copy(alpha = 0.2f),
//            ) {
//                Box(contentAlignment = Alignment.Center) {
//                    Icon(
//                        imageVector = icon,
//                        contentDescription = null,
//                        modifier = Modifier.size(28.dp),
//                    )
//                }
//            }
//
//            Spacer(modifier = Modifier.width(20.dp))
//
//            Column {
//                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
//                Text(text = subtitle, fontSize = 14.sp, color = contentColor.copy(alpha = 0.8f))
//            }
//        }
//    }
// }
