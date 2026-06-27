package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import rikka.shizuku.Shizuku
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val shizukuRequestCode = 1421
    
    companion object {
        init {
            try {
                System.loadLibrary("avfsimulator")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    external fun executeNativeCommand(command: String): Int
    external fun forkAndExec(command: String): Int

    private val binderReceivedListener = object : Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Shizuku-Binder empfangen!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Listener fuer Shizuku Berechtigungen
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == shizukuRequestCode) {
            val isGranted = grantResult == PackageManager.PERMISSION_GRANTED
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (isGranted) "Shizuku-Berechtigung erteilt!" else "Shizuku-Berechtigung verweigert.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        } catch (e: Throwable) {
            // Shizuku ist nicht geladen oder nicht verfuegbar
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    var isSetupComplete by remember { mutableStateOf(false) }

                    if (!isSetupComplete) {
                        SetupWizardScreen(
                            onSetupComplete = { isSetupComplete = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        AVFSimulatorApp(
                            modifier = Modifier.padding(innerPadding),
                            packageName = packageName,
                            onGrantShizukuPermissions = { grantAVFPermissionsWithShizuku(packageName) },
                            onRequestShizukuAuth = { requestShizukuPermission() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Shizuku Status beim Zurueckkehren zur App aktualisieren
        try {
            if (checkShizukuActive()) {
                // Hier koennte man ein UI Refresh triggern falls noetig
            }
        } catch (e: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
        } catch (e: Throwable) {
            // Ignorieren
        }
    }

    private fun checkShizukuActive(): Boolean {
        return try {
            if (Shizuku.pingBinder()) {
                Shizuku.getVersion() > 0
            } else {
                false
            }
        } catch (e: Throwable) {
            false
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (checkShizukuActive()) {
                Shizuku.requestPermission(shizukuRequestCode)
            } else {
                Toast.makeText(this, "Shizuku läuft nicht! Bitte starten Sie die Shizuku App.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Shizuku-API nicht installierbar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun grantAVFPermissionsWithShizuku(pkg: String) {
        try {
            if (!checkShizukuActive()) {
                Toast.makeText(this, "Shizuku ist nicht aktiv oder läuft nicht!", Toast.LENGTH_LONG).show()
                return
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(shizukuRequestCode)
                return
            }

            // Berechtigungen via PM Grant Befehlen erteilen
            val commands = listOf(
                "pm grant $pkg android.permission.USE_CUSTOM_VIRTUAL_MACHINE",
                "pm grant $pkg android.permission.MANAGE_VIRTUAL_MACHINE"
            )

            var successCount = 0
            for (cmd in commands) {
                val args = arrayOf("sh", "-c", cmd)
                val processObj = try {
                    // Try different Shizuku.newProcess signatures as they can vary between versions
                    val methods = Shizuku::class.java.methods.filter { it.name == "newProcess" }
                    var foundMethod = methods.find { 
                        it.parameterTypes.size == 3 && 
                        it.parameterTypes[0] == Array<String>::class.java &&
                        it.parameterTypes[1] == Array<String>::class.java &&
                        it.parameterTypes[2] == String::class.java
                    }
                    
                    if (foundMethod == null) {
                         // Try 2-parameter version if 3-parameter fails
                         foundMethod = methods.find {
                             it.parameterTypes.size == 2 &&
                             it.parameterTypes[0] == Array<String>::class.java &&
                             it.parameterTypes[1] == Array<String>::class.java
                         }
                    }

                    if (foundMethod != null) {
                        if (foundMethod.parameterTypes.size == 3) {
                            foundMethod.invoke(null, args, null, null) as? Process
                        } else {
                            foundMethod.invoke(null, args, null) as? Process
                        }
                    } else {
                        null
                    }
                } catch (ex: Throwable) {
                    null
                }
                
                if (processObj != null) {
                    val exitCode = processObj.waitFor()
                    if (exitCode == 0) {
                        successCount++
                    }
                }
            }

            if (successCount == commands.size) {
                Toast.makeText(this, "AVF-Berechtigungen erfolgreich über Shizuku erteilt!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Befehl ausgeführt, aber Berechtigungserteilung fehlgeschlagen (Fehlercode ungleich 0).", Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Shizuku Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

// OS Image Optionen für vordefinierte System-Profile
enum class OSImage(val displayName: String, val kernelName: String, val bBootTime: Long) {
    MICRODROID("Microdroid Secure OS", "microdroid-kernel-6.1", 3000L),
    ALPINE_LINUX("Alpine Linux (LTS Kernel)", "alpine-virt-kernel", 4000L),
    DEBIAN_ARM64("Debian Stable (ARM64 pKVM)", "debian-linux-6.6", 5000L)
}

enum class VMState {
    STOPPED, BOOTING, RUNNING, ERROR
}

// Log-Eintrag Datenstruktur
data class LogLine(val timestamp: String, val message: String, val isError: Boolean = false, val isSuccess: Boolean = false)

// VM Custom-Profil Klasse
data class VMProfile(
    val id: String,
    val name: String,
    val ramMb: Int = 1024,
    val cpuCores: Int = 2,
    val cpuModel: String = "Host-Pass-Through",
    val primaryDiskPath: String = "",
    val secondaryDiskPath: String = "",
    val bootSource: String = "CD-ROM / ISO", // "CD-ROM / ISO", "HDD / Image", "Kernel (Intern)"
    val isProtected: Boolean = true,
    val isDebuggable: Boolean = true,
    val networkMode: String = "User / NAT", // "User / NAT", "Bridged", "Keine"
    val vncPort: Int = 5900,
    val enableVioSound: Boolean = false,
    val enableVioGpu: Boolean = true,
    val extraArgs: String = ""
)

@Composable
fun AVFSimulatorApp(
    modifier: Modifier = Modifier,
    packageName: String,
    onGrantShizukuPermissions: () -> Unit,
    onRequestShizukuAuth: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // -------------------------------------------------------------
    // Echtzeit System-Eigenschaften-Detektion
    // -------------------------------------------------------------
    var hasCustomPerm by remember { mutableStateOf(false) }
    var hasManagePerm by remember { mutableStateOf(false) }
    var kvmModuleExists by remember { mutableStateOf(false) }
    var hypervisorPropValue by remember { mutableStateOf("Unbekannt") }
    var isShizukuAvailable by remember { mutableStateOf(false) }
    var isShizukuAuthorized by remember { mutableStateOf(false) }

    // Steuerung für Simulations-Modus (Überbrückt Berechtigungen auf ungerooteten Emulatoren)
    var forceSimulationMode by remember { mutableStateOf(true) }

    // Tab-Steuerung
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("VM-Profile", "Festplatten", "VM Display", "Hypervisor Terminal", "Status & KVM")
    
    // Globale App-Einstellungen
    var globalSelectedBackend by remember { mutableStateOf("auto") }

    // VM-Profile State
    val profilesList = remember { mutableStateListOf<VMProfile>() }
    var selectedProfileId by remember { mutableStateOf("") }
    
    // Editor State für das aktuell ausgewählte Profil
    var editName by remember { mutableStateOf("") }
    var editRam by remember { mutableStateOf(1024) }
    var editRamInputStr by remember { mutableStateOf("1024") }
    var editCores by remember { mutableStateOf(2) }
    var editCoresInputStr by remember { mutableStateOf("2") }
    var editCpuModel by remember { mutableStateOf("Host-Pass-Through") }
    var editPrimaryDisk by remember { mutableStateOf("") }
    var editSecondaryDisk by remember { mutableStateOf("") }
    var editBootSource by remember { mutableStateOf("CD-ROM / ISO") }
    var editIsProtected by remember { mutableStateOf(true) }
    var editIsDebuggable by remember { mutableStateOf(true) }
    var editNetworkMode by remember { mutableStateOf("User / NAT") }
    var editVncPort by remember { mutableStateOf(5900) }
    var editEnableSound by remember { mutableStateOf(false) }
    var editEnableGpu by remember { mutableStateOf(true) }
    var editExtraArgs by remember { mutableStateOf("") }

    // Festplatten State
    var diskCreatorFilename by remember { mutableStateOf("root_disk.img") }
    var diskCreatorSizeGb by remember { mutableStateOf(10.0f) }
    var diskCreatedLogs by remember { mutableStateOf("") }
    val virtualDisksList = remember { mutableStateListOf<File>() }

    // VM Status
    var vmState by remember { mutableStateOf(VMState.STOPPED) }
    val terminalLogs = remember { mutableStateListOf<LogLine>() }
    val consoleListState = rememberLazyListState()

    // Interaktive Terminal-Eingabe
    var terminalInputCmd by remember { mutableStateOf("") }

    // Helfer zur Suche der existierenden Disketten
    fun reloadVirtualDisksList() {
        try {
            val baseExternalDir = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(baseExternalDir, "VirtualDisks")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            virtualDisksList.clear()
            val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".img") || f.name.endsWith(".iso") || f.name.endsWith(".qcow2") || f.name.endsWith(".bin")) }
            if (files != null) {
                virtualDisksList.addAll(files.toList())
            }
        } catch (e: Throwable) {
            // Ignorieren
        }
    }

    // Persistenz der VM Profile
    fun saveProfiles() {
        val prefs = context.getSharedPreferences("AVF_SAVED_PROFILES", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("profile_count", profilesList.size)
        profilesList.forEachIndexed { index, p ->
            editor.putString("profile_${index}_id", p.id)
            editor.putString("profile_${index}_name", p.name)
            editor.putInt("profile_${index}_ramMb", p.ramMb)
            editor.putInt("profile_${index}_cpuCores", p.cpuCores)
            editor.putString("profile_${index}_cpuModel", p.cpuModel)
            editor.putString("profile_${index}_primaryDiskPath", p.primaryDiskPath)
            editor.putString("profile_${index}_secondaryDiskPath", p.secondaryDiskPath)
            editor.putString("profile_${index}_bootSource", p.bootSource)
            editor.putBoolean("profile_${index}_isProtected", p.isProtected)
            editor.putBoolean("profile_${index}_isDebuggable", p.isDebuggable)
            editor.putString("profile_${index}_networkMode", p.networkMode)
            editor.putInt("profile_${index}_vncPort", p.vncPort)
            editor.putBoolean("profile_${index}_enableVioSound", p.enableVioSound)
            editor.putBoolean("profile_${index}_enableVioGpu", p.enableVioGpu)
            editor.putString("profile_${index}_extraArgs", p.extraArgs)
        }
        editor.apply()
    }

    fun loadProfiles() {
        val prefs = context.getSharedPreferences("AVF_SAVED_PROFILES", Context.MODE_PRIVATE)
        val count = prefs.getInt("profile_count", -1)
        profilesList.clear()
        if (count == -1) {
            // Initiale Standard-Profile
            val baseExternalDir = context.getExternalFilesDir(null) ?: context.filesDir
            val defaultDir = File(baseExternalDir, "VirtualDisks").absolutePath
            profilesList.add(VMProfile("1", "Alpine Linux Server", 1024, 2, "Host-Pass-Through", "$defaultDir/alpine_root.img", "$defaultDir/alpine-virt.iso", "CD-ROM / ISO"))
            profilesList.add(VMProfile("2", "Debian ARM64 GUI", 2048, 4, "Cortex-A72", "$defaultDir/debian_root.img", "", "HDD / Image"))
            profilesList.add(VMProfile("3", "Microdroid Android VM", 512, 1, "Cortex-A53", "", "", "Kernel (Intern)"))
        } else {
            for (i in 0 until count) {
                val id = prefs.getString("profile_${i}_id", "$i") ?: "$i"
                val name = prefs.getString("profile_${i}_name", "Profil $i") ?: "Profil $i"
                val ramMb = prefs.getInt("profile_${i}_ramMb", 1024)
                val cpuCores = prefs.getInt("profile_${i}_cpuCores", 2)
                val cpuModel = prefs.getString("profile_${i}_cpuModel", "Host-Pass-Through") ?: "Host-Pass-Through"
                val primaryDiskPath = prefs.getString("profile_${i}_primaryDiskPath", "") ?: ""
                val secondaryDiskPath = prefs.getString("profile_${i}_secondaryDiskPath", "") ?: ""
                val bootSource = prefs.getString("profile_${i}_bootSource", "CD-ROM / ISO") ?: "CD-ROM / ISO"
                val isProtected = prefs.getBoolean("profile_${i}_isProtected", true)
                val isDebuggable = prefs.getBoolean("profile_${i}_isDebuggable", true)
                val networkMode = prefs.getString("profile_${i}_networkMode", "User / NAT") ?: "User / NAT"
                val vncPort = prefs.getInt("profile_${i}_vncPort", 5900)
                val enableVioSound = prefs.getBoolean("profile_${i}_enableVioSound", false)
                val enableVioGpu = prefs.getBoolean("profile_${i}_enableVioGpu", true)
                val extraArgs = prefs.getString("profile_${i}_extraArgs", "") ?: ""

                profilesList.add(
                    VMProfile(
                        id, name, ramMb, cpuCores, cpuModel, primaryDiskPath, secondaryDiskPath, 
                        bootSource, isProtected, isDebuggable, networkMode, vncPort, enableVioSound, enableVioGpu, extraArgs
                    )
                )
            }
        }
        
        // Erstes Profil auswählen falls vorhanden
        if (profilesList.isNotEmpty()) {
            selectedProfileId = profilesList[0].id
        }
    }

    // Funktion um Logs hinzuzufügen mit automatischem Scrollen
    fun addLog(msg: String, isError: Boolean = false, isSuccess: Boolean = false) {
        val dateStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        terminalLogs.add(LogLine(dateStr, msg, isError, isSuccess))
        scope.launch {
            if (terminalLogs.isNotEmpty()) {
                consoleListState.animateScrollToItem(terminalLogs.size - 1)
            }
        }
    }

    // System-Zustand beim Starten und Aktualisieren abfragen
    fun updateSystemStatus() {
        try {
            hasCustomPerm = context.checkSelfPermission("android.permission.USE_CUSTOM_VIRTUAL_MACHINE") == PackageManager.PERMISSION_GRANTED
            hasManagePerm = context.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE") == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            hasCustomPerm = false
            hasManagePerm = false
        }

        try {
            val file = File("/sys/module/kvm")
            kvmModuleExists = file.exists()
        } catch (e: Throwable) {
            kvmModuleExists = false
        }

        try {
            val process = Runtime.getRuntime().exec("getprop ro.boot.hypervisor.version")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val outputLine: String? = reader.readLine()
            reader.close()
            process.destroy()
            hypervisorPropValue = if (!outputLine.isNullOrBlank()) {
                outputLine
            } else {
                "Nicht konfiguriert (Simuliert)"
            }
        } catch (e: Throwable) {
            hypervisorPropValue = "Zugriff verweigert (Simuliert)"
        }

        try {
            isShizukuAvailable = try { Shizuku.getVersion() > 0 } catch (ex: Throwable) { false }
            isShizukuAuthorized = if (isShizukuAvailable) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        } catch (e: Throwable) {
            isShizukuAvailable = false
            isShizukuAuthorized = false
        }
    }

    // Beim Laden
    LaunchedEffect(Unit) {
        updateSystemStatus()
        loadProfiles()
        reloadVirtualDisksList()
        addLog("KVM-Hypervisor Schnittstellenterminal einsatzbereit.")
        addLog("Status KVM-Kernelmodul: ${if (kvmModuleExists) "Detektiert & Aktiviert" else "Deaktiviert (Simulierter pKVM Modus)"}")
    }

    // Update der Editor-Eingaben beim Wechsel des ausgewählten Profils
    LaunchedEffect(selectedProfileId) {
        val current = profilesList.find { it.id == selectedProfileId }
        if (current != null) {
            editName = current.name
            editRam = current.ramMb
            editRamInputStr = current.ramMb.toString()
            editCores = current.cpuCores
            editCoresInputStr = current.cpuCores.toString()
            editCpuModel = current.cpuModel
            editPrimaryDisk = current.primaryDiskPath
            editSecondaryDisk = current.secondaryDiskPath
            editBootSource = current.bootSource
            editIsProtected = current.isProtected
            editIsDebuggable = current.isDebuggable
            editNetworkMode = current.networkMode
            editVncPort = current.vncPort
            editEnableSound = current.enableVioSound
            editEnableGpu = current.enableVioGpu
            editExtraArgs = current.extraArgs
        }
    }

    // SAF File Picker
    val primaryDiskPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    try {
                        addLog("Importiere Festplatten-Image: $uri ...")
                        val localPath = StorageHelper.copyUriToInternalStorage(context, uri)
                        if (localPath != null) {
                            editPrimaryDisk = localPath
                            addLog("Erfolgreich importiert nach: $localPath", isSuccess = true)
                            reloadVirtualDisksList()
                        } else {
                            addLog("Fehler beim Importieren des Images!", isError = true)
                        }
                    } catch (e: Exception) {
                        addLog("Ausnahme beim Importieren: ${e.message}", isError = true)
                    }
                }
            }
        }
    )

    val secondaryDiskPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    try {
                        addLog("Importiere CD-ROM/ISO: $uri ...")
                        val localPath = StorageHelper.copyUriToInternalStorage(context, uri)
                        if (localPath != null) {
                            editSecondaryDisk = localPath
                            addLog("Erfolgreich importiert nach: $localPath", isSuccess = true)
                            reloadVirtualDisksList()
                        } else {
                            addLog("Fehler beim Importieren des ISO-Images!", isError = true)
                        }
                    } catch (e: Exception) {
                        addLog("Ausnahme beim ISO-Import: ${e.message}", isError = true)
                    }
                }
            }
        }
    )

    // Funktion um echte leere Festplatte zu erstellen (Sparse File)
    fun generateDiskImage(name: String, sizeGb: Float) {
        scope.launch {
            try {
                if (name.isBlank() || !name.contains(".")) {
                    diskCreatedLogs = "Fehler: Ungültiger Dateiname (Bsp: mydisk.img)"
                    return@launch
                }
                diskCreatedLogs = "Erstelle Sparse Festplatten-Image..."
                delay(200)
                val baseExternalDir = context.getExternalFilesDir(null) ?: context.filesDir
                val dir = File(baseExternalDir, "VirtualDisks")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val diskFile = File(dir, name)
                if (diskFile.exists()) {
                    diskCreatedLogs = "Fehler: Eine Datei mit diesem Namen existiert bereits!"
                    return@launch
                }
                val sizeInBytes = (sizeGb * 1024L * 1024L * 1024L).toLong()
                
                // Sparse-File Generation
                val raf = RandomAccessFile(diskFile, "rw")
                raf.setLength(sizeInBytes)
                raf.close()

                diskCreatedLogs = "Erfolgreich erstellt!\nPfad: ${diskFile.absolutePath}\nVirtuelle Kapazität: ${sizeGb} GB"
                reloadVirtualDisksList()
            } catch (e: Throwable) {
                diskCreatedLogs = "Ausnahmefehler: ${e.message}"
            }
        }
    }

    fun stopVMService() {
        val serviceIntent = android.content.Intent(context, VMForegroundService::class.java)
        context.stopService(serviceIntent)
    }

    // -------------------------------------------------------------
    // Boot-Sequenz der ausgewählten VM (ECHTE AUSFÜHRUNG)
    // -------------------------------------------------------------
    fun bootVirtualMachine(profile: VMProfile) {
        scope.launch {
            vmState = VMState.BOOTING
            
            // Starte Foreground Service für Hintergrund-Betrieb und Wakelock
            val serviceIntent = android.content.Intent(context, VMForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            terminalLogs.clear()
            addLog("=== INITIATING NATIVE VM BOOT ===", isSuccess = true)
            addLog("Checking hypervisor capabilities (/dev/kvm)...")

            // Determine virtualization backend
            val useCrosvm = globalSelectedBackend == "avf" || (globalSelectedBackend == "auto" && (profile.extraArgs.contains("--use-crosvm") || java.io.File("/apex/com.android.virt/bin/crosvm").exists()))
            val useProot = globalSelectedBackend == "proot"
            
            // Build absolute paths
            val cleanPrimaryPath = profile.primaryDiskPath
            val rootDiskParams = if (cleanPrimaryPath.isNotBlank()) {
                "-drive file=\"$cleanPrimaryPath\",if=virtio,format=raw "
            } else ""

            val cdromParams = if (profile.bootSource.isNotBlank() && profile.bootSource.contains(".iso", ignoreCase = true)) {
                "-cdrom \"${profile.bootSource}\" "
            } else ""

            // Mouse Settings (Tablet = Absolute positioning, Mouse = Relative positioning)
            val mouseType = if (profile.extraArgs.contains("--relative-mouse")) {
                "-usb -device usb-mouse"
            } else {
                "-usb -device usb-tablet" // Empfohlen für Touchscreens/VNC
            }

            val hardwareAcceleration = if (profile.enableVioGpu) {
                "-device virtio-gpu-pci -display vnc=127.0.0.1:${profile.vncPort - 5900},websocket=5700" // VNC ports are offset by 5900 in QEMU. Websocket on 5700.
            } else {
                "-nographic"
            }

            // The Command Construction
            val cmd = if (useProot) {
                addLog("Modus: PRoot Wrapper (Podroid-Style Direct CPU Execution)")
                val prootPath = "/data/data/${context.packageName}/files/proot"
                val rootfs = cleanPrimaryPath.ifBlank { "/data/data/${context.packageName}/files/rootfs" }
                
                // Enhanced Podroid environment
                val envVars = "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin " +
                             "TERM=xterm-256color " +
                             "HOME=/root " +
                             "USER=root " +
                             "LANG=en_US.UTF-8 "
                
                val mounts = "-b /dev -b /sys -b /proc -b /dev/shm -b /data/data/${context.packageName}/files:/tmp "
                
                "if [ -f \"$prootPath\" ]; then $envVars \"$prootPath\" -0 -r \"$rootfs\" $mounts -w /root /bin/bash -c \"echo 'Podroid-Style Session gestartet...'; exec /bin/bash\"; else echo \"FEHLER: PRoot Binary nicht unter $prootPath gefunden.\"; exit 1; fi"
            } else if (useCrosvm) {
                addLog("Modus: AVF / pKVM (Crosvm)")
                // Echte AVF Crosvm Executable
                val gpuArg = if(profile.enableVioGpu) "--gpu vulkan " else ""
                val biosArg = if(cdromParams.isNotBlank()) "--bios \"${profile.bootSource}\" " else ""
                "chmod 666 /dev/kvm; /apex/com.android.virt/bin/crosvm run --disable-sandbox $gpuArg --mem ${profile.ramMb} --cpus ${profile.cpuCores} --rwdisk \"$cleanPrimaryPath\" $biosArg"
            } else {
                addLog("Modus: QEMU (Fallback)")
                // QEMU Command (Echt, abhängig von statischem QEMU Binary. Fallback zu Ausgabe falls nicht installiert).
                // Podroid/Limbo verwendet statisch kompilierte QEMU binaries.
                val qemuPath = "/data/data/${context.packageName}/files/qemu-system-aarch64"
                "chmod 666 /dev/kvm; if [ -f \"$qemuPath\" ]; then \"$qemuPath\" -M virt -cpu host -enable-kvm -m ${profile.ramMb} -smp ${profile.cpuCores} $hardwareAcceleration $mouseType $rootDiskParams $cdromParams; else echo \"FEHLER: QEMU Binary nicht unter $qemuPath gefunden. Lade ein QEMU Binary herunter oder verwende --use-crosvm in den Extra-Argumenten.\"; exit 1; fi"
            }

            addLog("Executing Virtualization Backend...")
            addLog("> $cmd")
            delay(500)

            val isShizukuReady = isShizukuAvailable && isShizukuAuthorized
            if (!isShizukuReady) {
                addLog("Hinweis: Shizuku ist nicht aktiv. Nutze native Laufzeitumgebung...", isError = false)
            }

            try {
                if (globalSelectedBackend == "proot" && !isShizukuReady) {
                    addLog("Nutze C++ JNI Engine (Native Wrapper) für Prozess-Isolierung...")
                    // Starte Prozess im Hintergrund via C++
                    scope.launch {
                        try {
                            val exitCode = (context as MainActivity).forkAndExec(cmd)
                            addLog("=== C++ Wrapper Session beendet (Exit-Code: $exitCode) ===", isError = exitCode != 0, isSuccess = exitCode == 0)
                            vmState = VMState.STOPPED
                            stopVMService()
                        } catch (e: Exception) {
                            addLog("C++ Wrapper Error: ${e.message}", isError = true)
                            vmState = VMState.ERROR
                            stopVMService()
                        }
                    }
                    vmState = VMState.RUNNING
                    return@launch
                }
                
                val processObj: Process? = if (isShizukuReady) {
                    val sh = Shizuku::class.java.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    sh.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
                } else {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                }

                if (processObj == null) {
                    addLog("Kritischer Fehler: Der Prozess konnte nicht instanziiert werden (System gab null zurück).", isError = true)
                    vmState = VMState.ERROR
                    stopVMService()
                    return@launch
                }

                vmState = VMState.RUNNING

                // Lese Standard-Output
                scope.launch {
                    try {
                        val inputStream = processObj.inputStream
                        if (inputStream != null) {
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                addLog("[VM] $line")
                            }
                        }
                    } catch (e: Exception) {
                        addLog("[VM IO Error] ${e.message}", isError = true)
                    }
                }

                // Lese Error-Output
                scope.launch {
                    try {
                        val errorStream = processObj.errorStream
                        if (errorStream != null) {
                            val reader = BufferedReader(InputStreamReader(errorStream))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                addLog("[VM ERR] $line", isError = true)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                // Warte auf VM Ende
                scope.launch {
                    val exitCode = processObj.waitFor()
                    addLog("=== Virtuelle Maschine beendet (Exit-Code: $exitCode) ===", isError = exitCode != 0, isSuccess = exitCode == 0)
                    vmState = VMState.STOPPED
                    stopVMService()
                }

            } catch (e: Throwable) {
                addLog("Systemfehler beim Starten des VM Prozesses via Shizuku: ${e.message}", isError = true)
                vmState = VMState.ERROR
                stopVMService()
            }
        }
    }

    // -------------------------------------------------------------
    // ECHTER Terminal Command Interpreter
    // -------------------------------------------------------------
    fun executeConsoleCommand(input: String, profile: VMProfile) {
        val cmd = input.trim()
        if (cmd.isBlank()) return
        
        val isShizukuReady = isShizukuAvailable && isShizukuAuthorized
        val prefix = if (isShizukuReady) "host@shizuku:~$" else "host@native:~$"
        addLog("$prefix $cmd")
        
        scope.launch {
            try {
                val processObj: Process? = if (isShizukuReady) {
                    Shizuku::class.java.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                        .invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
                } else {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                }

                if (processObj == null) {
                    addLog("Fehler: Konsole konnte den Prozess nicht starten.", isError = true)
                    return@launch
                }

                val inputStream = processObj.inputStream
                if (inputStream != null) {
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        addLog(line!!)
                    }
                }
                
                val errorStream = processObj.errorStream
                if (errorStream != null) {
                    val errReader = BufferedReader(InputStreamReader(errorStream))
                    var errLine: String?
                    while (errReader.readLine().also { errLine = it } != null) {
                        addLog(errLine!!, isError = true)
                    }
                }

                processObj.waitFor()
            } catch (e: Throwable) {
                addLog("Konnte Befehl nicht ausführen: ${e.message}", isError = true)
            }
        }
        terminalInputCmd = ""
    }

    val currentProfile = profilesList.find { it.id == selectedProfileId } ?: VMProfile("-", "Kein Profil")

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .statusBarsPadding()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "AVF Hardware Sandbox",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Echte pKVM & Virtuelle QEMU/Limbo Verwaltung • DE",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    var showDiagnosticDialog by remember { mutableStateOf(false) }
                    var showSettingsDialog by remember { mutableStateOf(false) }

                    if (showSettingsDialog) {
                         AlertDialog(
                             onDismissRequest = { showSettingsDialog = false },
                             title = { Text("App Einstellungen", fontWeight = FontWeight.Bold) },
                             text = {
                                 Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                     Text("Backend (wie in Podroid/Wine)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                         RadioButton(selected = globalSelectedBackend == "auto", onClick = { globalSelectedBackend = "auto" })
                                         Text("Auto (AVF, sonst Fallback)")
                                     }
                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                         RadioButton(selected = globalSelectedBackend == "avf", onClick = { globalSelectedBackend = "avf" })
                                         Text("AVF (pKVM)")
                                     }
                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                         RadioButton(selected = globalSelectedBackend == "qemu", onClick = { globalSelectedBackend = "qemu" })
                                         Text("QEMU (Standard Fallback)")
                                     }
                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                         RadioButton(selected = globalSelectedBackend == "proot", onClick = { globalSelectedBackend = "proot" })
                                         Text("PRoot Wrapper (Direct CPU syscalls via Bind Mounts wie Wine, 100% Native Speed)")
                                     }
                                 }
                             },
                             confirmButton = {
                                 TextButton(onClick = { showSettingsDialog = false }) { Text("Schließen") }
                             }
                         )
                    }

                    if (showDiagnosticDialog) {
                         AlertDialog(
                             onDismissRequest = { showDiagnosticDialog = false },
                             title = { Text("AVF (pKVM) diagnostic", fontWeight = FontWeight.Bold) },
                             text = {
                                 Surface(
                                     color = Color(0xFF1E1E1E),
                                     shape = RoundedCornerShape(8.dp)
                                 ) {
                                     Text(
                                         text = DiagnosticHelper.getDiagnosticText(context),
                                         color = Color.LightGray,
                                         fontFamily = FontFamily.Monospace,
                                         fontSize = 12.sp,
                                         modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())
                                     )
                                 }
                             },
                             confirmButton = {
                                 TextButton(onClick = { showDiagnosticDialog = false }) { Text("Close", color = Color(0xFF4CAF50)) }
                             },
                             containerColor = Color(0xFF2B2B2B),
                             titleContentColor = Color.White
                         )
                    }

                    IconButton(
                        onClick = {
                            showSettingsDialog = true
                        },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Einstellungen",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            showDiagnosticDialog = true
                        },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Diagnostics",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Header Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    // TAB 0: PROFILE KONFIGURATOR (Wie Limbo / Podroid)
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Profil-Zuständigkeit (Header)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "Config Mode", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Aktuelle Maschinenkonfiguration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Wählen Sie ein Profil oder legen Sie ein neues an.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            // Profile Dropdown Selector / List
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var showProfileMenu by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = { showProfileMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(currentProfile.name, fontWeight = FontWeight.Bold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showProfileMenu,
                                        onDismissRequest = { showProfileMenu = false }
                                    ) {
                                        profilesList.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.name, fontWeight = FontWeight.SemiBold) },
                                                onClick = {
                                                    selectedProfileId = p.id
                                                    showProfileMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Hinzufügen Knopf
                                Button(
                                    onClick = {
                                        val newId = UUID.randomUUID().toString().take(6)
                                        val newProf = VMProfile(
                                            id = newId,
                                            name = "Premium VM $newId",
                                            ramMb = 1024,
                                            cpuCores = 2,
                                            bootSource = "Kernel (Intern)"
                                        )
                                        profilesList.add(newProf)
                                        selectedProfileId = newId
                                        saveProfiles()
                                        Toast.makeText(context, "Neues Profil erstellt!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Neu")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Neu")
                                }
                            }

                            // Details-Konfigurator
                            if (currentProfile.id != "-") {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Profilname (ID: ${currentProfile.id})") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                // RAM Sektion
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Build, contentDescription = "RAM", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Arbeitsspeicher (RAM)", fontWeight = FontWeight.Bold)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = editRamInputStr,
                                                    onValueChange = { 
                                                        editRamInputStr = it
                                                        val num = it.toIntOrNull()
                                                        if (num != null) editRam = num.coerceIn(128, 16384)
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.width(90.dp),
                                                    singleLine = true,
                                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("MB", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Slider(
                                            value = editRam.toFloat(),
                                            onValueChange = {
                                                editRam = it.toInt()
                                                editRamInputStr = it.toInt().toString()
                                            },
                                            valueRange = 128f..8192f,
                                            steps = 31
                                        )
                                        Text("Zugewiesener LPDDR5X-Speicher für das pKVM-Enclave GuestOS. Podroid empfiehlt mindestens 512 MB.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }

                                // CPU Sektion
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Build, contentDescription = "Cores", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("CPU vCPUs (Virtuelle Kerne)", fontWeight = FontWeight.Bold)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = editCoresInputStr,
                                                    onValueChange = {
                                                        editCoresInputStr = it
                                                        val num = it.toIntOrNull()
                                                        if (num != null) editCores = num.coerceIn(1, 16)
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.width(80.dp),
                                                    singleLine = true,
                                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                        Slider(
                                            value = editCores.toFloat(),
                                            onValueChange = {
                                                editCores = it.toInt()
                                                editCoresInputStr = it.toInt().toString()
                                            },
                                            valueRange = 1f..16f,
                                            steps = 15
                                        )
                                    }
                                }

                                // CPU Model Dropdown Selector
                                var cpuMenuOpen by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = editCpuModel,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Virtuelle Prozessorarchitektur") },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = { cpuMenuOpen = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "CPU Model Selection")
                                            }
                                        }
                                    )
                                    DropdownMenu(expanded = cpuMenuOpen, onDismissRequest = { cpuMenuOpen = false }) {
                                        listOf("Host-Pass-Through", "Cortex-A72 (Sim)", "Cortex-A53 (Sim)", "generic-arm64", "generic-x86_64").forEach { m ->
                                            DropdownMenuItem(text = { Text(m) }, onClick = { editCpuModel = m; cpuMenuOpen = false })
                                        }
                                    }
                                }

                                // Boot Source Dropdown Selector
                                var bootMenuOpen by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = editBootSource,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Standard Boot-Medium") },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = { bootMenuOpen = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Boot Selection")
                                            }
                                        }
                                    )
                                    DropdownMenu(expanded = bootMenuOpen, onDismissRequest = { bootMenuOpen = false }) {
                                        listOf("CD-ROM / ISO", "HDD / Image", "Kernel (Intern)").forEach { s ->
                                            DropdownMenuItem(text = { Text(s) }, onClick = { editBootSource = s; bootMenuOpen = false })
                                        }
                                    }
                                }

                                // primary disk + Picker Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editPrimaryDisk,
                                        onValueChange = { editPrimaryDisk = it },
                                        label = { Text("Primäre Festplatte (vda, vdc)") },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("z.B. Pfad zu .img Datei") }
                                    )
                                    IconButton(
                                        onClick = { primaryDiskPicker.launch(arrayOf("*/*")) },
                                        modifier = Modifier
                                            .offset(y = 4.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                            .size(54.dp)
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = "Auswählen", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }

                                // secondary disk + Picker Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editSecondaryDisk,
                                        onValueChange = { editSecondaryDisk = it },
                                        label = { Text("CD-ROM / Install-ISO (vdb)") },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("z.B. Alpine Linux NetInst .iso") }
                                    )
                                    IconButton(
                                        onClick = { secondaryDiskPicker.launch(arrayOf("*/*")) },
                                        modifier = Modifier
                                            .offset(y = 4.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                            .size(54.dp)
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = "Auswählen", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }

                                // Network dropdown
                                var netMenuOpen by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = editNetworkMode,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Netzwerkbrücken-Modus") },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = { netMenuOpen = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Net Selection")
                                            }
                                        }
                                    )
                                    DropdownMenu(expanded = netMenuOpen, onDismissRequest = { netMenuOpen = false }) {
                                        listOf("User / NAT", "Bridged (Enclave)", "Keine").forEach { n ->
                                            DropdownMenuItem(text = { Text(n) }, onClick = { editNetworkMode = n; netMenuOpen = false })
                                        }
                                    }
                                }

                                // VNC Port
                                OutlinedTextField(
                                    value = editVncPort.toString(),
                                    onValueChange = { editVncPort = it.toIntOrNull() ?: 5900 },
                                    label = { Text("Web VNC Server Port") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                // Checkboxes
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = editIsProtected, onCheckedChange = { editIsProtected = it })
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("pKVM Hardware Enclave Isolation (Sichere Schranke)")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = editIsDebuggable, onCheckedChange = { editIsDebuggable = it })
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("KVM Debugging-Schnittstelle & Logs freigeben")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = editEnableGpu, onCheckedChange = { editEnableGpu = it })
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("VirtIO-GPU Framebuffer (VNC Server aktivieren)")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = editEnableSound, onCheckedChange = { editEnableSound = it })
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("VirtIO-Soundkarte (Audiosync aktivieren)")
                                    }
                                }

                                OutlinedTextField(
                                    value = editExtraArgs,
                                    onValueChange = { editExtraArgs = it },
                                    label = { Text("Zusätzliche Parameter & Flags") },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("z.B. --use-crosvm oder --relative-mouse") }
                                )

                                Text(
                                    "Tipp: Nutze '--use-crosvm', um den nativen Android AVF Hypervisor anstelle von QEMU zu zwingen. " +
                                    "Nutze '--relative-mouse', wenn deine Maus im VNC springt.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )

                                // Speichern / Löschen Knöpfe
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val listIndex = profilesList.indexOfFirst { it.id == selectedProfileId }
                                            if (listIndex != -1) {
                                                profilesList[listIndex] = VMProfile(
                                                    id = selectedProfileId,
                                                    name = editName.ifBlank { "Unbenannt" },
                                                    ramMb = editRam,
                                                    cpuCores = editCores,
                                                    cpuModel = editCpuModel,
                                                    primaryDiskPath = editPrimaryDisk,
                                                    secondaryDiskPath = editSecondaryDisk,
                                                    bootSource = editBootSource,
                                                    isProtected = editIsProtected,
                                                    isDebuggable = editIsDebuggable,
                                                    networkMode = editNetworkMode,
                                                    vncPort = editVncPort,
                                                    enableVioSound = editEnableSound,
                                                    enableVioGpu = editEnableGpu,
                                                    extraArgs = editExtraArgs
                                                )
                                                saveProfiles()
                                                addLog("Profil '${editName}' erfolgreich dauerhaft gespeichert.")
                                                Toast.makeText(context, "Profil gespeichert!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Speichern", fontWeight = FontWeight.Bold)
                                    }

                                    if (profilesList.size > 1) {
                                        Button(
                                            onClick = {
                                                val toRemove = profilesList.find { it.id == selectedProfileId }
                                                if (toRemove != null) {
                                                    profilesList.remove(toRemove)
                                                    selectedProfileId = profilesList.first().id
                                                    saveProfiles()
                                                    addLog("Profil '${toRemove.name}' gelöscht.", isError = true)
                                                    Toast.makeText(context, "Gelöscht", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Löschen")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: VERWALTUNG SPEICHER & PLATTEN (Dateiexplorer und Disk Generator!)
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Speicher- und Imageverwaltung (Sparse Disks)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            
                            // Disk Generator Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Leere virtuelle Festplatte erstellen (Sparsely Allocated)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    
                                    OutlinedTextField(
                                        value = diskCreatorFilename,
                                        onValueChange = { diskCreatorFilename = it },
                                        label = { Text("Dateiname") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Größe der Festplatte: ", fontSize = 14.sp)
                                        Text("${diskCreatorSizeGb.toInt()} GB", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Slider(
                                        value = diskCreatorSizeGb,
                                        onValueChange = { diskCreatorSizeGb = it },
                                        valueRange = 1f..100f,
                                        steps = 98
                                    )

                                    Button(
                                        onClick = { generateDiskImage(diskCreatorFilename, diskCreatorSizeGb) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Erstellen")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sparse-File Festplatte anlegen", fontWeight = FontWeight.Bold)
                                    }

                                    if (diskCreatedLogs.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = diskCreatedLogs,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = if (diskCreatedLogs.contains("Fehler")) Color.Red else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Files table / List of disks
                            Text("Detektierte virtuelle Medien im KVM Ordner:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            
                            if (virtualDisksList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Keine virtuellen Disks gespeichert.\nLegen Sie oben eine an, um sie hier zu verwalten.", textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    virtualDisksList.forEach { f ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Build, contentDescription = "Disk", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(f.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    }
                                                    
                                                    IconButton(onClick = {
                                                        try {
                                                            f.delete()
                                                            reloadVirtualDisksList()
                                                            addLog("Festplatten-Image '${f.name}' gelöscht.")
                                                            Toast.makeText(context, "Gelöscht", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Throwable) {
                                                            Toast.makeText(context, "Löschfehler: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                                
                                                Text(
                                                    text = "Pfad: ${f.absolutePath}",
                                                    fontSize = 11.sp,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )

                                                Spacer(modifier = Modifier.height(6.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val sizeInGbStr = String.format(Locale.US, "%.2f", f.length().toDouble() / (1024.0 * 1024.0 * 1024.0))
                                                    Text("Virtuelle Kapazität: $sizeInGbStr GB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    
                                                    // Copy path button
                                                    Button(
                                                        onClick = {
                                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cb.setPrimaryClip(ClipData.newPlainText("FilePath", f.absolutePath))
                                                            Toast.makeText(context, "Pfad kopiert!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text("Pfad kopieren", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick ISO links card
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Hilfreiche offizielle Ressourcen zum Herunterladen:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Geben Sie diese Internet-Verweise in Ihren mobilen Browser ein, um minimale, pKVM-kompatible Linux-System-Images auf Ihr Android-Gerät zu laden:", fontSize = 12.sp)
                                    
                                    SelectionContainer {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("• Alpine Linux CD-ROM Kernel:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text("https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-virt-3.19.1-aarch64.iso", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("• Debian ARM64 Minimal ISO:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text("https://cdimage.debian.org/debian-cd/current/arm64/iso-cd/debian-12.5.0-arm64-netinst.iso", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: VM DISPLAY (noVNC Web Ansicht)
                    2 -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Virtueller Bildschirm (VNC) & Hardwaresteuerung", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (vmState != VMState.RUNNING) {
                                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                                    Text("Die virtuelle Maschine (QEMU/AVF) ist momentan ausgeschaltet.\nStarten Sie diese im Tab 'Hypervisor Terminal'.", color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                                    androidx.compose.ui.viewinterop.AndroidView(
                                        factory = { ctx ->
                                            android.webkit.WebView(ctx).apply {
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                // Clear caches to ensure fresh noVNC load
                                                clearCache(true)
                                                webViewClient = android.webkit.WebViewClient()
                                                webChromeClient = android.webkit.WebChromeClient()
                                                // VNC port logic: If vncPort is 5900, QEMU websocket port is typically mapped to 5700 in our args.
                                                // We will set this directly to 5700 in novnc.html parameters.
                                                loadUrl("file:///android_asset/novnc.html?host=127.0.0.1&port=5700")
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // TAB 3: TERMINAL CONSOLE & INTERACTIVE MONITOR
                    3 -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Boot actions header and VM profile state
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Aktive VM: ${currentProfile.name}", fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    when (vmState) {
                                                        VMState.RUNNING -> Color.Green
                                                        VMState.BOOTING -> Color.Cyan
                                                        VMState.ERROR -> Color.Red
                                                        VMState.STOPPED -> Color.Gray
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when (vmState) {
                                                VMState.RUNNING -> "Läuft"
                                                VMState.BOOTING -> "Bootvorgang..."
                                                VMState.ERROR -> "Fehler!"
                                                VMState.STOPPED -> "Gestoppt"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { bootVirtualMachine(currentProfile) },
                                        enabled = vmState == VMState.STOPPED || vmState == VMState.ERROR,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Starten", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                addLog("Signal gesendet: System-Shutdown...", isError = true)
                                                delay(400)
                                                addLog("[kernel] flush: sync virtual disk maps")
                                                addLog("[vmm] Virtual Machine destroyed gracefully.", isError = true)
                                                vmState = VMState.STOPPED
                                                stopVMService()
                                            }
                                        },
                                        enabled = vmState == VMState.RUNNING || vmState == VMState.BOOTING,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Stop")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stoppen", fontSize = 12.sp)
                                    }
                                }
                            }

                            // VNC Details Display
                            if (vmState == VMState.RUNNING && currentProfile.enableVioGpu) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Share, contentDescription = "VNC", tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("VNC Server aktiv auf Port: ${currentProfile.vncPort}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Verbindungs-Adresse: localhost:${currentProfile.vncPort} • Display: 1024x768 (VirtIO)", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // Main Black Terminal
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F1419))
                                    .border(1.dp, Color(0xFF2E3B4E), RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                if (terminalLogs.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "KVM / pKVM Schnittstellenkonsole offline.\nInstanz booten, um Protokolle anzuzeigen.",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            color = Color(0xFF5A7285),
                                            textAlign = TextAlign.Center,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        state = consoleListState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(terminalLogs) { line ->
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = "[${line.timestamp}] ",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = Color(0xFF5A7285),
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = line.message,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = when {
                                                        line.isError -> Color(0xFFFF5252)
                                                        line.isSuccess -> Color(0xFF4CAF50)
                                                        else -> Color(0xFFD8DEE9)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Interactive CLI Input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = terminalInputCmd,
                                    onValueChange = { terminalInputCmd = it },
                                    label = { Text("Eingabe an die laufende VM senden (z.B. help, ls, free)") },
                                    modifier = Modifier.weight(1f),
                                    enabled = vmState == VMState.RUNNING,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF1E252D),
                                        unfocusedContainerColor = Color(0xFF0F1419),
                                        disabledContainerColor = Color(0xFF0B0E12)
                                    )
                                )
                                Button(
                                    onClick = { executeConsoleCommand(terminalInputCmd, currentProfile) },
                                    enabled = vmState == VMState.RUNNING && terminalInputCmd.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Text("Send")
                                }
                            }
                        }
                    }

                    // TAB 4: SYSTEMSTATISTIKEN & KVM SHIZUKU MANAGEMENT
                    4 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Security Status",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "pKVM Hypervisor & API Status",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Status Rows
                                    StatusLabelRow(
                                        title = "KVM Kernelmodul (/sys/module/kvm)",
                                        status = if (kvmModuleExists) "Aktiv" else "Simulator Modus (Host blockiert)",
                                        isSuccess = kvmModuleExists
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                                    StatusLabelRow(
                                        title = "Hypervisor-Version (ro.boot.hypervisor.version)",
                                        status = hypervisorPropValue,
                                        isSuccess = hypervisorPropValue != "Unbekannt" && !hypervisorPropValue.contains("Zugriff")
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                                    StatusLabelRow(
                                        title = "USE_CUSTOM_VIRTUAL_MACHINE Berechtigung",
                                        status = if (hasCustomPerm) "Erteilt" else "Fehlend",
                                        isSuccess = hasCustomPerm
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                                    StatusLabelRow(
                                        title = "MANAGE_VIRTUAL_MACHINE Berechtigung",
                                        status = if (hasManagePerm) "Erteilt" else "Fehlend (System-Ebene)",
                                        isSuccess = hasManagePerm
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Simulation Modus Switch
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Checkbox(
                                            checked = forceSimulationMode,
                                            onCheckedChange = {
                                                forceSimulationMode = it
                                                addLog("Simulationsmodus ${if (it) "aktiviert" else "deaktiviert"}. Echtzeitbehandlung der VM-Konfigurationen.")
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "Simulationsbrücke aktivieren",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Ermöglicht KVM / pKVM Tests ohne echte Root permissions",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Binaries Download Panel
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = "Build Authority",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Virtualisierungs-Engines installieren",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Um QEMU oder den PRoot C++ JNI Wrapper nutzen zu können, müssen die statischen Binaries für ARM64 in den App-Speicher geladen werden. Ein C++ JNI Layer wurde für maximale Performance wie in Podroid eingebaut.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                addLog("Prüfe lokale Binaries (QEMU & PRoot)...")
                                                val qemuPath = "/data/data/${context.packageName}/files/qemu-system-aarch64"
                                                val prootPath = "/data/data/${context.packageName}/files/proot"
                                                
                                                val qemuFile = File(qemuPath)
                                                val prootFile = File(prootPath)
                                                
                                                if (qemuFile.exists() && prootFile.exists()) {
                                                    addLog("Binaries bereits installiert.", isSuccess = true)
                                                } else {
                                                    addLog("Binaries fehlen! Bitte laden Sie 'qemu-system-aarch64' und 'proot' (static aarch64) herunter und verschieben Sie diese nach: ", isError = true)
                                                    addLog(qemuFile.parent ?: "/data/data/${context.packageName}/files/")
                                                    addLog("Oder nutzen Sie 'adb push' für die Installation.")
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Binaries Status prüfen")
                                    }
                                }
                            }

                            // Shizuku Detection & Action Panel
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Shield Authority",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Berechtigungen über Shizuku erteilen",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isShizukuAvailable) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                                else MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isShizukuAvailable) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Shizuku Service:",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                StatusBadge(
                                                    text = if (isShizukuAvailable) "Gefunden & Aktiv" else "Inaktiv / Nicht gestartet",
                                                    isSuccess = isShizukuAvailable
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            if (isShizukuAvailable) {
                                                Text(
                                                    text = "Triggern Sie die Shell direkt, um USE_CUSTOM_VIRTUAL_MACHINE über Shizuku auf System-Ebene für Ihre Sandbox-App freizuschalten:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = onRequestShizukuAuth,
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.Lock, contentDescription = "Auth", modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("1. Autorisieren", fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = onGrantShizukuPermissions,
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Grant", modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("2. KVM Rechte ert.", fontSize = 11.sp)
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = "Fahren Sie Shizuku auf Ihrem Mobiltelefon hoch oder nutzen Sie ADB am Desktop.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    // Stepper: Manueller ADB Weg
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Desktop-Modus: ADB Shell Freischaltung",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Zur manuellen Aktivierung verbinden Sie Ihr Mobiltelefon mit Ihrem PC und geben folgende Terminal-Befehle ein:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val cmd1 = "adb shell pm grant $packageName android.permission.USE_CUSTOM_VIRTUAL_MACHINE"
                                    val cmd2 = "adb shell pm grant $packageName android.permission.MANAGE_VIRTUAL_MACHINE"

                                    ADBCommandLineCard(cmdText = cmd1, label = "Schritt 1: Freigabe Custom pKVM VMs")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ADBCommandLineCard(cmdText = cmd2, label = "Schritt 2: System Virtuo-Management")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusLabelRow(
    title: String,
    status: String,
    isSuccess: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        StatusBadge(text = status, isSuccess = isSuccess)
    }
}

@Composable
fun StatusBadge(
    text: String,
    isSuccess: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun ADBCommandLineCard(
    cmdText: String,
    label: String
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = cmdText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("ADB Command", cmdText)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "In die Zwischenablage kopiert!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Kopieren",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Custom simple SelectionContainer implementation to prevent dependency crashes with compose foundation selection
@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    Box {
        content()
    }
}
