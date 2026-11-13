package com.shapks.biovault

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shapks.biovault.databinding.ActivityMainBinding
import com.shapks.biovault.databinding.DialogAddSecretBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: VaultStorage
    private lateinit var authManager: BiometricAuthManager
    private lateinit var adapter: SecretsAdapter
    private val secretsList = mutableListOf<SecretItem>()

    private var isUnlocked = false
    private var isAuthenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protect screen contents from screenshots and recents previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = VaultStorage(this)

        setupRecyclerView()
        setupAuthManager()
        setupListeners()
        updateLockState(unlocked = false)

        // Check hardware and prompt on initial open
        checkBiometricsAndPrompt(autoPrompt = true)
    }

    private fun setupRecyclerView() {
        adapter = SecretsAdapter(secretsList) { itemToDelete ->
            showDeleteConfirmation(itemToDelete)
        }
        binding.rvSecrets.layoutManager = LinearLayoutManager(this)
        binding.rvSecrets.adapter = adapter
    }

    private fun setupAuthManager() {
        authManager = BiometricAuthManager(
            activity = this,
            onAuthSuccess = {
                isAuthenticating = false
                isUnlocked = true
                updateLockState(unlocked = true)
                loadVaultData()
                Toast.makeText(this, "Vault Unlocked Successfully", Toast.LENGTH_SHORT).show()
            },
            onAuthError = { errorCode, errString ->
                isAuthenticating = false
                binding.tvStatusMessage.text = getString(R.string.auth_error, errString)
            },
            onAuthFailed = {
                binding.tvStatusMessage.text = getString(R.string.auth_failed)
            }
        )
    }

    private fun checkBiometricsAndPrompt(autoPrompt: Boolean) {
        when (val status = authManager.checkBiometricStatus(this)) {
            BiometricAuthManager.CanAuthResult.READY -> {
                binding.tvSecurityLevel.text = "🔒 Security: Hardware Biometrics & PIN Enabled"
                if (autoPrompt && !isUnlocked && !isAuthenticating) {
                    isAuthenticating = true
                    authManager.promptBiometricAuthentication()
                }
            }
            BiometricAuthManager.CanAuthResult.NONE_ENROLLED -> {
                binding.tvSecurityLevel.text = "⚠️ No Fingerprint or Screen Lock Enrolled"
                binding.tvStatusMessage.text = getString(R.string.none_enrolled)
                showEnrollmentDialog()
            }
            BiometricAuthManager.CanAuthResult.NO_HARDWARE,
            BiometricAuthManager.CanAuthResult.HARDWARE_UNAVAILABLE,
            BiometricAuthManager.CanAuthResult.SECURITY_UPDATE_REQUIRED,
            BiometricAuthManager.CanAuthResult.UNSUPPORTED -> {
                binding.tvSecurityLevel.text = "⚠️ Biometric Status: ${status.name}"
                binding.tvStatusMessage.text = getString(R.string.hardware_not_available)
            }
        }
    }

    private fun showEnrollmentDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Device Security Required")
            .setMessage("To secure your vault, please enroll a fingerprint, face unlock, or set a device PIN/Pattern in your system settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                        putExtra(
                            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                    }
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupListeners() {
        binding.btnUnlock.setOnClickListener {
            isAuthenticating = true
            authManager.promptBiometricAuthentication()
        }

        binding.btnToolbarLock.setOnClickListener {
            lockVault()
        }

        binding.btnQuickLock.setOnClickListener {
            lockVault()
        }

        binding.btnAddSecret.setOnClickListener {
            showAddSecretDialog()
        }
    }

    private fun lockVault() {
        isUnlocked = false
        updateLockState(unlocked = false)
        secretsList.clear()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Vault Locked", Toast.LENGTH_SHORT).show()
    }

    private fun updateLockState(unlocked: Boolean) {
        if (unlocked) {
            binding.lockedLayout.visibility = View.GONE
            binding.unlockedLayout.visibility = View.VISIBLE
            binding.btnToolbarLock.visibility = View.VISIBLE
        } else {
            binding.lockedLayout.visibility = View.VISIBLE
            binding.unlockedLayout.visibility = View.GONE
            binding.btnToolbarLock.visibility = View.GONE
            binding.tvStatusMessage.text = getString(R.string.locked_status_text)
        }
    }

    private fun loadVaultData() {
        secretsList.clear()
        secretsList.addAll(storage.loadSecrets())
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (secretsList.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.rvSecrets.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.rvSecrets.visibility = View.VISIBLE
        }
    }

    private fun showAddSecretDialog() {
        val dialogBinding = DialogAddSecretBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val title = dialogBinding.etTitle.text?.toString()?.trim() ?: ""
                val content = dialogBinding.etContent.text?.toString()?.trim() ?: ""

                if (title.isNotEmpty() && content.isNotEmpty()) {
                    val newItem = SecretItem(title = title, content = content)
                    secretsList.add(0, newItem)
                    storage.saveSecrets(secretsList)
                    adapter.notifyItemInserted(0)
                    binding.rvSecrets.scrollToPosition(0)
                    updateEmptyState()
                    Toast.makeText(this, "Saved and encrypted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(item: SecretItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Secret")
            .setMessage("Are you sure you want to permanently delete '${item.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                val index = secretsList.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    secretsList.removeAt(index)
                    storage.saveSecrets(secretsList)
                    adapter.notifyItemRemoved(index)
                    updateEmptyState()
                    Toast.makeText(this, R.string.entry_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        // Automatically re-lock vault when leaving or backgrounding app
        if (storage.isAutoLockEnabled() && isUnlocked) {
            lockVault()
        }
    }
}
