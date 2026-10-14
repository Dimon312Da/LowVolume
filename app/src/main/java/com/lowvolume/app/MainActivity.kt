package com.lowvolume.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var settings: AppSettings
    private lateinit var passwords: PasswordManager
    private lateinit var audioManager: AudioManager
    private lateinit var devicePolicyManager: DevicePolicyManager

    private lateinit var statusText: TextView
    private lateinit var enabledSwitch: Switch
    private lateinit var limitLabel: TextView
    private lateinit var limitSeek: SeekBar
    private lateinit var limitDetails: TextView
    private lateinit var streamsContainer: LinearLayout
    private lateinit var lockStatus: TextView
    private lateinit var lockButton: Button
    private lateinit var changePasswordButton: Button
    private lateinit var adminButton: Button
    private lateinit var batteryButton: Button

    private val streamBoxes = mutableMapOf<Int, CheckBox>()

    /** Настройки разблокированы паролем до тех пор, пока экран открыт. */
    private var unlocked = false
    private var lockedHintShown = false
    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)
        passwords = PasswordManager(settings)
        audioManager = getSystemService(AudioManager::class.java)
        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)

        statusText = findViewById(R.id.status_text)
        enabledSwitch = findViewById(R.id.enabled_switch)
        limitLabel = findViewById(R.id.limit_label)
        limitSeek = findViewById(R.id.limit_seek)
        limitDetails = findViewById(R.id.limit_details)
        streamsContainer = findViewById(R.id.streams_container)
        lockStatus = findViewById(R.id.lock_status)
        lockButton = findViewById(R.id.lock_button)
        changePasswordButton = findViewById(R.id.change_password_button)
        adminButton = findViewById(R.id.admin_button)
        batteryButton = findViewById(R.id.battery_button)

        setupStreams()
        setupListeners()

        if (!passwords.hasPassword) {
            showNewPasswordDialog(passwords, cancelable = false) {
                settings.enabled = true
                VolumeGuardService.start(this)
                refreshUi()
                Toast.makeText(this, R.string.protection_started, Toast.LENGTH_LONG).show()
            }
        } else {
            VolumeGuardService.start(this)
        }
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onStop() {
        super.onStop()
        // Как только пользователь ушёл с экрана — снова блокируем настройки.
        unlocked = false
    }

    private fun setupStreams() {
        for ((stream, labelRes) in AppSettings.ALL_STREAMS) {
            val box = CheckBox(this).apply { setText(labelRes) }
            box.setOnClickListener {
                if (box.isChecked) {
                    // Добавить поток под ограничение можно без пароля.
                    settings.streams = settings.streams + stream
                    refreshUi()
                } else {
                    box.isChecked = true
                    requireUnlock {
                        settings.streams = settings.streams - stream
                        refreshUi()
                    }
                }
            }
            streamBoxes[stream] = box
            streamsContainer.addView(box)
        }
    }

    @SuppressLint("BatteryLife")
    private fun setupListeners() {
        // Слушаем именно изменение состояния: переключатель можно перетащить, не вызывая клик.
        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUi || checked == settings.enabled) return@setOnCheckedChangeListener
            if (checked) {
                settings.enabled = true
                VolumeGuardService.start(this)
                refreshUi()
            } else {
                refreshUi()
                requireUnlock {
                    settings.enabled = false
                    refreshUi()
                }
            }
        }

        limitSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (!unlocked && progress > settings.limitPercent) {
                    // Без пароля лимит можно только уменьшать.
                    seekBar.progress = settings.limitPercent
                    if (!lockedHintShown) {
                        lockedHintShown = true
                        Toast.makeText(this@MainActivity, R.string.raise_needs_password, Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                settings.limitPercent = progress
                updateLimitTexts()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                lockedHintShown = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        lockButton.setOnClickListener {
            if (unlocked) {
                unlocked = false
                refreshUi()
            } else {
                requireUnlock { }
            }
        }

        changePasswordButton.setOnClickListener {
            requireUnlock {
                showNewPasswordDialog(passwords, cancelable = true) {
                    Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        adminButton.setOnClickListener {
            val admin = AdminReceiver.component(this)
            if (devicePolicyManager.isAdminActive(admin)) {
                requireUnlock {
                    devicePolicyManager.removeActiveAdmin(admin)
                    Toast.makeText(this, R.string.admin_removed, Toast.LENGTH_SHORT).show()
                    // Статус администратора обновляется не мгновенно.
                    adminButton.postDelayed({ refreshUi() }, 500)
                }
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
                startActivity(intent)
            }
        }

        batteryButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /** Выполняет [action] сразу, если настройки разблокированы, иначе — после ввода пароля. */
    private fun requireUnlock(action: () -> Unit) {
        if (unlocked) {
            action()
            return
        }
        showEnterPasswordDialog(passwords) {
            unlocked = true
            refreshUi()
            action()
        }
    }

    private fun refreshUi() {
        val enabled = settings.enabled
        updatingUi = true
        enabledSwitch.isChecked = enabled
        updatingUi = false
        statusText.setText(if (enabled) R.string.status_on else R.string.status_off)
        statusText.setTextColor(getColor(if (enabled) R.color.status_on else R.color.status_off))

        limitSeek.progress = settings.limitPercent
        updateLimitTexts()

        val streams = settings.streams
        for ((stream, box) in streamBoxes) box.isChecked = stream in streams

        lockStatus.setText(if (unlocked) R.string.lock_status_unlocked else R.string.lock_status_locked)
        lockButton.setText(if (unlocked) R.string.lock else R.string.unlock)

        val adminActive = devicePolicyManager.isAdminActive(AdminReceiver.component(this))
        adminButton.setText(if (adminActive) R.string.admin_disable else R.string.admin_enable)

        val power = getSystemService(PowerManager::class.java)
        batteryButton.visibility =
            if (power.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE
    }

    private fun updateLimitTexts() {
        val percent = settings.limitPercent
        limitLabel.text = getString(R.string.limit_label, percent)
        val details = AppSettings.ALL_STREAMS
            .filter { (stream, _) -> stream in settings.streams }
            .joinToString("\n") { (stream, labelRes) ->
                getString(
                    R.string.limit_detail_line,
                    getString(labelRes),
                    VolumeLimiter.allowedIndex(audioManager, stream, percent),
                    audioManager.getStreamMaxVolume(stream),
                )
            }
        limitDetails.text = details.ifEmpty { getString(R.string.no_streams) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
