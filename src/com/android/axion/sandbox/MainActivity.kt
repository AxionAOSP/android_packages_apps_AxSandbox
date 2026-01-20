package com.android.axion.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.axion.sandbox.security.LockedAppBehavior
import com.android.axion.sandbox.security.PrivateSectionBehavior
import com.android.axion.sandbox.security.SecurityType
import com.android.axion.sandbox.security.SandboxSecurityManager
import com.android.axion.sandbox.ui.LockScreen
import com.android.axion.sandbox.ui.PasswordScreen
import com.android.axion.sandbox.ui.PatternScreen
import com.android.axion.sandbox.ui.SecuritySetupScreen
import com.android.axion.sandbox.ui.SettingsScreen
import com.android.axion.sandbox.ui.SandboxApp
import com.android.axion.sandbox.ui.ForgotPasswordScreen
import com.android.axion.sandbox.ui.SecurityQuestionSetupScreen
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricManager
import android.os.CancellationSignal
import androidx.compose.ui.res.stringResource
import com.android.axion.sandbox.ui.theme.SandboxTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var securityManager: SandboxSecurityManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        securityManager = SandboxSecurityManager(this)
        
        setContent {
            SandboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SandboxNavigation(
                        securityManager = securityManager,
                        onShowBiometricPrompt = { onSuccess ->
                            showBiometricPrompt(onSuccess)
                        }
                    )
                }
            }
        }
    }
    
    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.unlock_private_apps))
            .setNegativeButton(getString(R.string.cancel), mainExecutor) { _, _ -> }
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
                    onSuccess()
                }
            }
        )
    }
}

enum class SandboxScreen {
    UNLOCK_PRIVATE,
    SETUP_TYPE_SELECTOR,
    SETUP_CREDENTIAL,
    SETUP_SECURITY_QUESTION,
    MAIN,
    SETTINGS,
    SETTINGS_RECOVERY,
    VERIFY_CURRENT_CREDENTIAL,
    CHANGE_SECURITY,
    FORGOT_PASSWORD
}

@Composable
fun SandboxNavigation(
    securityManager: SandboxSecurityManager,
    onShowBiometricPrompt: (() -> Unit) -> Unit
) {

    val lifecycleOwner = LocalLifecycleOwner.current
    
    var currentScreen by rememberSaveable { mutableStateOf(SandboxScreen.MAIN) }
    var selectedSecurityType by rememberSaveable { mutableStateOf(SecurityType.PIN) }
    var pendingSecurityType by rememberSaveable { mutableStateOf<SecurityType?>(null) }
    var firstCredential by remember { mutableStateOf<Any?>(null) }
    var isConfirmStep by rememberSaveable { mutableStateOf(false) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    
    var isPrivateUnlocked by rememberSaveable { mutableStateOf(false) }
    
    var isPrivateAreaExpanded by rememberSaveable { mutableStateOf(false) }
    
    var currentLockedAppBehavior by remember { mutableStateOf<LockedAppBehavior>(securityManager.getLockedAppBehavior()) }
    var currentLockedAppTimeout by remember { mutableStateOf<Int>(securityManager.getLockedAppTimeout()) }
    var currentPrivateBehavior by remember { mutableStateOf<PrivateSectionBehavior>(securityManager.getPrivateSectionBehavior()) }
    var currentPrivateTimeout by remember { mutableStateOf<Int>(securityManager.getPrivateSectionTimeout()) }
    var currentSecurityType by remember { mutableStateOf<SecurityType>(securityManager.getSecurityType()) }
    var currentBiometricEnabled by remember { mutableStateOf<Boolean>(securityManager.isBiometricEnabled()) }
    var currentPreferBiometric by remember { mutableStateOf<Boolean>(securityManager.isPreferBiometric()) }
    var hasSecurityQuestion by remember { mutableStateOf<Boolean>(securityManager.hasSecurityQuestion()) }
    var forgotPasswordReturnScreen by rememberSaveable { mutableStateOf(SandboxScreen.UNLOCK_PRIVATE) }
    
    val currentScreenState = rememberUpdatedState(currentScreen)
    val currentPrivateBehaviorState = rememberUpdatedState(currentPrivateBehavior)
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && securityManager.isSetup()) {
                val privateBehavior = currentPrivateBehaviorState.value
                
                when (privateBehavior) {
                    PrivateSectionBehavior.ON_LEAVE -> {
                        isPrivateUnlocked = false
                        isPrivateAreaExpanded = false
                    }
                    PrivateSectionBehavior.TIMEOUT -> {
                    }
                }
            } else if (event == Lifecycle.Event.ON_RESUME && securityManager.isSetup()) {
                if (currentPrivateBehaviorState.value == PrivateSectionBehavior.TIMEOUT 
                    && securityManager.isTimeoutExpired()) {
                    isPrivateUnlocked = false
                    isPrivateAreaExpanded = false
                } else if (isPrivateUnlocked) {
                    securityManager.setLastUnlockTime()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(currentScreen) {
        if (currentScreen == SandboxScreen.SETUP_CREDENTIAL || currentScreen == SandboxScreen.CHANGE_SECURITY) {
            if (!isConfirmStep) {
                firstCredential = null
            }
        }
    }
    
    when (currentScreen) {
        SandboxScreen.UNLOCK_PRIVATE -> {
            val securityType = securityManager.getSecurityType()
            
            if (securityType == SecurityType.NONE || !securityManager.isSetup()) {
                isPrivateUnlocked = true
                isPrivateAreaExpanded = true
                currentScreen = SandboxScreen.MAIN
                return@SandboxNavigation
            }
            
            val onUnlockSuccess = {
                if (securityManager.isSetup() && !securityManager.hasSecurityQuestion()) {
                    currentScreen = SandboxScreen.SETUP_SECURITY_QUESTION
                } else {
                    isPrivateUnlocked = true
                    isPrivateAreaExpanded = true
                    securityManager.setLastUnlockTime()
                    currentScreen = SandboxScreen.MAIN
                }
            }
            
            BackHandler {
                currentScreen = SandboxScreen.MAIN
            }
            
            LaunchedEffect(Unit) {
                if (currentBiometricEnabled && securityManager.isBiometricAvailable() && currentPreferBiometric) {
                    onShowBiometricPrompt(onUnlockSuccess)
                }
            }
            
            when (securityType) {
                SecurityType.PIN -> {
                    LockScreen(
                        isSetup = false,
                        onUnlock = onUnlockSuccess,
                        onPinEntered = { pin -> securityManager.verifyCredential(pin) },
                        biometricType = if (currentBiometricEnabled && securityManager.isBiometricAvailable()) 
                            securityManager.getBiometricType() else SandboxSecurityManager.BiometricType.NONE,
                        onBiometricClick = { onShowBiometricPrompt(onUnlockSuccess) },
                        onBack = { currentScreen = SandboxScreen.MAIN },
                        onForgotPassword = if (securityManager.hasSecurityQuestion()) {
                            { currentScreen = SandboxScreen.FORGOT_PASSWORD }
                        } else null
                    )
                }
                SecurityType.PASSWORD -> {
                    PasswordScreen(
                        isSetup = false,
                        onUnlock = onUnlockSuccess,
                        onPasswordEntered = { password -> securityManager.verifyCredential(password) },
                        biometricType = if (currentBiometricEnabled && securityManager.isBiometricAvailable()) 
                            securityManager.getBiometricType() else SandboxSecurityManager.BiometricType.NONE,
                        onBiometricClick = { onShowBiometricPrompt(onUnlockSuccess) },
                        onBack = { currentScreen = SandboxScreen.MAIN },
                        onForgotPassword = if (securityManager.hasSecurityQuestion()) {
                            { currentScreen = SandboxScreen.FORGOT_PASSWORD }
                        } else null
                    )
                }
                SecurityType.PATTERN -> {
                    PatternScreen(
                        isSetup = false,
                        onUnlock = onUnlockSuccess,
                        onPatternEntered = { pattern -> securityManager.verifyPattern(pattern) },
                        biometricType = if (currentBiometricEnabled && securityManager.isBiometricAvailable()) 
                            securityManager.getBiometricType() else SandboxSecurityManager.BiometricType.NONE,
                        onBiometricClick = { onShowBiometricPrompt(onUnlockSuccess) },
                        onBack = { currentScreen = SandboxScreen.MAIN },
                        onForgotPassword = if (securityManager.hasSecurityQuestion()) {
                            { currentScreen = SandboxScreen.FORGOT_PASSWORD }
                        } else null
                    )
                }
                SecurityType.NONE -> {
                    isPrivateUnlocked = true
                    isPrivateAreaExpanded = true
                    currentScreen = SandboxScreen.MAIN
                }
            }
        }
        
        SandboxScreen.SETUP_TYPE_SELECTOR -> {
            SecuritySetupScreen(
                onSecurityTypeSelected = { type ->
                    selectedSecurityType = type
                    firstCredential = null
                    isConfirmStep = false
                    currentScreen = SandboxScreen.SETUP_CREDENTIAL
                },
                onSkip = {
                    currentScreen = SandboxScreen.MAIN
                }
            )
        }
        
        SandboxScreen.SETUP_CREDENTIAL -> {
            val handleBack = {
                if (isConfirmStep) {
                    isConfirmStep = false
                    firstCredential = null
                } else {
                    currentScreen = SandboxScreen.SETUP_TYPE_SELECTOR
                }
            }
            
            BackHandler { handleBack() }
            
            when (selectedSecurityType) {
                SecurityType.PIN -> {
                    LockScreen(
                        isSetup = true,
                        onUnlock = {
                            if (!isConfirmStep) {
                                isConfirmStep = true
                            } else {
                                securityManager.setPin(firstCredential as String)
                                currentSecurityType = SecurityType.PIN
                                currentScreen = SandboxScreen.SETUP_SECURITY_QUESTION
                            }
                        },
                        onPinEntered = { pin ->
                            if (!isConfirmStep) {
                                firstCredential = pin
                                true
                            } else {
                                pin == (firstCredential as? String)
                            }
                        },
                        confirmPin = if (isConfirmStep) firstCredential as? String else null,
                        onBack = handleBack
                    )
                }
                SecurityType.PASSWORD -> {
                    PasswordScreen(
                        isSetup = true,
                        onUnlock = {
                            if (!isConfirmStep) {
                                isConfirmStep = true
                            } else {
                                securityManager.setPassword(firstCredential as String)
                                currentSecurityType = SecurityType.PASSWORD
                                currentScreen = SandboxScreen.SETUP_SECURITY_QUESTION
                            }
                        },
                        onPasswordEntered = { password ->
                            if (!isConfirmStep) {
                                firstCredential = password
                                true
                            } else {
                                password == (firstCredential as? String)
                            }
                        },
                        confirmPassword = if (isConfirmStep) firstCredential as? String else null,
                        onBack = handleBack
                    )
                }
                SecurityType.PATTERN -> {
                    PatternScreen(
                        isSetup = true,
                        onUnlock = {
                            if (!isConfirmStep) {
                                isConfirmStep = true
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                securityManager.setPattern(firstCredential as List<Int>)
                                currentSecurityType = SecurityType.PATTERN
                                currentScreen = SandboxScreen.SETUP_SECURITY_QUESTION
                            }
                        },
                        onPatternEntered = { pattern ->
                            if (!isConfirmStep) {
                                firstCredential = pattern
                                true
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                pattern == (firstCredential as? List<Int>)
                            }
                        },
                        confirmPattern = if (isConfirmStep) {
                            @Suppress("UNCHECKED_CAST")
                            firstCredential as? List<Int>
                        } else null,
                        onBack = handleBack
                    )
                }
                SecurityType.NONE -> {
                    currentScreen = SandboxScreen.MAIN
                }
            }
        }
        
        SandboxScreen.MAIN -> {
            BackHandler(enabled = isSearchExpanded) {
                isSearchExpanded = false
            }
            
            SandboxApp(
                onSettingsClick = { currentScreen = SandboxScreen.SETTINGS },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { expanded -> isSearchExpanded = expanded },
                isPrivateUnlocked = isPrivateUnlocked,
                onUnlockRequest = { 
                    currentScreen = SandboxScreen.UNLOCK_PRIVATE
                },
                isPrivateAreaExpanded = isPrivateAreaExpanded,
                onPrivateAreaExpandChange = { expanded -> 
                    isPrivateAreaExpanded = expanded
                },
                isSecuritySetup = securityManager.isSetup(),
                onSetupSecurity = {
                    currentScreen = SandboxScreen.SETUP_TYPE_SELECTOR
                }
            )
        }
        
        SandboxScreen.SETTINGS -> {
            BackHandler {
                currentScreen = SandboxScreen.MAIN
            }
            
            SettingsScreen(
                currentSecurityType = currentSecurityType,
                currentLockedAppBehavior = currentLockedAppBehavior,
                currentLockedAppTimeout = currentLockedAppTimeout,
                currentPrivateBehavior = currentPrivateBehavior,
                currentPrivateTimeout = currentPrivateTimeout,
                isBiometricAvailable = securityManager.isBiometricAvailable(),
                isBiometricEnabled = currentBiometricEnabled,
                isPreferBiometric = currentPreferBiometric,
                onBackClick = { currentScreen = SandboxScreen.MAIN },
                onChangeSecurityType = { type ->
                    if (securityManager.isSetup()) {
                        pendingSecurityType = type
                        currentScreen = SandboxScreen.VERIFY_CURRENT_CREDENTIAL
                    } else {
                        selectedSecurityType = type
                        firstCredential = null
                        isConfirmStep = false
                        currentScreen = SandboxScreen.CHANGE_SECURITY
                    }
                },
                onChangeLockedAppBehavior = { behavior ->
                    securityManager.setLockedAppBehavior(behavior)
                    currentLockedAppBehavior = behavior
                },
                onChangeLockedAppTimeout = { timeout ->
                    securityManager.setLockedAppTimeout(timeout)
                    currentLockedAppTimeout = timeout
                },
                onChangePrivateBehavior = { behavior ->
                    securityManager.setPrivateSectionBehavior(behavior)
                    currentPrivateBehavior = behavior
                    if (behavior == PrivateSectionBehavior.TIMEOUT) {
                        securityManager.setLastUnlockTime()
                    }
                },
                onChangePrivateTimeout = { timeout ->
                    securityManager.setPrivateSectionTimeout(timeout)
                    currentPrivateTimeout = timeout
                },
                onChangeBiometricEnabled = { enabled ->
                    securityManager.setBiometricEnabled(enabled)
                    currentBiometricEnabled = enabled
                    if (!enabled) {
                        currentPreferBiometric = false
                    }
                },
                onChangePreferBiometric = { preferred ->
                    securityManager.setPreferBiometric(preferred)
                    currentPreferBiometric = preferred
                },
                hasSecurityQuestion = hasSecurityQuestion,
                onSetupRecovery = {
                    currentScreen = SandboxScreen.SETTINGS_RECOVERY
                },
                onForgotPassword = {
                    forgotPasswordReturnScreen = SandboxScreen.SETTINGS
                    currentScreen = SandboxScreen.FORGOT_PASSWORD
                }
            )
        }
        
        SandboxScreen.VERIFY_CURRENT_CREDENTIAL -> {
            val securityType = securityManager.getSecurityType()
            
            val onVerified = {
                selectedSecurityType = pendingSecurityType ?: SecurityType.PIN
                pendingSecurityType = null
                firstCredential = null
                isConfirmStep = false
                currentScreen = SandboxScreen.CHANGE_SECURITY
            }
            
            BackHandler {
                pendingSecurityType = null
                currentScreen = SandboxScreen.SETTINGS
            }
            
            when (securityType) {
                SecurityType.PIN -> {
                    LockScreen(
                        isSetup = false,
                        promptText = stringResource(R.string.enter_current_pin_to_change),
                        onUnlock = onVerified,
                        onPinEntered = { pin -> securityManager.verifyCredential(pin) },
                        biometricType = if (currentBiometricEnabled && securityManager.isBiometricAvailable()) 
                            securityManager.getBiometricType() else SandboxSecurityManager.BiometricType.NONE,
                        onBiometricClick = { onShowBiometricPrompt(onVerified) },
                        onBack = { 
                            pendingSecurityType = null
                            currentScreen = SandboxScreen.SETTINGS 
                        }
                    )
                }
                SecurityType.PASSWORD -> {
                    PasswordScreen(
                        isSetup = false,
                        promptText = stringResource(R.string.enter_current_password_to_change),
                        onUnlock = onVerified,
                        onPasswordEntered = { password -> securityManager.verifyCredential(password) },
                        biometricType = if (currentBiometricEnabled && securityManager.isBiometricAvailable()) 
                            securityManager.getBiometricType() else SandboxSecurityManager.BiometricType.NONE,
                        onBiometricClick = { onShowBiometricPrompt(onVerified) },
                        onBack = { 
                            pendingSecurityType = null
                            currentScreen = SandboxScreen.SETTINGS 
                        }
                    )
                }
                SecurityType.PATTERN -> {
                    PatternScreen(
                        isSetup = false,
                        promptText = stringResource(R.string.draw_current_pattern_to_change),
                        onUnlock = onVerified,
                        onPatternEntered = { pattern -> securityManager.verifyPattern(pattern) },
                        biometricType = if (currentBiometricEnabled && securityManager.isBiometricAvailable()) 
                            securityManager.getBiometricType() else SandboxSecurityManager.BiometricType.NONE,
                        onBiometricClick = { onShowBiometricPrompt(onVerified) },
                        onBack = { 
                            pendingSecurityType = null
                            currentScreen = SandboxScreen.SETTINGS 
                        }
                    )
                }
                SecurityType.NONE -> {
                    selectedSecurityType = pendingSecurityType ?: SecurityType.PIN
                    pendingSecurityType = null
                    firstCredential = null
                    isConfirmStep = false
                    currentScreen = SandboxScreen.CHANGE_SECURITY
                }
            }
        }
        
        SandboxScreen.CHANGE_SECURITY -> {
            val handleBack = {
                if (isConfirmStep) {
                    isConfirmStep = false
                    firstCredential = null
                } else {
                    currentScreen = SandboxScreen.SETTINGS
                }
            }
            
            BackHandler { handleBack() }
            
            when (selectedSecurityType) {
                SecurityType.PIN -> {
                    LockScreen(
                        isSetup = true,
                        onUnlock = {
                            if (!isConfirmStep) {
                                isConfirmStep = true
                            } else {
                                securityManager.setPin(firstCredential as String)
                                currentSecurityType = SecurityType.PIN
                                currentScreen = SandboxScreen.SETTINGS
                            }
                        },
                        onPinEntered = { pin ->
                            if (!isConfirmStep) {
                                firstCredential = pin
                                true
                            } else {
                                pin == (firstCredential as? String)
                            }
                        },
                        confirmPin = if (isConfirmStep) firstCredential as? String else null,
                        onBack = handleBack
                    )
                }
                SecurityType.PASSWORD -> {
                    PasswordScreen(
                        isSetup = true,
                        onUnlock = {
                            if (!isConfirmStep) {
                                isConfirmStep = true
                            } else {
                                securityManager.setPassword(firstCredential as String)
                                currentSecurityType = SecurityType.PASSWORD
                                currentScreen = SandboxScreen.SETTINGS
                            }
                        },
                        onPasswordEntered = { password ->
                            if (!isConfirmStep) {
                                firstCredential = password
                                true
                            } else {
                                password == (firstCredential as? String)
                            }
                        },
                        confirmPassword = if (isConfirmStep) firstCredential as? String else null,
                        onBack = handleBack
                    )
                }
                SecurityType.PATTERN -> {
                    PatternScreen(
                        isSetup = true,
                        onUnlock = {
                            if (!isConfirmStep) {
                                isConfirmStep = true
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                securityManager.setPattern(firstCredential as List<Int>)
                                currentSecurityType = SecurityType.PATTERN
                                currentScreen = SandboxScreen.SETTINGS
                            }
                        },
                        onPatternEntered = { pattern ->
                            if (!isConfirmStep) {
                                firstCredential = pattern
                                true
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                pattern == (firstCredential as? List<Int>)
                            }
                        },
                        confirmPattern = if (isConfirmStep) {
                            @Suppress("UNCHECKED_CAST")
                            firstCredential as? List<Int>
                        } else null,
                        onBack = handleBack
                    )
                }
                SecurityType.NONE -> {
                    currentScreen = SandboxScreen.SETTINGS
                }
            }
        }
        
        SandboxScreen.SETUP_SECURITY_QUESTION -> {
            SecurityQuestionSetupScreen(
                onComplete = { question, answer ->
                    securityManager.setSecurityQuestion(question, answer)
                    hasSecurityQuestion = true
                    isPrivateUnlocked = true
                    isPrivateAreaExpanded = true
                    securityManager.setLastUnlockTime()
                    currentScreen = SandboxScreen.MAIN
                },
                onBack = {
                    isPrivateUnlocked = true
                    isPrivateAreaExpanded = true
                    securityManager.setLastUnlockTime()
                    currentScreen = SandboxScreen.MAIN
                }
            )
        }
        
        SandboxScreen.SETTINGS_RECOVERY -> {
            BackHandler {
                currentScreen = SandboxScreen.SETTINGS
            }
            
            SecurityQuestionSetupScreen(
                onComplete = { question, answer ->
                    securityManager.setSecurityQuestion(question, answer)
                    hasSecurityQuestion = true
                    currentScreen = SandboxScreen.SETTINGS
                },
                onBack = {
                    currentScreen = SandboxScreen.SETTINGS
                }
            )
        }
        
        SandboxScreen.FORGOT_PASSWORD -> {
            val securityQuestion = securityManager.getSecurityQuestion()
            
            if (securityQuestion == null) {
                currentScreen = SandboxScreen.UNLOCK_PRIVATE
                return@SandboxNavigation
            }
            
            BackHandler {
                currentScreen = forgotPasswordReturnScreen
            }
            
            ForgotPasswordScreen(
                securityQuestion = securityQuestion,
                onAnswerSubmit = { answer ->
                    securityManager.verifySecurityAnswer(answer)
                },
                onSuccess = {
                    securityManager.clearCredentials()
                    currentSecurityType = SecurityType.NONE
                    currentBiometricEnabled = false
                    hasSecurityQuestion = false
                    isPrivateUnlocked = true
                    isPrivateAreaExpanded = true
                    currentScreen = SandboxScreen.SETUP_TYPE_SELECTOR
                },
                onBack = {
                    currentScreen = forgotPasswordReturnScreen
                }
            )
        }
    }
}
