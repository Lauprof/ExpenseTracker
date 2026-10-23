package com.example.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensetracker.data.local.PreferencesManager
import com.example.expensetracker.data.sms.SmsSyncWorker
import com.example.expensetracker.ui.ExpenseViewModel
import com.example.expensetracker.ui.screens.HomeScreen
import com.example.expensetracker.ui.screens.OnboardingScreen
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferencesManager(this)
        var isUnlocked by mutableStateOf(!prefs.isAppLockEnabled)

        if (prefs.isAppLockEnabled) {
            promptBiometric(onSuccess = { isUnlocked = true })
        }

        setContent {
            ExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isUnlocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Expense Tracker Locked",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Authenticate to access your financial records",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { promptBiometric(onSuccess = { isUnlocked = true }) }
                                ) {
                                    Text("Unlock with Biometric / PIN")
                                }
                            }
                        }
                    } else {
                        val viewModel: ExpenseViewModel = viewModel()
                        val uiState by viewModel.uiState.collectAsState()

                        if (!uiState.isAppOnboardingShown) {
                            OnboardingScreen(
                                onFinished = { name, phone -> viewModel.completeOnboarding(name, phone) }
                            )
                        } else {
                            var hasSmsPermission by remember {
                                mutableStateOf(
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.READ_SMS
                                    ) == PackageManager.PERMISSION_GRANTED
                                )
                            }

                            val permissionLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestPermission()
                            ) { isGranted ->
                                hasSmsPermission = isGranted
                                if (isGranted) {
                                    SmsSyncWorker.schedulePeriodicSync(this@MainActivity)
                                    viewModel.syncSms(forceFullSync = false)
                                }
                            }

                            LaunchedEffect(hasSmsPermission) {
                                if (hasSmsPermission) {
                                    SmsSyncWorker.schedulePeriodicSync(this@MainActivity)
                                    viewModel.syncSms(forceFullSync = false)
                                }
                            }

                            HomeScreen(
                                viewModel = viewModel,
                                hasSmsPermission = hasSmsPermission,
                                onRequestPermission = {
                                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun promptBiometric(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Expense Tracker")
            .setSubtitle("Confirm your fingerprint, face, or device PIN to continue")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onSuccess()
        }
    }
}
