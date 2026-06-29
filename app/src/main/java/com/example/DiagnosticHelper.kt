package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku
import java.io.File

object DiagnosticHelper {
    fun getDiagnosticText(context: Context): String {
        val pm = context.packageManager
        
        // Shizuku check
        val shizukuVersion = try { Shizuku.getVersion() } catch (e: Exception) { -1 }
        val shizukuAvailable = shizukuVersion > 0
        val shizukuPermission = if (shizukuAvailable) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Granted" else "Denied"
        } else "N/A"

        // File checks
        val apexPresent = File("/apex/com.android.virt").exists()
        val devKvm = File("/dev/kvm").exists()
        val virtioGpu = File("/sys/module/virtio_gpu").exists() || File("/sys/bus/virtio/drivers/virtio_gpu").exists()
        
        val featureSupport = pm.hasSystemFeature("android.software.virtualization_framework")
        val managePerm = context.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE") == PackageManager.PERMISSION_GRANTED
        val usePerm = context.checkSelfPermission("android.permission.USE_CUSTOM_VIRTUAL_MACHINE") == PackageManager.PERMISSION_GRANTED

        var vmmLoadable = false
        var builderPresent = false
        var serviceReachable = false
        var capsRaw = "0 (none/unknown)"

        try {
            val vmmClass = Class.forName("android.system.virtualmachine.VirtualMachineManager")
            vmmLoadable = true

            try {
                Class.forName("android.system.virtualmachine.VirtualMachineConfig\$Builder")
                builderPresent = true
            } catch (e: Exception) {}

            try {
                val getInstanceMethod = vmmClass.getMethod("getInstance", Context::class.java)
                val vmmInstance = getInstanceMethod.invoke(null, context)
                if (vmmInstance != null) {
                    serviceReachable = true
                    try {
                        val getCapabilitiesMethod = vmmClass.getMethod("getCapabilities")
                        val caps = getCapabilitiesMethod.invoke(vmmInstance)
                        capsRaw = caps?.toString() ?: "unknown"
                        
                        // Check for Android 16 specific capabilities
                        if (Build.VERSION.SDK_INT >= 36) {
                            val isAnyVmTypeSupportedMethod = vmmClass.getMethod("isAnyVmTypeSupported")
                            val anySupported = isAnyVmTypeSupportedMethod.invoke(vmmInstance)
                            capsRaw += " (AnyVmTypeSupported: $anySupported)"
                        }
                    } catch (e: Exception) {
                        capsRaw = "error fetching"
                    }
                }
            } catch (e: Exception) {}
        } catch (e: Exception) {}

        // Best Guess Backend
        var backend = "qemu"
        if (serviceReachable && builderPresent) {
             backend = "avf (native)"
        } else if (apexPresent && devKvm) {
             backend = "crosvm (bypass)"
        }

        if (devKvm && capsRaw == "0 (none/unknown)") {
             capsRaw = "KVM present in /dev/kvm"
        }

        val apiLevel = Build.VERSION.SDK_INT
        val codename = Build.VERSION.CODENAME

        return """
            System Info
              API Level: $apiLevel
              Codename: $codename

            Shizuku
              Available: $shizukuAvailable
              Version: $shizukuVersion
              Permission: $shizukuPermission

            Active backend
              $backend

            VirtIO Hardware Support
              GPU Acceleration = $virtioGpu
              KVM Node (/dev/kvm) = $devKvm

            Feature: virtualization_framework
              supported = $featureSupport

            Permission: MANAGE_VIRTUAL_MACHINE
              granted = $managePerm

            Permission: USE_CUSTOM_VIRTUAL_MACHINE
              granted = $usePerm

            APEX /apex/com.android.virt
              present = $apexPresent

            API VirtualMachineManager
              class loadable = $vmmLoadable

            Service
              reachable via system service = $serviceReachable

            Custom-VM API
              builder present = $builderPresent

            Hypervisor capabilities
              raw = $capsRaw
        """.trimIndent()
    }
}
