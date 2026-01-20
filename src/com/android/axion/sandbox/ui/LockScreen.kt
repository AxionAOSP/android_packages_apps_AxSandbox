package com.android.axion.sandbox.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.android.axion.sandbox.R
import com.android.axion.sandbox.security.SandboxSecurityManager

private object LockScreenShapes {
    val button = CircleShape
    val indicator = CircleShape
    val container = RoundedCornerShape(32.dp)
}

@Composable
fun LockScreen(
    isSetup: Boolean = false,
    promptText: String? = null,
    onUnlock: () -> Unit,
    onPinEntered: (String) -> Boolean,
    confirmPin: String? = null,
    onBack: (() -> Unit)? = null,
    biometricType: SandboxSecurityManager.BiometricType = SandboxSecurityManager.BiometricType.NONE,
    onBiometricClick: () -> Unit = {},
    onForgotPassword: (() -> Unit)? = null,
    isExiting: Boolean = false
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    LaunchedEffect(confirmPin) {
        enteredPin = ""
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
                enteredPin = ""
            }
        }
    )
    
    val shakeTranslation = if (isError) {
        kotlin.math.sin(shakeOffset * 4 * Math.PI.toFloat()) * 20f
    } else 0f
    
    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            delay(100) 
            val success = onPinEntered(enteredPin)
            if (success) {
                onUnlock()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                errorMessage = if (isSetup && confirmPin != null) context.getString(R.string.pins_mismatched) else context.getString(R.string.incorrect_pin)
                isError = true
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
        ) {
            Text(
                text = if (isSetup) {
                    if (confirmPin == null) stringResource(R.string.create_pin) else stringResource(R.string.confirm_pin)
                } else stringResource(R.string.enter_pin),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = promptText ?: if (isSetup) {
                    if (confirmPin == null) stringResource(R.string.choose_pin_prompt) else stringResource(R.string.reenter_pin_prompt)
                } else stringResource(R.string.enter_pin_to_unlock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.graphicsLayer { translationX = shakeTranslation }
            ) {
                repeat(4) { index ->
                    PinDot(
                        isFilled = index < enteredPin.length,
                        isError = isError
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isError) errorMessage else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(20.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                ).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        row.forEach { digit ->
                            NumberButton(
                                text = digit,
                                onClick = {
                                    if (enteredPin.length < 4 && !isError) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        enteredPin += digit
                                    }
                                }
                            )
                        }
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isSetup && biometricType != SandboxSecurityManager.BiometricType.NONE) {
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
                    }
                    
                    NumberButton(
                        text = "0",
                        onClick = {
                            if (enteredPin.length < 4 && !isError) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                enteredPin += "0"
                            }
                        }
                    )
                    
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (enteredPin.isNotEmpty() && !isError) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    enteredPin = enteredPin.dropLast(1)
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = stringResource(R.string.backspace),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
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

@Composable
private fun PinDot(
    isFilled: Boolean,
    isError: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isFilled) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dotScale"
    )
    
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        isFilled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    
    Box(
        modifier = Modifier
            .size(20.dp)
            .scale(scale)
            .clip(LockScreenShapes.indicator)
            .background(color)
    )
}

@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )
    
    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(LockScreenShapes.button)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
