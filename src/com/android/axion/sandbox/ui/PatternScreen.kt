package com.android.axion.sandbox.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.android.axion.sandbox.R
import com.android.axion.sandbox.security.SandboxSecurityManager
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun PatternScreen(
    isSetup: Boolean = false,
    promptText: String? = null,
    onUnlock: () -> Unit,
    onPatternEntered: (List<Int>) -> Boolean,
    confirmPattern: List<Int>? = null,
    onBack: (() -> Unit)? = null,
    biometricType: SandboxSecurityManager.BiometricType = SandboxSecurityManager.BiometricType.NONE,
    onBiometricClick: () -> Unit = {},
    onForgotPassword: (() -> Unit)? = null,
    isExiting: Boolean = false
) {
    var selectedDots by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentTouchPosition by remember { mutableStateOf<Offset?>(null) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var dotPositions by remember { mutableStateOf<Map<Int, Offset>>(emptyMap()) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    LaunchedEffect(confirmPattern) {
        selectedDots = emptyList()
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
                selectedDots = emptyList()
                errorMessage = ""
            }
        }
    )
    
    val shakeTranslation = if (isError) {
        kotlin.math.sin(shakeOffset * 4 * Math.PI.toFloat()) * 16f
    } else 0f
    
    fun submitPattern() {
        if (selectedDots.size >= 4) {
            val success = onPatternEntered(selectedDots)
            if (success) {
                onUnlock()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                errorMessage = if (isSetup && confirmPattern != null) context.getString(R.string.patterns_mismatched) else context.getString(R.string.wrong_pattern)
                isError = true
            }
        } else if (selectedDots.isNotEmpty()) {
            errorMessage = context.getString(R.string.connect_dots_prompt)
            isError = true
        }
    }
    
    val density = LocalDensity.current
    val dotRadius = with(density) { 12.dp.toPx() }
    val touchRadius = with(density) { 40.dp.toPx() }
    
    val errorColor = MaterialTheme.colorScheme.error
    val lineColor = if (isError) errorColor else MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val dotInactiveColor = MaterialTheme.colorScheme.outlineVariant
    
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
                    if (confirmPattern == null) stringResource(R.string.create_pattern) else stringResource(R.string.confirm_pattern)
                } else stringResource(R.string.draw_pattern),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = promptText ?: if (isSetup) {
                    if (confirmPattern == null) stringResource(R.string.connect_dots_prompt) else stringResource(R.string.draw_pattern_again_prompt)
                } else stringResource(R.string.draw_pattern_to_unlock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isError) errorMessage else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(20.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .graphicsLayer { translationX = shakeTranslation }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isError = false
                                errorMessage = ""
                                selectedDots = emptyList()
                                currentTouchPosition = offset
                                
                                val hitDot = findHitDot(offset, dotPositions, touchRadius)
                                if (hitDot != null && !selectedDots.contains(hitDot)) {
                                    selectedDots = listOf(hitDot)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDrag = { change, _ ->
                                currentTouchPosition = change.position
                                
                                val hitDot = findHitDot(change.position, dotPositions, touchRadius)
                                if (hitDot != null && !selectedDots.contains(hitDot)) {
                                    selectedDots = selectedDots + hitDot
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                currentTouchPosition = null
                                submitPattern()
                            },
                            onDragCancel = {
                                currentTouchPosition = null
                                selectedDots = emptyList()
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSize = 3
                    val cellSize = size.width / gridSize
                    
                    val positions = mutableMapOf<Int, Offset>()
                    for (row in 0 until gridSize) {
                        for (col in 0 until gridSize) {
                            val dotIndex = row * gridSize + col
                            val center = Offset(
                                x = col * cellSize + cellSize / 2,
                                y = row * cellSize + cellSize / 2
                            )
                            positions[dotIndex] = center
                        }
                    }
                    dotPositions = positions
                    
                    if (selectedDots.size > 1) {
                        for (i in 0 until selectedDots.size - 1) {
                            val from = positions[selectedDots[i]]!!
                            val to = positions[selectedDots[i + 1]]!!
                            drawLine(
                                color = lineColor,
                                start = from,
                                end = to,
                                strokeWidth = 8.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    
                    if (selectedDots.isNotEmpty() && currentTouchPosition != null) {
                        val lastDot = positions[selectedDots.last()]!!
                        drawLine(
                            color = lineColor.copy(alpha = 0.5f),
                            start = lastDot,
                            end = currentTouchPosition!!,
                            strokeWidth = 8.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    
                    for ((index, center) in positions) {
                        val isSelected = selectedDots.contains(index)
                        val color = when {
                            isError -> errorColor
                            isSelected -> dotColor
                            else -> dotInactiveColor
                        }
                        
                        drawCircle(
                            color = color.copy(alpha = if (isSelected) 0.3f else 0.2f),
                            radius = dotRadius * 2.5f,
                            center = center
                        )
                        
                        drawCircle(
                            color = color,
                            radius = if (isSelected) dotRadius * 1.3f else dotRadius,
                            center = center
                        )
                    }
                }
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

private fun findHitDot(
    position: Offset,
    dotPositions: Map<Int, Offset>,
    touchRadius: Float
): Int? {
    for ((index, center) in dotPositions) {
        val distance = sqrt(
            (position.x - center.x).pow(2) + (position.y - center.y).pow(2)
        )
        if (distance <= touchRadius) {
            return index
        }
    }
    return null
}
