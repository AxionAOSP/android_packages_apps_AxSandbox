package com.android.axion.sandbox

import android.app.Activity
import android.app.AxSandboxManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.PowerManager
import android.os.Process
import android.os.UserHandle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.axion.sandbox.security.SecurityType
import com.android.axion.sandbox.security.SandboxSecurityManager
import com.android.axion.sandbox.ui.LockScreen
import com.android.axion.sandbox.ui.PasswordScreen
import com.android.axion.sandbox.ui.PatternScreen
import com.android.axion.sandbox.ui.theme.SandboxTheme

class AuthenticateActivity : ComponentActivity() {
    
    private lateinit var securityManager: SandboxSecurityManager
    private var packageName: String? = null
    private var appLabel: String? = null
    private var userId: Int = 0
    private var isSystemUnlock: Boolean = false

    private var resultIntent: Intent? = null
    private var isAuthSuccess = false
    private var failedTime: Long = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupWindowForOverlay()
        
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelAndFinish()
            }
        })

        resultIntent = Intent()
        
        securityManager = SandboxSecurityManager(this)
        
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(EXTRA_LOCKED_PACKAGE)
        
        appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: packageName?.let { pkg ->
            try {
                val pm = applicationContext.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        } ?: getString(R.string.apps)
        
        userId = intent.getIntExtra(EXTRA_USER_ID, 0)
            .takeIf { it != 0 } ?: intent.getIntExtra(EXTRA_LOCKED_UID, 0).let { 
            UserHandle.getUserId(it) 
        }

        isSystemUnlock = ACTION_SYSTEM_UNLOCK == intent.action
        
        if (!securityManager.isSetup()) {
            unlockAndFinish()
            return
        }

        val biometricType = if (securityManager.isBiometricEnabled() && securityManager.isBiometricAvailable()) {
             securityManager.getBiometricType()
        } else {
             SandboxSecurityManager.BiometricType.NONE
        }
        
        setContent {
            SandboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthenticateScreen(
                        securityManager = securityManager,
                        appLabel = appLabel ?: getString(R.string.apps),
                        onSuccess = { unlockAndFinish() },
                        onCancel = { cancelAndFinish() },
                        biometricType = biometricType,
                        isPreferBiometric = securityManager.isPreferBiometric(),
                        onBiometricClick = { showBiometricPrompt() }
                    )
                }
            }
        }
    }
    
    private fun showBiometricPrompt() {
        val negativeButtonText = when (securityManager.getSecurityType()) {
            SecurityType.PIN -> getString(R.string.use_pin)
            SecurityType.PASSWORD -> getString(R.string.use_password)
            SecurityType.PATTERN -> getString(R.string.use_pattern)
            else -> getString(R.string.cancel)
        }

        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.unlock_app_title, appLabel ?: getString(R.string.apps)))
            .setNegativeButton(negativeButtonText, mainExecutor) { _, _ -> 
                cancelAndFinish()
            }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(
            CancellationSignal(),
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    unlockAndFinish()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                     super.onAuthenticationError(errorCode, errString)
                     if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED || 
                         errorCode == BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON) {
                     } else {
                         cancelAndFinish()
                     }
                }
            }
        )
    }
    
    private fun setupWindowForOverlay() {
        window?.apply {
            setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL)
            
            addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            
            addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            
            attributes = attributes?.apply {
                privateFlags = privateFlags or 
                    WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
            }
        }
    }

    private fun unlockAndFinish() {
        packageName?.let { pkg ->
            val sandboxManager = getSystemService(Context.AX_SANDBOX_SERVICE) as? AxSandboxManager
            sandboxManager?.unlockApp(pkg, userId)
        }
        resultIntent?.apply {
            putExtra(EXTRA_LOCKED_PACKAGE, packageName)
            putExtra(EXTRA_LOCKED_UID, userId)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        isAuthSuccess = true
        
        finish()
        Process.killProcess(Process.myPid())
    }
    
    private fun cancelAndFinish() {
        resultIntent?.apply {
            putExtra(EXTRA_LOCKED_PACKAGE, packageName)
            putExtra(EXTRA_LOCKED_UID, userId)
        }
        setResult(Activity.RESULT_CANCELED, resultIntent)
        
        failedTime = SystemClock.elapsedRealtime()
        
        moveTaskToBack(true)
        
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        
        finishAndCleanup()
    }
    
    override fun onPause() {
        super.onPause()
        finishAndCleanup()
    }
    
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finishAndCleanup()
    }
    
    private fun finishAndCleanup() {
        finish()
        Process.killProcess(Process.myPid())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (hasFocus && isAuthSuccess) {
            finishAndCleanup()
            isAuthSuccess = false
            return
        }

        val timeSinceFailed = SystemClock.elapsedRealtime() - failedTime
        val isMultiWindow = WindowModeUtil.isAppInMultiWindowMode()
        val isFreeform = WindowModeUtil.isAppInFreeformDisplay(this)

        if (hasFocus && !isMultiWindow && !isFreeform) {
            if (timeSinceFailed in 1..280 && isScreenOn()) {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(this)
                }
                finishAndCleanup()
            }
        }
    }
    
    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: false
    }
    
    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_LABEL = "app_label"
        const val EXTRA_USER_ID = "user_id"
        
        const val EXTRA_LOCKED_PACKAGE = "LOCKED_PACKAGE"
        const val EXTRA_LOCKED_UID = "LOCKED_UID"
        
        const val ACTION_AUTHENTICATE = "com.android.axion.sandbox.action.AUTHENTICATE"
        const val ACTION_SYSTEM_UNLOCK = "com.android.axion.sandbox.action.SYSTEM_UNLOCK"
    }
}

@Composable
fun AuthenticateScreen(
    securityManager: SandboxSecurityManager,
    appLabel: String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    biometricType: SandboxSecurityManager.BiometricType = SandboxSecurityManager.BiometricType.NONE,
    isPreferBiometric: Boolean = false,
    onBiometricClick: () -> Unit = {}
) {
    LaunchedEffect(biometricType) {
        if (biometricType != SandboxSecurityManager.BiometricType.NONE && isPreferBiometric) {
            onBiometricClick()
        }
    }

    val securityType = securityManager.getSecurityType()
    val promptText = stringResource(R.string.auth_prompt_format, appLabel)
    
    when (securityType) {
        SecurityType.PIN -> {
            LockScreen(
                isSetup = false,
                promptText = promptText,
                onUnlock = onSuccess,
                onPinEntered = { pin -> securityManager.verifyCredential(pin) },
                onBack = onCancel,
                biometricType = biometricType,
                onBiometricClick = onBiometricClick
            )
        }
        SecurityType.PASSWORD -> {
            PasswordScreen(
                isSetup = false,
                promptText = promptText,
                onUnlock = onSuccess,
                onPasswordEntered = { password -> securityManager.verifyCredential(password) },
                onBack = onCancel,
                biometricType = biometricType,
                onBiometricClick = onBiometricClick
            )
        }
        SecurityType.PATTERN -> {
            PatternScreen(
                isSetup = false,
                promptText = promptText,
                onUnlock = onSuccess,
                onPatternEntered = { pattern -> securityManager.verifyPattern(pattern) },
                onBack = onCancel,
                biometricType = biometricType,
                onBiometricClick = onBiometricClick
            )
        }
        SecurityType.NONE -> {
            LaunchedEffect(Unit) {
                onSuccess()
            }
        }
    }
}
