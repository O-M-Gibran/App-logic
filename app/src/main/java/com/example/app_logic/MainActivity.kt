package com.example.app_logic

import android.Manifest
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var sensorTextView: TextView
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceAddress = "8C:4F:00:3C:99:CE" // Ganti dengan alamat MAC ESP32 Anda
    private val serviceUUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val characteristicUUID = UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")
    private var bluetoothGatt: BluetoothGatt? = null

    private val phoneNumber = "+6281234567890"
    private var retryCount = 0
    private val maxRetries = 5
    private val reconnectDelay = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorTextView = TextView(this).apply {
            textSize = 20f
            setPadding(20, 20, 20, 20)
            text = "Memulai koneksi BLE..."
        }
        setContentView(sensorTextView)

        requestBluetoothPermissions {
            toastOnUiThread("Izin diberikan, menghubungkan...")
            connectToBLE()
        }
    }

    private fun requestBluetoothPermissions(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                    if (permissions.all { it.value }) {
                        onGranted()
                    } else {
                        runOnUiThread {
                            sensorTextView.text = "Izin Bluetooth ditolak"
                        }
                    }
                }

            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            onGranted()
        }
    }

    private fun connectToBLE() {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            sensorTextView.text = "Perangkat tidak ditemukan"
            return
        }

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        sensorTextView.text = "Mencoba koneksi ke perangkat..."
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    retryCount = 0
                    runOnUiThread {
                        sensorTextView.text = "Terkoneksi, mencari layanan..."
                    }
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runOnUiThread {
                        sensorTextView.text = "Terputus. Mencoba lagi..."
                    }
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    if (retryCount++ < maxRetries) {
                        Handler(Looper.getMainLooper()).postDelayed({ connectToBLE() }, reconnectDelay)
                    } else {
                        sensorTextView.text = "Gagal reconnect setelah $maxRetries percobaan"
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val charac = gatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
            if (charac != null) {
                gatt.setCharacteristicNotification(charac, true)
                val descriptor = charac.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                sensorTextView.text = "Notifikasi diaktifkan"
            } else {
                sensorTextView.text = "Karakteristik tidak ditemukan"
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val value = characteristic?.getStringValue(0)
            println("Diterima: $value")
            value?.let { handleSensorData(it) }
        }
    }

    private fun handleSensorData(line: String) {
        runOnUiThread {
            sensorTextView.text = "Diterima: $line"
        }
        val parts = line.split(",")
        if (parts.size == 4) {
            try {
                val vitals = Vitals(
                    bpm = parts[1].toInt(),
                    spo2 = parts[2].toInt()
                )
                val measurement = Measurement(
                    bpmMeasured = vitals.bpm,
                    bpmReference = 70,
                    spo2Measured = vitals.spo2,
                    spo2Reference = 90
                )

                val result = if (!measurement.isValid()) {
                    alert("Data tidak valid! Error BPM: %.2f%%, SpO₂: %.2f%%".format(
                        measurement.bpmError(), measurement.spo2Error()
                    ))
                    return
                } else {
                    AsthmaDetector.detect(vitals)
                }

                runOnUiThread {
                    val statusText = when (result) {
                        AsthmaLevel.SEVERE, AsthmaLevel.MODERATE -> {
                            val location = "https://www.google.com/maps/place/-7.2575,112.7521"
                            alert("Asma ${result.name.lowercase()}! Kirim lokasi...")
                            println("Sending SMS to $phoneNumber: $location")
                            println("Buzzer ON")
                            "⚠️ Asma ${result.name.lowercase()} terdeteksi!"
                        }
                        AsthmaLevel.NORMAL -> {
                            alert("Kondisi normal. BPM: ${vitals.bpm}, SpO₂: ${vitals.spo2}%")
                            "✅ Kondisi normal"
                        }
                    }

                    sensorTextView.text = """
                        BPM: ${vitals.bpm}
                        SpO₂: ${vitals.spo2}%
                        Status: $statusText
                    """.trimIndent()
                }
            } catch (e: Exception) {
                // error parsing data
            }
        }
    }

    private fun alert(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun toastOnUiThread(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        super.onDestroy()
    }
}
