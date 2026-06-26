package com.example

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object DiagnosticHelper {
    fun getDiagnosticText(context: Context): String {
        val pm = context.packageManager
        
        // File checks
        val apexPresent = File("/apex/com.android.virt").exists()
        val devKvm = File("/dev/kvm").exists()
        
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
                        val getCapsMethod = vmmClass.getMethod("getCapabilities")
                        val caps = getCapsMethod.invoke(vmmInstance)
                        capsRaw = caps?.toString() ?: "unknown"
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

        return """
            Active backend
              $backend

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
