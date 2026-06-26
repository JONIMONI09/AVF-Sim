package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

@Composable
fun SetupWizardScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showPermissions by remember { mutableStateOf(true) }
    var showAnalysis by remember { mutableStateOf(false) }

    // States for Permissions
    var hasNotifications by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    var hasStorage by remember { mutableStateOf(false) }
    var hasBattery by remember { mutableStateOf(false) }

    // Analysis Results
    var isKvmSupported by remember { mutableStateOf<Boolean?>(null) }
    var isCrosvmFound by remember { mutableStateOf<Boolean?>(null) }
    var isShizukuActive by remember { mutableStateOf<Boolean?>(null) }
    var isRootActive by remember { mutableStateOf<Boolean?>(null) }
    var analysisDone by remember { mutableStateOf(false) }

    // Permissions Launchers
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        hasBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasStorage = Environment.isExternalStorageManager()
        } else {
            hasStorage = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStorage = granted
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotifications = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    hasStorage = Environment.isExternalStorageManager()
                } else {
                    hasStorage = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                hasBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Refresh states on load
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotifications = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasStorage = Environment.isExternalStorageManager()
        } else {
            hasStorage = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        hasBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "AVF Manager Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Um virtuelle Maschinen im Hintergrund auszuführen und Images zu speichern, benötigen wir ein paar Berechtigungen.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (showPermissions) {
            // Notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = "Benachrichtigungen",
                    description = "Halte die VM am Leben mit einer Foreground-Benachrichtigung.",
                    isGranted = hasNotifications,
                    onRequest = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            // Storage
            PermissionItem(
                title = "Voller Speicherzugriff",
                description = "Notwendig um ISO/RAW Images zu lesen und zu schreiben.",
                isGranted = hasStorage,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            storageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            storageLauncher.launch(intent)
                        }
                    } else {
                        legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            )

            // Battery
            PermissionItem(
                title = "Akku-Optimierungen ignorieren",
                description = "Erlaubt der VM mit voller CPU-Leistung dauerhaft im Hintergrund zu laufen.",
                isGranted = hasBattery,
                onRequest = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    batteryLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { 
                    showPermissions = false
                    showAnalysis = true
                    
                    scope.launch {
                        delay(500)
                        isKvmSupported = File("/dev/kvm").exists()
                        delay(400)
                        isCrosvmFound = File("/apex/com.android.virt/bin/crosvm").exists()
                        delay(600)
                        isShizukuActive = try { Shizuku.getVersion() > 0 } catch(e: Throwable) { false }
                        delay(300)
                        
                        // Safe root checking - try which su first, then search common directories
                        val rootObj = try {
                            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
                            val exitVal = kotlinx.coroutines.withTimeoutOrNull(300) {
                                process.waitFor()
                            }
                            if (exitVal == 0) {
                                true
                            } else {
                                val paths = arrayOf(
                                    "/system/bin/su", "/system/xbin/su", "/sbin/su", 
                                    "/system/sd/xbin/su", "/system/bin/failsafe/su", 
                                    "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su"
                                )
                                paths.any { File(it).exists() }
                            }
                        } catch (e: Exception) {
                            false
                        }
                        isRootActive = rootObj

                        delay(500)
                        analysisDone = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Weiter zur Geräte-Analyse", fontSize = 16.sp)
            }
        }

        if (showAnalysis) {
            Text("System Analyse", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

            var diagnosticText by remember { mutableStateOf("") }

            LaunchedEffect(analysisDone) {
                 if (analysisDone) {
                      diagnosticText = DiagnosticHelper.getDiagnosticText(context)
                 }
            }

            if (diagnosticText.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diagnosticText,
                        color = Color.White,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (analysisDone) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val optimal = isKvmSupported == true && (isShizukuActive == true || isRootActive == true || isCrosvmFound == true)
                    Text(
                        if (optimal) "Gerät ist optimal für Hardware-Virtualisierung geeignet!" else "Achtung: Einige Virtualisierungs-Komponenten fehlen. Die App nutzt Fallbacks.",
                        color = if (optimal) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = onSetupComplete,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("App Starten", fontSize = 16.sp)
                    }
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String, isGranted: Boolean, onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Erteilt", tint = Color(0xFF4CAF50))
            } else {
                Button(onClick = onRequest) {
                    Text("Erlauben")
                }
            }
        }
    }
}

@Composable
fun AnalysisItem(title: String, result: Boolean?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        when (result) {
            null -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            true -> Icon(Icons.Default.CheckCircle, contentDescription = "Vorhanden", tint = Color(0xFF4CAF50))
            false -> Icon(Icons.Default.Warning, contentDescription = "Fehlt", tint = Color(0xFFF44336))
        }
    }
}
