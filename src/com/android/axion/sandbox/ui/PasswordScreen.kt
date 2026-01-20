package com.android.axion.sandbox.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.axion.sandbox.R
import com.android.axion.sandbox.security.SandboxSecurityManager

private object PasswordScreenShapes {
    val input = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(28.dp)
}

@Composable
fun PasswordScreen(
    isSetup: Boolean = false,
    promptText: String? = null,
    onUnlock: () -> Unit,
    onPasswordEntered: (String) -> Boolean,
    confirmPassword: String? = null,
    onBack: (() -> Unit)? = null,
    biometricType: SandboxSecurityManager.BiometricType = SandboxSecurityManager.BiometricType.NONE,
    onBiometricClick: () -> Unit = {},
    onForgotPassword: (() -> Unit)? = null,
    isExiting: Boolean = false
) {
    var enteredPassword by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    LaunchedEffect(confirmPassword) {
        enteredPassword = ""
        isError = false
    }
    
    val shakeOffset by animateFloatAsState(
        targetValue = if (isError) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "shake",
        finishedListener = {
            if (isError) {
                isError = false
            }
        }
    )
    
    val shakeTranslation = if (isError) {
        kotlin.math.sin(shakeOffset * 4 * Math.PI.toFloat()) * 16f
    } else 0f
    
    fun submit() {
        if (enteredPassword.length >= 4) {
            val success = onPasswordEntered(enteredPassword)
            if (success) {
                onUnlock()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isError = true
                errorMessage = if (isSetup && confirmPassword != null) context.getString(R.string.passwords_mismatched) else context.getString(R.string.incorrect_password)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
    ) {


        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .widthIn(max = 400.dp)
        ) {
            Text(
                text = if (isSetup) {
                    if (confirmPassword == null) stringResource(R.string.create_password) else stringResource(R.string.confirm_password)
                } else stringResource(R.string.enter_password),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = promptText ?: if (isSetup) {
                    if (confirmPassword == null) stringResource(R.string.choose_password_prompt) else stringResource(R.string.reenter_password_prompt)
                } else stringResource(R.string.enter_password_to_unlock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            OutlinedTextField(
                value = enteredPassword,
                onValueChange = { 
                    enteredPassword = it
                    if (isError) isError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shakeTranslation },
                label = { Text(stringResource(R.string.password)) },
                placeholder = { Text(stringResource(R.string.enter_password_placeholder)) },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                ),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility 
                                          else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) stringResource(R.string.hide) else stringResource(R.string.show)
                        )
                    }
                },
                isError = isError,
                supportingText = if (isError) {
                    { Text(errorMessage, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                } else null,
                shape = PasswordScreenShapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = enteredPassword.length >= 4,
                shape = PasswordScreenShapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isSetup) {
                        if (confirmPassword == null) stringResource(R.string.continue_text) else stringResource(R.string.set_password)
                    } else stringResource(R.string.unlock),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            if (!isSetup && biometricType != SandboxSecurityManager.BiometricType.NONE) {
                Spacer(modifier = Modifier.height(24.dp))
                IconButton(
                    onClick = onBiometricClick,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (biometricType == SandboxSecurityManager.BiometricType.FACE) 
                            Icons.Filled.Face else Icons.Filled.Fingerprint,
                        contentDescription = stringResource(R.string.biometric_unlock),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            if (!isSetup && onForgotPassword != null) {
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onForgotPassword) {
                    Text(
                        text = stringResource(R.string.forgot_password_q),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
