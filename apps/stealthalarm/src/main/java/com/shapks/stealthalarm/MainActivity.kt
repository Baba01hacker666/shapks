package com.shapks.stealthalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.shapks.stealthalarm.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    private enum class State { DISARMED, COUNTDOWN, ARMED, TRIGGERED }
    private var currentState = State.DISARMED

    private var initialX = 0f
    private var initialY = 0f
    private var initialZ = 0f
    private var baselineSet = false

    private var isSirenActive = false
    private var countdownTimer: CountDownTimer? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                if (currentState == State.ARMED && binding.switchCharger.isChecked) {
                    triggerAlarm("Charger was unplugged!")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.btnMainAction.setOnClickListener {
            handleActionClick()
        }

        registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED))
    }

    private fun handleActionClick() {
        when (currentState) {
            State.DISARMED -> startArmCountdown()
            State.COUNTDOWN -> cancelCountdown()
            State.ARMED, State.TRIGGERED -> promptBiometricDisarm()
        }
    }

    private fun startArmCountdown() {
        currentState = State.COUNTDOWN
        binding.btnMainAction.text = "CANCEL ARMING"
        binding.btnMainAction.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_amber))
        binding.tvAlarmState.text = "ARMING..."
        binding.tvAlarmState.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000) + 1
                binding.tvCountdown.text = "Place phone down. Arming in $sec seconds..."
            }

            override fun onFinish() {
                armSystem()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        disarmSystem(silent = true)
    }

    private fun armSystem() {
        currentState = State.ARMED
        baselineSet = false
        binding.tvAlarmState.text = "🛡️ SYSTEM ARMED"
        binding.tvAlarmState.setTextColor(ContextCompat.getColor(this, R.color.primary))
        binding.tvCountdown.text = "Sensors active. Fingerprint or PIN required to disarm."
        binding.btnMainAction.text = "DISARM WITH FINGERPRINT / PIN"
        binding.btnMainAction.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_variant))

        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun triggerAlarm(reason: String) {
        if (currentState == State.TRIGGERED) return
        currentState = State.TRIGGERED

        binding.tvAlarmState.text = "⚠️ THEFT DETECTED: $reason"
        binding.tvAlarmState.setTextColor(ContextCompat.getColor(this, R.color.primary))
        binding.tvCountdown.text = "ALARM SOUNDING! Use fingerprint/PIN to silence."

        startSiren()
        promptBiometricDisarm()
    }

    private fun startSiren() {
        isSirenActive = true
        Thread {
            while (isSirenActive) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(400)
                }
                Thread.sleep(600)
            }
        }.start()
    }

    private fun stopSiren() {
        isSirenActive = false
        toneGenerator?.stopTone()
        vibrator?.cancel()
    }

    private fun promptBiometricDisarm() {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val executor = ContextCompat.getMainExecutor(this)

        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                stopSiren()
                disarmSystem(silent = false)
                Toast.makeText(this@MainActivity, "Alarm Disarmed Successfully", Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@MainActivity, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@MainActivity, "Biometrics Failed", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Disarm Theft Alarm")
            .setSubtitle("Confirm your identity with Fingerprint or PIN")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun disarmSystem(silent: Boolean) {
        currentState = State.DISARMED
        sensorManager.unregisterListener(this)
        stopSiren()

        binding.tvAlarmState.text = "SYSTEM DISARMED"
        binding.tvAlarmState.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        binding.tvCountdown.text = "Tap below to activate theft guard"
        binding.btnMainAction.text = "ARM ALARM SYSTEM (5s DELAY)"
        binding.btnMainAction.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (currentState != State.ARMED || !binding.switchMotion.isChecked) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!baselineSet) {
            initialX = x
            initialY = y
            initialZ = z
            baselineSet = true
            return
        }

        val deltaX = abs(x - initialX)
        val deltaY = abs(y - initialY)
        val deltaZ = abs(z - initialZ)
        val movement = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

        // Movement threshold: 2.2 m/s^2 change
        if (movement > 2.2f) {
            triggerAlarm("Phone was moved or picked up!")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        stopSiren()
        toneGenerator?.release()
        try {
            unregisterReceiver(powerReceiver)
        } catch (e: Exception) {}
        sensorManager.unregisterListener(this)
    }
}
