package com.example.vpncat

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.vpncat.ui.theme.ServerSelectionScreen
import com.example.vpncat.wireguardvpnapp.VpnConnectionStatus
import com.example.vpncat.wireguardvpnapp.VpnViewModel
import com.example.vpncat.ui.theme.VPNCatTheme
import com.example.vpncat.wireguardvpnapp.MyVpnService

class MainActivity : ComponentActivity() {
    private lateinit var vpnStatusReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VPNCatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: VpnViewModel = viewModel()
                    val context = LocalContext.current
                    val navController = rememberNavController() // Initialize NavController

                    // Register BroadcastReceiver to listen for VPN status updates from MyVpnService
                    DisposableEffect(viewModel) {
                        vpnStatusReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                if (intent?.action == MyVpnService.ACTION_VPN_STATUS_UPDATE) {
                                    val statusString = intent.getStringExtra(MyVpnService.EXTRA_VPN_STATUS)
                                    val vpnName = intent.getStringExtra(MyVpnService.EXTRA_CONNECTED_VPN_NAME)
                                    val status = VpnConnectionStatus.valueOf(statusString ?: VpnConnectionStatus.Disconnected.name)
                                    viewModel.updateVpnStatusFromService(status, vpnName)
                                    Log.d("MainActivity", "Received VPN status update: $status, name: $vpnName")
                                }
                            }
                        }
                        LocalBroadcastManager.getInstance(context)
                            .registerReceiver(vpnStatusReceiver, IntentFilter(MyVpnService.ACTION_VPN_STATUS_UPDATE))

                        onDispose {
                            LocalBroadcastManager.getInstance(context).unregisterReceiver(vpnStatusReceiver)
                        }
                    }

                    // Setup Navigation Host
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            // VpnAppScreen is your main/home screen
                            VpnAppScreen(viewModel = viewModel, navController = navController)
                        }
                        composable("server_selection") {
                            ServerSelectionScreen(navController = navController, viewModel = viewModel)
                        }
                        // You can add more composable destinations here for "Mua" and "Thêm"
                    }
                }
            }
        }
    }
}

/**
 * This is your main/home screen Composable.
 * It displays the VPN toggle button, status, and navigation elements.
 */
@Composable
fun VpnAppScreen(viewModel: VpnViewModel, navController: NavController) {
    val context = LocalContext.current
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val connectedVpnName by viewModel.connectedVpnName.collectAsState()
    val selectedVpnConfig by viewModel.selectedVpnConfig.collectAsState()
    val vpnPermissionRequired by viewModel.vpnPermissionRequired.collectAsState()

    // Launcher for VPN permission request
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted(context)
        } else {
            viewModel.onVpnPermissionDenied()
            Toast.makeText(context, "VPN permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Observe vpnPermissionRequired to launch permission intent
    LaunchedEffect(vpnPermissionRequired) {
        if (vpnPermissionRequired) {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                viewModel.onVpnPermissionGranted(context)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {


        // Header/Logo
        Column(
            modifier = Modifier.padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CATVPN",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
        }

        // Main VPN Toggle Button
        VpnToggleButton(
            vpnStatus = vpnStatus,
            connectedVpnName = connectedVpnName,
            onClick = { viewModel.toggleVpnConnection(context) }
        )

        // Mode/Server Selection Button
        ServerSelectionButton(selectedServerName = selectedVpnConfig?.name ?: "Chưa chọn") {
            navController.navigate("server_selection") // Navigate to server selection screen
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Navigation
        BottomNavigationBar()
    }
}

@Composable
fun VpnToggleButton(
    vpnStatus: VpnConnectionStatus,
    connectedVpnName: String?,
    onClick: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = when (vpnStatus) {
            VpnConnectionStatus.Connecting -> 100f
            VpnConnectionStatus.Connected -> 100f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing), label = "vpnProgress"
    )

    val buttonColor = when (vpnStatus) {
        VpnConnectionStatus.Disconnected, VpnConnectionStatus.Error -> listOf(Color(0xFF8A2BE2), Color(0xFF4B0082)) // Purple to Indigo
        VpnConnectionStatus.Connecting -> listOf(Color(0xFFFFA500), Color(0xFFFF4500)) // Orange to OrangeRed
        VpnConnectionStatus.Connected -> listOf(Color(0xFF32CD32), Color(0xFF006400)) // LimeGreen to DarkGreen
        VpnConnectionStatus.Disconnecting -> listOf(Color(0xFFDC143C), Color(0xFF8B0000)) // Crimson to DarkRed
    }

    val ringColor = when (vpnStatus) {
        VpnConnectionStatus.Disconnected, VpnConnectionStatus.Error -> Color.Gray.copy(alpha = 0.5f)
        VpnConnectionStatus.Connecting -> Color(0xFFFFA500) // Orange
        VpnConnectionStatus.Connected -> Color(0xFF32CD32) // LimeGreen
        VpnConnectionStatus.Disconnecting -> Color(0xFFDC143C) // Crimson
    }

    val iconColor = when (vpnStatus) {
        VpnConnectionStatus.Disconnected -> Color.Gray
        VpnConnectionStatus.Connecting -> Color.Yellow
        VpnConnectionStatus.Connected -> Color.Green
        VpnConnectionStatus.Disconnecting -> Color.Red
        VpnConnectionStatus.Error -> Color.Red
    }

    val statusText = when (vpnStatus) {
        VpnConnectionStatus.Disconnected -> "Tăng tốc"
        VpnConnectionStatus.Connecting -> "Đang kết nối..."
        VpnConnectionStatus.Connected -> "Đã kết nối\n${connectedVpnName ?: "Unknown"}"
        VpnConnectionStatus.Disconnecting -> "Đang ngắt kết nối..."
        VpnConnectionStatus.Error -> "Lỗi kết nối"
    }

    Box(
        modifier = Modifier
            .size(240.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Gray.copy(alpha = 0.2f),
                radius = size.minDimension / 2,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Progress ring
        if (vpnStatus == VpnConnectionStatus.Connecting || vpnStatus == VpnConnectionStatus.Connected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360 * (progress / 100f),
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Inner button
        Button(
            onClick = onClick,
            modifier = Modifier
                .size(180.dp)
                .background(Brush.radialGradient(buttonColor), CircleShape),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Toggle VPN",
                    tint = iconColor,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = statusText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ServerSelectionButton(selectedServerName: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp), // Rounded pill shape
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)), // Dark gray
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = "Server Mode",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "chế độ phương tiện | ",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = selectedServerName,
                    color = Color.Gray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Select Server",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun BottomNavigationBar() {
    val selectedItem = remember { mutableStateOf("công tác") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1C), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Icons.Default.ShoppingCart,
            label = "Mua",
            isSelected = selectedItem.value == "Mua"
        ) { selectedItem.value = "Mua" }

        BottomNavItem(
            icon = Icons.Default.Home,
            label = "công tác",
            isSelected = selectedItem.value == "công tác"
        ) { selectedItem.value = "công tác" }

        BottomNavItem(
            icon = Icons.Default.MoreHoriz,
            label = "Thêm",
            isSelected = selectedItem.value == "Thêm"
        ) { selectedItem.value = "Thêm" }
    }
}

@Composable
fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val iconTint = if (isSelected) Color(0xFF8A2BE2) else Color.Gray
        val labelColor = if (isSelected) Color(0xFF8A2BE2) else Color.Gray

        if (isSelected) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF8A2BE2).copy(alpha = 0.2f), CircleShape)
                    .padding(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
            }
        } else {
            Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, color = labelColor)
    }
}
