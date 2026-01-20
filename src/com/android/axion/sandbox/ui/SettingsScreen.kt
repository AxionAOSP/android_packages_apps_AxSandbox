package com.android.axion.sandbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.axion.sandbox.R
import com.android.axion.sandbox.security.LockedAppBehavior
import com.android.axion.sandbox.security.PrivateSectionBehavior
import com.android.axion.sandbox.security.SecurityType

private object SettingsShapes {
    val card = RoundedCornerShape(24.dp)
    val item = RoundedCornerShape(16.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSecurityType: SecurityType,
    currentLockedAppBehavior: LockedAppBehavior,
    currentLockedAppTimeout: Int,
    currentPrivateBehavior: PrivateSectionBehavior,
    currentPrivateTimeout: Int,
    isBiometricAvailable: Boolean,
    isBiometricEnabled: Boolean,
    isPreferBiometric: Boolean,
    hasSecurityQuestion: Boolean,
    onBackClick: () -> Unit,
    onChangeSecurityType: (SecurityType) -> Unit,
    onChangeLockedAppBehavior: (LockedAppBehavior) -> Unit,
    onChangeLockedAppTimeout: (Int) -> Unit,
    onChangePrivateBehavior: (PrivateSectionBehavior) -> Unit,
    onChangePrivateTimeout: (Int) -> Unit,
    onChangeBiometricEnabled: (Boolean) -> Unit,
    onChangePreferBiometric: (Boolean) -> Unit,
    onSetupRecovery: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var showLockedAppTimeoutDialog by remember { mutableStateOf(false) }
    var showPrivateTimeoutDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.private_apps_security),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    SettingsItem(
                        icon = when (currentSecurityType) {
                            SecurityType.PIN -> Icons.Outlined.Pin
                            SecurityType.PASSWORD -> Icons.Outlined.Password
                            SecurityType.PATTERN -> Icons.Outlined.Pattern
                            SecurityType.NONE -> Icons.Outlined.Lock
                        },
                        title = stringResource(R.string.current_lock_type),
                        subtitle = when (currentSecurityType) {
                            SecurityType.PIN -> stringResource(R.string.pin_description)
                            SecurityType.PASSWORD -> stringResource(R.string.password)
                            SecurityType.PATTERN -> stringResource(R.string.pattern)
                            SecurityType.NONE -> stringResource(R.string.not_set)
                        },
                        onClick = null
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Pin,
                        title = stringResource(R.string.use_pin),
                        subtitle = stringResource(R.string.pin_description),
                        isSelected = currentSecurityType == SecurityType.PIN,
                        onClick = { onChangeSecurityType(SecurityType.PIN) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Password,
                        title = stringResource(R.string.use_password),
                        subtitle = stringResource(R.string.password_description),
                        isSelected = currentSecurityType == SecurityType.PASSWORD,
                        onClick = { onChangeSecurityType(SecurityType.PASSWORD) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Pattern,
                        title = stringResource(R.string.use_pattern),
                        subtitle = stringResource(R.string.pattern_description),
                        isSelected = currentSecurityType == SecurityType.PATTERN,
                        onClick = { onChangeSecurityType(SecurityType.PATTERN) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isBiometricAvailable) {
                Text(
                    text = stringResource(R.string.biometrics),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsShapes.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Fingerprint,
                            title = stringResource(R.string.unlock_with_biometrics),
                            subtitle = stringResource(R.string.biometrics_description),
                            checked = isBiometricEnabled,
                            onCheckedChange = onChangeBiometricEnabled
                        )

                        if (isBiometricEnabled) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            SettingsSwitchItem(
                                icon = Icons.Outlined.AutoMode,
                                title = stringResource(R.string.auto_show_biometric_prompt),
                                subtitle = stringResource(R.string.auto_show_biometric_prompt_description),
                                checked = isPreferBiometric,
                                onCheckedChange = onChangePreferBiometric
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            if (currentSecurityType != SecurityType.NONE) {
                Text(
                    text = stringResource(R.string.recovery_options),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsShapes.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        if (hasSecurityQuestion) {
                            SettingsItem(
                                icon = Icons.Outlined.LockReset,
                                title = stringResource(R.string.forgot_password_item),
                                subtitle = stringResource(R.string.forgot_password_subtitle),
                                onClick = onForgotPassword
                            )
                        } else {
                            SettingsItem(
                                icon = Icons.Outlined.Help,
                                title = stringResource(R.string.setup_security_question),
                                subtitle = stringResource(R.string.recovery_required_subtitle),
                                onClick = onSetupRecovery
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            Text(
                text = stringResource(R.string.locked_app_behavior),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.locked_app_behavior_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    SettingsItem(
                        icon = Icons.Outlined.ExitToApp,
                        title = stringResource(R.string.relock_on_leave),
                        subtitle = stringResource(R.string.relock_on_leave_description),
                        isSelected = currentLockedAppBehavior == LockedAppBehavior.ON_LEAVE,
                        onClick = { onChangeLockedAppBehavior(LockedAppBehavior.ON_LEAVE) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.relock_after_timeout),
                        subtitle = stringResource(R.string.relock_after_timeout_description, currentLockedAppTimeout),
                        isSelected = currentLockedAppBehavior == LockedAppBehavior.TIMEOUT,
                        onClick = { 
                            onChangeLockedAppBehavior(LockedAppBehavior.TIMEOUT)
                            showLockedAppTimeoutDialog = true
                        }
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Smartphone,
                        title = stringResource(R.string.relock_on_screen_off),
                        subtitle = stringResource(R.string.relock_on_screen_off_description),
                        isSelected = currentLockedAppBehavior == LockedAppBehavior.ON_SCREEN_OFF,
                        onClick = { onChangeLockedAppBehavior(LockedAppBehavior.ON_SCREEN_OFF) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Close,
                        title = stringResource(R.string.relock_only_killed),
                        subtitle = stringResource(R.string.relock_only_killed_description),
                        isSelected = currentLockedAppBehavior == LockedAppBehavior.ON_KILL,
                        onClick = { onChangeLockedAppBehavior(LockedAppBehavior.ON_KILL) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.private_section_behavior),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.private_section_behavior_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    SettingsItem(
                        icon = Icons.Outlined.ExitToApp,
                        title = stringResource(R.string.collapse_on_leave),
                        subtitle = stringResource(R.string.collapse_on_leave_description),
                        isSelected = currentPrivateBehavior == PrivateSectionBehavior.ON_LEAVE,
                        onClick = { onChangePrivateBehavior(PrivateSectionBehavior.ON_LEAVE) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.collapse_after_timeout),
                        subtitle = stringResource(R.string.collapse_after_timeout_description, currentPrivateTimeout),
                        isSelected = currentPrivateBehavior == PrivateSectionBehavior.TIMEOUT,
                        onClick = { 
                            onChangePrivateBehavior(PrivateSectionBehavior.TIMEOUT)
                            showPrivateTimeoutDialog = true
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.version),
                        subtitle = "1.0.0",
                        onClick = null
                    )
                }
            }
        }
    }
    
    if (showLockedAppTimeoutDialog) {
        TimeoutPickerDialog(
            currentTimeout = currentLockedAppTimeout,
            onDismiss = { showLockedAppTimeoutDialog = false },
            onConfirm = { timeout ->
                onChangeLockedAppTimeout(timeout)
                showLockedAppTimeoutDialog = false
            }
        )
    }
    
    if (showPrivateTimeoutDialog) {
        TimeoutPickerDialog(
            currentTimeout = currentPrivateTimeout,
            onDismiss = { showPrivateTimeoutDialog = false },
            onConfirm = { timeout ->
                onChangePrivateTimeout(timeout)
                showPrivateTimeoutDialog = false
            }
        )
    }
}

@Composable
private fun TimeoutPickerDialog(
    currentTimeout: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val timeoutOptions = listOf(15, 30, 60, 120, 300)
    var selectedTimeout by remember { mutableStateOf(currentTimeout) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_timeout)) },
        text = {
            Column {
                timeoutOptions.forEach { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTimeout = seconds }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTimeout == seconds,
                            onClick = { selectedTimeout = seconds }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (seconds < 60) stringResource(R.string.seconds_format, seconds) 
                                   else if (seconds == 60) stringResource(R.string.minutes_format_singular, 1)
                                   else stringResource(R.string.minutes_format_plural, seconds / 60)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTimeout) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean = false,
    isDestructive: Boolean = false,
    onClick: (() -> Unit)?
) {
    val contentColor = when {
        isDestructive -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsShapes.item)
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsShapes.item)
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(checked.let { 
                    if (it) MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surfaceContainerHigh 
                }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            )
        )
    }
}

