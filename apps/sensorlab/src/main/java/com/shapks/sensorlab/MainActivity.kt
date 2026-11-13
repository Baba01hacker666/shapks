package com.shapks.sensorlab

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shapks.sensorlab.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager

    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var proxSensor: Sensor? = null

    private val gravityValues = FloatArray(3)
    private val geomagValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proxSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        displayHardwareProfile()
    }

    override fun onResume() {
        super.onResume()
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proxSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val totalMps2 = sqrt(x * x + y * y + z * z)
                val totalG = totalMps2 / 9.80665f

                binding.tvAccelValues.text = String.format(
                    Locale.US,
                    "X: %+.2f | Y: %+.2f | Z: %+.2f m/s²\nTotal Vector: %.2f m/s² (%.2f G)",
                    x, y, z, totalMps2, totalG
                )
                updateCompass()
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                binding.tvGyroValues.text = String.format(
                    Locale.US,
                    "Pitch: %+.2f | Roll: %+.2f | Yaw: %+.2f rad/s",
                    gx, gy, gz
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagValues, 0, 3)
                hasGeomag = true
                updateCompass()
            }
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                val currentText = binding.tvLightProximity.text.toString()
                val proxPart = if (currentText.contains("Proximity:")) {
                    currentText.substring(currentText.indexOf("Proximity:"))
                } else {
                    "Proximity: N/A"
                }
                binding.tvLightProximity.text = String.format(
                    Locale.US,
                    "Ambient Light: %.1f Lux\n%s",
                    lux, proxPart
                )
            }
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                val proxState = if (distance < maxRange) "NEAR (Obstructed)" else "FAR (Clear)"
                val currentText = binding.tvLightProximity.text.toString()
                val lightPart = if (currentText.contains("Ambient Light:")) {
                    currentText.substring(0, currentText.indexOf("\nProximity:")).trim()
                } else {
                    "Ambient Light: Detecting..."
                }
                binding.tvLightProximity.text = String.format(
                    Locale.US,
                    "%s\nProximity: %s (%.1f cm)",
                    lightPart, proxState, distance
                )
            }
        }
    }

    private fun updateCompass() {
        if (!hasGravity || !hasGeomag) return
        val rMatrix = FloatArray(9)
        val iMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(rMatrix, iMatrix, gravityValues, geomagValues)
        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rMatrix, orientation)
            var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuthDeg < 0) azimuthDeg += 360f

            val cardinal = getCardinalDirection(azimuthDeg)
            binding.tvCompassHeading.text = String.format(
                Locale.US,
                "Heading: %.1f° (%s)",
                azimuthDeg, cardinal
            )
        }
    }

    private fun getCardinalDirection(deg: Float): String {
        return when {
            deg >= 337.5 || deg < 22.5 -> "North"
            deg < 67.5 -> "North-East"
            deg < 112.5 -> "East"
            deg < 157.5 -> "South-East"
            deg < 202.5 -> "South"
            deg < 247.5 -> "South-West"
            deg < 292.5 -> "West"
            else -> "North-West"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun displayHardwareProfile() {
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingStatus = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charging"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Charging"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charging"
            else -> "Discharging (On Battery)"
        }

        val profile = buildString {
            append("• Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}\n")
            append("• Brand / Product: ${Build.BRAND} (${Build.PRODUCT})\n")
            append("• Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("• Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
            append("• Battery Level: $batteryPct% ($chargingStatus)\n")
            append("• Sensors Detected: ")
            val available = listOfNotNull(
                accelSensor?.let { "Accel" },
                gyroSensor?.let { "Gyro" },
                magSensor?.let { "Mag" },
                lightSensor?.let { "Light" },
                proxSensor?.let { "Prox" }
            )
            append(if (available.isNotEmpty()) available.joinToString(", ") else "None")
        }
        binding.tvDeviceSpecs.text = profile
    }
}
