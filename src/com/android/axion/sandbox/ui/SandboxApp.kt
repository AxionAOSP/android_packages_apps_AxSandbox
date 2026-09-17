/*
 * Copyright (C) 2025-2026 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.axion.sandbox.ui

import android.app.AxSandboxManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.axion.compose.applist.AppFilter
import com.android.axion.compose.applist.rememberAppList
import com.android.axion.compose.preferences.BasePreference
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SettingsType
import com.android.axion.compose.preferences.SwitchPreference
import com.android.axion.compose.preferences.rememberSettingsFlow
import com.android.axion.compose.scaffold.AxionLargeTopAppBar
import com.android.axion.compose.scaffold.AxionScaffold
import com.android.axion.compose.sheet.BottomSheetDialog
import com.android.axion.sandbox.R
import com.android.internal.app.IHiddenNotificationListener
import com.android.internal.app.HiddenNotificationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.OptIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SandboxApp"
private const val SANDBOX_CONFIG_KEY = "sandbox_config"
private const val SHEET_HEIGHT_FRACTION = 0.75f

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val uid: Int,
    val isLocked: Boolean,
    val isHidden: Boolean,
    val isLauncherHidden: Boolean,
    val isSandboxed: Boolean,
    val isSystem: Boolean = false,
    val userId: Int = 0,
    val isClone: Boolean = false
)

private data class UserSandboxPackages(
    val locked: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val launcherHidden: Set<String> = emptySet(),
    val sandboxed: Set<String> = emptySet()
)

private fun AxSandboxManager?.getUserPackages(userId: Int): UserSandboxPackages {
    if (this == null) return UserSandboxPackages()
    try {
        val locked = getLockedPackages(userId)?.toSet() ?: emptySet()
        val hidden = getHiddenPackages(userId)?.toSet() ?: emptySet()
        val launcherHidden = getHiddenFromLauncherPackages(userId)?.toSet() ?: emptySet()
        val sandboxed = getSandboxedPackages(userId)?.toSet() ?: emptySet()
        return UserSandboxPackages(locked, hidden, launcherHidden, sandboxed)
    } catch (e: Exception) {
        return UserSandboxPackages()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SandboxApp(
    onSettingsClick: () -> Unit = {},
    isSearchExpanded: Boolean = false,
    onSearchExpandedChange: (Boolean) -> Unit = {},
    isPrivateUnlocked: Boolean = false,
    onUnlockRequest: () -> Unit = {},
    isPrivateAreaExpanded: Boolean = false,
    onPrivateAreaExpandChange: (Boolean) -> Unit = {},
    isSecuritySetup: Boolean = false,
    onSetupSecurity: () -> Unit = {},
    selectedTabIndex: Int = 0,
    onTabIndexChange: (Int) -> Unit = {},
    isPickingFiles: Boolean = false,
    onPickingFilesChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val motionScheme = MaterialTheme.motionScheme

    val appsState = rememberAppList(AppFilter.ALL, AppFilter.NO_OVERLAYS, AppFilter.INCLUDE_DUAL_APPS)
    var refreshKey by remember { mutableStateOf(0) }
    val sandboxManager = remember { context.getSystemService(Context.AX_SANDBOX_SERVICE) as? AxSandboxManager }

    val apps by remember(appsState.value, refreshKey) {
        derivedStateOf {
            val userPackagesMap = appsState.value
                .map { it.userId }
                .distinct()
                .associateWith { userId -> sandboxManager.getUserPackages(userId) }

            appsState.value.map { entry ->
                val userPkgs = userPackagesMap[entry.userId] ?: UserSandboxPackages()
                AppInfo(
                    packageName = entry.packageName,
                    label = entry.label,
                    icon = entry.icon,
                    uid = 0,
                    isLocked = userPkgs.locked.contains(entry.packageName),
                    isHidden = userPkgs.hidden.contains(entry.packageName),
                    isLauncherHidden = userPkgs.launcherHidden.contains(entry.packageName),
                    isSandboxed = userPkgs.sandboxed.contains(entry.packageName),
                    isSystem = entry.isSystem,
                    userId = entry.userId,
                    isClone = entry.isClone
                )
            }
        }
    }
    val isLoading = appsState.value.isEmpty()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { 3 }
    )

    LaunchedEffect(pagerState.currentPage) {
        onTabIndexChange(pagerState.currentPage)
    }

    val reloadApps: () -> Unit = {
        refreshKey++
        selectedApp?.let { selected ->
            selectedApp = apps.find { it.packageName == selected.packageName && it.userId == selected.userId }
        }
    }

    val settingsFlow = rememberSettingsFlow(SettingsType.SECURE)
    LaunchedEffect(settingsFlow) {
        settingsFlow.observe(SANDBOX_CONFIG_KEY).collect {
            reloadApps()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isEmpty()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    var notificationCount by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? android.app.AxSandboxManager

        val listener = object : IHiddenNotificationListener.Stub() {
            override fun onHiddenNotificationPosted(info: HiddenNotificationInfo) {
                scope.launch(Dispatchers.Main) {
                    notificationCount++
                }
            }

            override fun onHiddenNotificationRemoved(key: String) {
                scope.launch(Dispatchers.Main) {
                    if (notificationCount > 0) notificationCount--
                }
            }
        }

        try {
            sandboxManager?.registerHiddenNotificationListener(listener)
            scope.launch(Dispatchers.IO) {
                val count = sandboxManager?.hiddenNotifications?.size ?: 0
                withContext(Dispatchers.Main) {
                    notificationCount = count
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register notification listener", e)
        }

        onDispose {
            try {
                sandboxManager?.unregisterHiddenNotificationListener(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister notification listener", e)
            }
        }
    }

    if (selectedApp != null) {
        AppDetailScreen(
            app = selectedApp!!,
            onBackClick = { selectedApp = null },
            onLockToggle = { app ->
                scope.launch {
                    toggleAppLock(context, app.packageName, app.userId, !app.isLocked)
                    reloadApps()
                }
            },
            onHideToggle = { app ->
                scope.launch {
                    val nextHideState = !app.isHidden
                    toggleAppHidden(context, app.packageName, app.userId, nextHideState)
                    if (nextHideState) {
                        toggleAppLauncherHidden(context, app.packageName, app.userId, false)
                        selectedApp = app.copy(isHidden = true, isLauncherHidden = false)
                    } else {
                        selectedApp = app.copy(isHidden = false)
                    }
                    reloadApps()
                }
            },
            onLauncherHideToggle = { app ->
                scope.launch {
                    val nextLauncherHideState = !app.isLauncherHidden
                    toggleAppLauncherHidden(context, app.packageName, app.userId, nextLauncherHideState)
                    if (nextLauncherHideState) {
                        toggleAppHidden(context, app.packageName, app.userId, false)
                        selectedApp = app.copy(isLauncherHidden = true, isHidden = false)
                    } else {
                        selectedApp = app.copy(isLauncherHidden = false)
                    }
                    reloadApps()
                }
            },
            onSandboxToggle = { app ->
                scope.launch {
                    toggleAppSandboxed(context, app.packageName, app.userId, !app.isSandboxed)
                    reloadApps()
                }
            },
            onLaunch = { app ->
                launchApp(context, app.packageName, app.userId)
            },
            isSecuritySetup = isSecuritySetup
        )
    } else {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                AxionLargeTopAppBar(
                    title = stringResource(R.string.sandbox_title),
                    scrollBehavior = scrollBehavior,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContent =
                        if (isSearchExpanded) {
                            {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.search_hint)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        } else null,
                    actions = {
                        FilledIconToggleButton(
                            checked = isSearchExpanded,
                            onCheckedChange = {
                                onSearchExpandedChange(it)
                                if (!it) searchQuery = ""
                            },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceBright,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search)
                            )
                        }

                        Box {
                            FilledIconButton(
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (notificationCount > 0)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceBright
                                )
                            ) {
                                Icon(
                                    imageVector = if (notificationCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = stringResource(R.string.action_notifications)
                                )
                            }

                            if (notificationCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (notificationCount > 9) "9+" else notificationCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }

                        FilledIconButton(
                            onClick = onSettingsClick,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceBright
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.action_settings)
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        icon = {
                            Icon(
                                imageVector = if (pagerState.currentPage == 0) Icons.Filled.Apps else Icons.Outlined.Apps,
                                contentDescription = stringResource(R.string.tab_apps)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_apps)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        icon = {
                            Icon(
                                imageVector = if (pagerState.currentPage == 1) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = stringResource(R.string.tab_notifications)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_notifications)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                        icon = {
                            Icon(
                                imageVector = if (pagerState.currentPage == 2) Icons.Filled.FolderOpen else Icons.Outlined.FolderOpen,
                                contentDescription = stringResource(R.string.tab_vault)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_vault)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { page ->
                when (page) {
                    0 -> AppsTab(
                        apps = filteredApps,
                        isLoading = isLoading,
                        onAppClick = { app -> selectedApp = app },
                        isPrivateAreaExpanded = isPrivateAreaExpanded,
                        onPrivateAreaExpandChange = onPrivateAreaExpandChange,
                        onUnlockRequest = onUnlockRequest,
                        isPrivateUnlocked = isPrivateUnlocked,
                        isSecuritySetup = isSecuritySetup,
                        onSetupSecurity = onSetupSecurity
                    )
                    1 -> NotificationsTab(
                        isUnlocked = isPrivateUnlocked,
                        onUnlockRequest = onUnlockRequest
                    )
                    2 -> VaultTab(
                        isUnlocked = isPrivateUnlocked,
                        onUnlockRequest = onUnlockRequest,
                        onPickingFilesChange = onPickingFilesChange
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SandboxOptionsDropdown(
    app: AppInfo,
    sandboxManager: AxSandboxManager?,
    onSandboxToggle: (AppInfo) -> Unit
) {
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.secondary
    var expanded by rememberSaveable { mutableStateOf(app.isSandboxed) }

    var restrictInternet by remember(app.packageName, app.userId) {
        val gids = sandboxManager?.getRestrictedGids(app.packageName, app.userId)
        mutableStateOf(gids != null && gids.contains(AxSandboxManager.GID_INET))
    }

    var restrictStorage by remember(app.packageName, app.userId) {
        val gids = sandboxManager?.getRestrictedGids(app.packageName, app.userId)
        val storageGids = AxSandboxManager.STORAGE_GIDS
        mutableStateOf(gids != null && storageGids.any { gids.contains(it) })
    }

    var dataIsolation by remember(app.packageName, app.userId) {
        mutableStateOf(sandboxManager?.isSandboxDataIsolationEnabled(app.packageName, app.userId) == true)
    }

    PreferenceGroup(
        title = stringResource(R.string.sandbox_options_title),
        collapsible = true,
        initiallyExpanded = expanded,
    ) {
        item {
            SwitchPreference(
                title = stringResource(R.string.action_isolate),
                summary = stringResource(R.string.isolate_app_description),
                checked = app.isSandboxed,
                onCheckedChange = { onSandboxToggle(app) },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.restrict_internet_title),
                summary = stringResource(R.string.restrict_internet_description),
                checked = restrictInternet,
                onCheckedChange = {
                    if (sandboxManager == null) return@SwitchPreference
                    restrictInternet = !restrictInternet
                    scope.launch(Dispatchers.IO) {
                        val current = sandboxManager.getRestrictedGids(app.packageName, app.userId)
                                ?.toMutableList() ?: mutableListOf()
                        if (restrictInternet) {
                            if (!current.contains(AxSandboxManager.GID_INET))
                                current.add(AxSandboxManager.GID_INET)
                        } else {
                            current.remove(AxSandboxManager.GID_INET)
                        }
                        sandboxManager.setRestrictedGids(app.packageName,
                                current.toIntArray(), app.userId)
                    }
                },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.restrict_storage_title),
                summary = stringResource(R.string.restrict_storage_description),
                checked = restrictStorage,
                onCheckedChange = {
                    if (sandboxManager == null) return@SwitchPreference
                    restrictStorage = !restrictStorage
                    scope.launch(Dispatchers.IO) {
                        val current = sandboxManager.getRestrictedGids(app.packageName, app.userId)
                                ?.toMutableList() ?: mutableListOf()
                        val storageGids = AxSandboxManager.STORAGE_GIDS
                        if (restrictStorage) {
                            for (gid in storageGids) {
                                if (!current.contains(gid)) current.add(gid)
                            }
                        } else {
                            current.removeAll(storageGids.toSet())
                        }
                        sandboxManager.setRestrictedGids(app.packageName,
                                current.toIntArray(), app.userId)
                    }
                },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.data_isolation_title),
                summary = stringResource(R.string.data_isolation_description),
                checked = dataIsolation,
                onCheckedChange = {
                    if (sandboxManager == null) return@SwitchPreference
                    dataIsolation = !dataIsolation
                    scope.launch(Dispatchers.IO) {
                        sandboxManager.setSandboxDataIsolationEnabled(
                                app.packageName, dataIsolation, app.userId)
                    }
                },
            )
        }
    }
}

private data class SpoofSettingEntry(
    val key: String,
    val titleRes: Int,
    val descriptionRes: Int
)

private val SPOOF_SETTINGS = listOf(
    SpoofSettingEntry("adb_enabled",
            R.string.spoof_adb_enabled, R.string.spoof_adb_enabled_description),
    SpoofSettingEntry("development_settings_enabled",
            R.string.spoof_dev_settings_enabled, R.string.spoof_dev_settings_enabled_description),
    SpoofSettingEntry("adb_wifi_enabled",
            R.string.spoof_adb_wifi_enabled, R.string.spoof_adb_wifi_enabled_description),
    SpoofSettingEntry("package_verifier_user_consent",
            R.string.spoof_package_verifier, R.string.spoof_package_verifier_description),
    SpoofSettingEntry("verify_apps_over_usb",
            R.string.spoof_verify_apps_usb, R.string.spoof_verify_apps_usb_description)
)

private val SPOOF_ACCESSIBILITY_KEYS = listOf(
    "accessibility_enabled",
    "enabled_accessibility_services",
    "accessibility_display_inversion_enabled"
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpoofSettingsDropdown(
    app: AppInfo,
    sandboxManager: AxSandboxManager?
) {
    if (sandboxManager == null) return

    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.tertiary
    var expanded by rememberSaveable { mutableStateOf(false) }

    val enabledSettings = remember(app.packageName, app.userId) {
        mutableStateMapOf<String, Boolean>().apply {
            SPOOF_SETTINGS.forEach { entry ->
                put(entry.key, sandboxManager.isSpoofSettingEnabled(
                        app.packageName, entry.key, app.userId))
            }
            put("accessibility_spoof", SPOOF_ACCESSIBILITY_KEYS.all { key ->
                sandboxManager.isSpoofSettingEnabled(app.packageName, key, app.userId)
            })
        }
    }

    val hasAnyEnabled by remember { derivedStateOf { enabledSettings.values.any { it } } }

    PreferenceGroup(
        title = stringResource(R.string.spoof_settings_title),
        collapsible = true,
        initiallyExpanded = expanded,
    ) {
        SPOOF_SETTINGS.forEach { entry ->
            item {
                val isEnabled = enabledSettings[entry.key] ?: false
                SwitchPreference(
                    title = stringResource(entry.titleRes),
                    summary = stringResource(entry.descriptionRes),
                    checked = isEnabled,
                    onCheckedChange = {
                        val newValue = !isEnabled
                        enabledSettings[entry.key] = newValue
                        scope.launch(Dispatchers.IO) {
                            sandboxManager.setSpoofSettingEnabled(
                                    app.packageName, entry.key, newValue, app.userId)
                        }
                    },
                )
            }
        }
        item {
            val accessibilityEnabled = enabledSettings["accessibility_spoof"] ?: false
            SwitchPreference(
                title = stringResource(R.string.spoof_accessibility),
                summary = stringResource(R.string.spoof_accessibility_description),
                checked = accessibilityEnabled,
                onCheckedChange = {
                    val newValue = !accessibilityEnabled
                    enabledSettings["accessibility_spoof"] = newValue
                    scope.launch(Dispatchers.IO) {
                        SPOOF_ACCESSIBILITY_KEYS.forEach { key ->
                            sandboxManager.setSpoofSettingEnabled(
                                    app.packageName, key, newValue, app.userId)
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HideSettingsDropdown(
    app: AppInfo,
    onHideToggle: (AppInfo) -> Unit,
    onLauncherHideToggle: (AppInfo) -> Unit
) {
    val expanded = remember(app.packageName, app.userId) { app.isHidden || app.isLauncherHidden }

    PreferenceGroup(
        title = stringResource(R.string.hide_settings_title),
        collapsible = true,
        initiallyExpanded = expanded,
    ) {
        item {
            SwitchPreference(
                title = stringResource(R.string.hide_app_title),
                summary = if (app.isHidden) stringResource(R.string.hide_app_description_on)
                else stringResource(R.string.hide_app_description_off),
                checked = app.isHidden,
                onCheckedChange = { onHideToggle(app) },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.hide_from_launcher_title),
                summary = if (app.isLauncherHidden) stringResource(R.string.hide_from_launcher_description_on)
                else stringResource(R.string.hide_from_launcher_description_off),
                checked = app.isLauncherHidden,
                onCheckedChange = { onLauncherHideToggle(app) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDetailScreen(
    app: AppInfo,
    onBackClick: () -> Unit,
    onLockToggle: (AppInfo) -> Unit,
    onHideToggle: (AppInfo) -> Unit,
    onLauncherHideToggle: (AppInfo) -> Unit,
    onSandboxToggle: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    isSecuritySetup: Boolean = false
) {
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(128, 128)
    }

    AxionScaffold(
        title = app.label,
        onBackClick = onBackClick,
        collapsedByDefault = false,
        actions = {
            FilledTonalIconButton(onClick = { onLaunch(app) }) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = stringResource(R.string.action_launch)
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceBright),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = BitmapPainter(bitmap.asImageBitmap()),
                        contentDescription = app.label,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            item {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (isSecuritySetup) {
                item {
                    PreferenceGroup {
                        item {
                            SwitchPreference(
                                title = stringResource(R.string.lock_app_title),
                                summary = stringResource(R.string.lock_app_description),
                                checked = app.isLocked,
                                onCheckedChange = { onLockToggle(app) },
                                icon = if (app.isLocked) Icons.Filled.Lock else Icons.Outlined.Lock,
                            )
                        }
                    }
                }

                item {
                    HideSettingsDropdown(
                        app = app,
                        onHideToggle = onHideToggle,
                        onLauncherHideToggle = onLauncherHideToggle
                    )
                }
            }

            item {
                val context = LocalContext.current
                val sandboxManager = remember {
                    context.getSystemService(android.app.AxSandboxManager::class.java)
                }
                SandboxOptionsDropdown(
                    app = app,
                    sandboxManager = sandboxManager,
                    onSandboxToggle = onSandboxToggle
                )
            }

            item {
                val context = LocalContext.current
                val sandboxManager = remember {
                    context.getSystemService(android.app.AxSandboxManager::class.java)
                }
                SpoofSettingsDropdown(
                    app = app,
                    sandboxManager = sandboxManager
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsTab(
    apps: List<AppInfo>,
    isLoading: Boolean,
    onAppClick: (AppInfo) -> Unit,
    isPrivateAreaExpanded: Boolean = false,
    onPrivateAreaExpandChange: (Boolean) -> Unit = {},
    onUnlockRequest: () -> Unit = {},
    isPrivateUnlocked: Boolean = false,
    isSecuritySetup: Boolean = false,
    onSetupSecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }

    val privateApps = remember(apps) { apps.filter { it.isLocked || it.isHidden } }
    val sandboxedApps = remember(apps) { apps.filter { it.isSandboxed && !it.isLocked && !it.isHidden } }
    val regularApps = remember(apps) { apps.filter { !it.isLocked && !it.isHidden && !it.isSandboxed && !it.isSystem } }
    val systemApps = remember(apps) { apps.filter { !it.isLocked && !it.isHidden && !it.isSandboxed && it.isSystem } }

    val effectiveExpanded = isPrivateUnlocked && isPrivateAreaExpanded

    val onLockToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            toggleAppLock(context, app.packageName, app.userId, !app.isLocked)
            selectedApp = app.copy(isLocked = !app.isLocked)
        }
    }

    val onHideToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            val nextHideState = !app.isHidden
            toggleAppHidden(context, app.packageName, app.userId, nextHideState)
            if (nextHideState) {
                toggleAppLauncherHidden(context, app.packageName, app.userId, false)
                selectedApp = app.copy(isHidden = true, isLauncherHidden = false)
            } else {
                selectedApp = app.copy(isHidden = false)
            }
        }
    }

    val onLauncherHideToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            val nextLauncherHideState = !app.isLauncherHidden
            toggleAppLauncherHidden(context, app.packageName, app.userId, nextLauncherHideState)
            if (nextLauncherHideState) {
                toggleAppHidden(context, app.packageName, app.userId, false)
                selectedApp = app.copy(isLauncherHidden = true, isHidden = false)
            } else {
                selectedApp = app.copy(isLauncherHidden = false)
            }
        }
    }

    val onSandboxToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            toggleAppSandboxed(context, app.packageName, app.userId, !app.isSandboxed)
            selectedApp = app.copy(isSandboxed = !app.isSandboxed)
        }
    }

    val onSheetDismiss = {
        showBottomSheet = false
        selectedApp = null
    }

    if (showBottomSheet && selectedApp != null) {
        BottomSheetDialog(
            onDismiss = onSheetDismiss,
            heightFraction = SHEET_HEIGHT_FRACTION,
        ) {
            AppQuickActionsSheet(
                app = selectedApp!!,
                onLockToggle = onLockToggle,
                onHideToggle = onHideToggle,
                onLauncherHideToggle = onLauncherHideToggle,
                onSandboxToggle = onSandboxToggle,
                onLaunch = { app -> launchApp(context, app.packageName, app.userId) },
                onDismiss = onSheetDismiss,
            )
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    } else if (apps.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.no_apps_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(4) }) {
                if (!isSecuritySetup) {
                    PreferenceGroup(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        item {
                            ClickablePreference(
                                title = stringResource(R.string.setup_private_apps_title),
                                summary = stringResource(R.string.setup_private_apps_description),
                                icon = Icons.Filled.Lock,
                                onClick = onSetupSecurity
                            )
                        }
                    }
                } else if (privateApps.isNotEmpty()) {
                    CollapsibleSectionHeader(
                        icon = if (effectiveExpanded) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        title = stringResource(R.string.section_private_apps),
                        count = privateApps.size,
                        color = MaterialTheme.colorScheme.primary,
                        isExpanded = effectiveExpanded,
                        onExpandChange = { wantExpand ->
                            if (wantExpand && !isPrivateUnlocked) {
                                onUnlockRequest()
                            } else {
                                onPrivateAreaExpandChange(wantExpand)
                            }
                        }
                    )
                }
            }

            if (isSecuritySetup && privateApps.isNotEmpty() && effectiveExpanded) {
                items(privateApps, key = { "private_${it.packageName}" }) { app ->
                    AppGridItem(
                        app = app,
                        onClick = {
                            selectedApp = app
                            showBottomSheet = true
                        }
                    )
                }
            }

            if (isSecuritySetup && privateApps.isNotEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (sandboxedApps.isNotEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    SectionHeader(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.section_isolated_apps),
                        count = sandboxedApps.size,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                items(sandboxedApps, key = { "sandboxed_${it.packageName}" }) { app ->
                    AppGridItem(
                        app = app,
                        onClick = {
                            selectedApp = app
                            showBottomSheet = true
                        }
                    )
                }

                item(span = { GridItemSpan(4) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item(span = { GridItemSpan(4) }) {
                SectionHeader(
                    icon = Icons.Filled.Apps,
                    title = stringResource(R.string.section_all_apps),
                    count = regularApps.size,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        FilterChip(
                            selected = showSystemApps,
                            onClick = { showSystemApps = !showSystemApps },
                            label = {
                                Text(
                                    text = stringResource(R.string.show_system_apps),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = if (showSystemApps) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                )
            }

            items(regularApps, key = { "regular_${it.packageName}" }) { app ->
                AppGridItem(
                    app = app,
                    onClick = {
                        selectedApp = app
                        showBottomSheet = true
                    }
                )
            }

            if (showSystemApps && systemApps.isNotEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.show_system_apps),
                        count = systemApps.size,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                items(systemApps, key = { "system_${it.packageName}" }) { app ->
                    AppGridItem(
                        app = app,
                        onClick = {
                            selectedApp = app
                            showBottomSheet = true
                        }
                    )
                }
            }

            item(span = { GridItemSpan(4) }) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = color
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f)
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollapsibleSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onExpandChange(!isExpanded) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .wrapContentSize()
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = color,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotationAngle }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AppGridItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    val protectionStates = listOfNotNull(
        if (app.isLocked) Pair(Icons.Filled.Lock, MaterialTheme.colorScheme.primary) else null,
        if (app.isHidden) Pair(Icons.Filled.VisibilityOff, MaterialTheme.colorScheme.tertiary) else null,
        if (app.isSandboxed) Pair(Icons.Filled.Security, MaterialTheme.colorScheme.secondary) else null
    )
    val hasAnyProtection = protectionStates.isNotEmpty()

    val bitmap = remember(app.packageName, app.userId) {
        app.icon.toBitmap(64, 64)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = app.label,
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.large)
            )

            if (hasAnyProtection) {
                if (protectionStates.size == 1) {
                    val (badgeIcon, badgeColor) = protectionStates[0]
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${protectionStates.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 4.dp, y = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy((-2).dp)
                    ) {
                        protectionStates.take(3).forEachIndexed { _, (_, color) ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppQuickActionsSheet(
    app: AppInfo,
    onLockToggle: (AppInfo) -> Unit,
    onHideToggle: (AppInfo) -> Unit,
    onLauncherHideToggle: (AppInfo) -> Unit,
    onSandboxToggle: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = remember(app.packageName, app.userId) {
        app.icon.toBitmap(80, 80)
    }
    val context = LocalContext.current
    val sandboxManager = remember {
        context.getSystemService(AxSandboxManager::class.java)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PreferenceGroup {
                item {
                    BasePreference(
                        title = app.label,
                        summary = app.packageName,
                        customIcon = {
                            Image(
                                painter = BitmapPainter(bitmap.asImageBitmap()),
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        },
                        widget = {
                            FilledTonalIconButton(onClick = { onLaunch(app) }) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = stringResource(R.string.action_launch)
                                )
                            }
                        }
                    )
                }
            }
        }

        item {
            PreferenceGroup {
                item {
                    SwitchPreference(
                        title = stringResource(R.string.action_lock),
                        checked = app.isLocked,
                        onCheckedChange = { onLockToggle(app) },
                        icon = if (app.isLocked) Icons.Filled.Lock else Icons.Outlined.Lock,
                    )
                }
            }
        }

        item {
            HideSettingsDropdown(
                app = app,
                onHideToggle = onHideToggle,
                onLauncherHideToggle = onLauncherHideToggle
            )
        }

            item {
                SandboxOptionsDropdown(
                    app = app,
                    sandboxManager = sandboxManager,
                    onSandboxToggle = onSandboxToggle
                )
            }

            item {
                SpoofSettingsDropdown(
                    app = app,
                    sandboxManager = sandboxManager
                )
            }
        }
}

data class HiddenNotification(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val icon: Drawable? = null,
    val contentIntent: PendingIntent? = null,
    val userId: Int = 0
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsTab(
    isUnlocked: Boolean = false,
    onUnlockRequest: () -> Unit = {}
) {
    if (!isUnlocked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceBright),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.notifications_locked_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.notifications_locked_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                FilledTonalButton(
                    onClick = onUnlockRequest,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_unlock))
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<HiddenNotification>>(emptyList()) }

    DisposableEffect(Unit) {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? android.app.AxSandboxManager

        val listener = object : IHiddenNotificationListener.Stub() {
            override fun onHiddenNotificationPosted(info: HiddenNotificationInfo) {
                scope.launch(Dispatchers.Main) {
                    val pm = context.packageManager
                    val icon = info.appIcon?.loadDrawable(context) ?: try {
                         pm.getApplicationIcon(info.packageName)
                    } catch (e: Exception) { null }

                    val notif = HiddenNotification(
                        key = info.key,
                        packageName = info.packageName,
                        title = info.title?.toString(),
                        text = info.text?.toString(),
                        timestamp = info.postTime,
                        icon = icon,
                        contentIntent = info.contentIntent,
                        userId = info.userId
                    )
                    notifications = (listOf(notif) + notifications.filter { it.key != info.key })
                }
            }

            override fun onHiddenNotificationRemoved(key: String) {
                scope.launch(Dispatchers.Main) {
                    notifications = notifications.filter { it.key != key }
                }
            }
        }

        try {
            sandboxManager?.registerHiddenNotificationListener(listener)
            scope.launch(Dispatchers.IO) {
                val current = sandboxManager?.hiddenNotifications ?: emptyList()
                withContext(Dispatchers.Main) {
                    val pm = context.packageManager
                    notifications = current.map { info ->
                         val icon = info.appIcon?.loadDrawable(context) ?: try {
                             pm.getApplicationIcon(info.packageName)
                        } catch (e: Exception) { null }

                        HiddenNotification(
                            key = info.key,
                            packageName = info.packageName,
                            title = info.title?.toString(),
                            text = info.text?.toString(),
                            timestamp = info.postTime,
                            icon = icon,
                            contentIntent = info.contentIntent,
                            userId = info.userId
                        )
                    }.sortedByDescending { it.timestamp }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register notification listener", e)
        }

        onDispose {
            try {
                sandboxManager?.unregisterHiddenNotificationListener(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister notification listener", e)
            }
        }
    }

    if (notifications.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.no_private_notifications_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.no_private_notifications_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(notifications, key = { it.key }) { notification ->
                NotificationItem(
                    notification = notification,
                    onLaunch = {
                        try {
                            notification.contentIntent?.send()
                                ?: launchApp(context, notification.packageName, notification.userId)
                        } catch (e: Exception) {
                            launchApp(context, notification.packageName, notification.userId)
                        }
                    },
                    onDismiss = {
                        notifications = notifications.filter { it.key != notification.key }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: HiddenNotification,
    onLaunch: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (notification.icon != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = BitmapPainter(notification.icon.toBitmap().asImageBitmap()),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = notification.title ?: notification.packageName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!notification.text.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = notification.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_dismiss))
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onLaunch,
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open))
                }
            }
        }
    }
}

private suspend fun toggleAppLock(context: Context, packageName: String, userId: Int, lock: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? AxSandboxManager ?: return@withContext
        if (lock) sandboxManager.addLockedApp(packageName, userId) else sandboxManager.removeLockedApp(packageName, userId)
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling app lock for $packageName", e)
    }
}

private suspend fun toggleAppHidden(context: Context, packageName: String, userId: Int, hidden: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? AxSandboxManager ?: return@withContext
        sandboxManager.setPackageHidden(packageName, hidden, userId)
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling app hidden for $packageName", e)
    }
}

private suspend fun toggleAppLauncherHidden(context: Context, packageName: String, userId: Int, hidden: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? AxSandboxManager ?: return@withContext
        sandboxManager.setPackageHiddenFromLauncher(packageName, hidden, userId)
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling app launcher hidden for $packageName", e)
    }
}

private suspend fun toggleAppSandboxed(context: Context, packageName: String, userId: Int, sandboxed: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? AxSandboxManager ?: return@withContext
        if (sandboxed) sandboxManager.addSandboxedPackage(packageName, userId) else sandboxManager.removeSandboxedPackage(packageName, userId)
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling app sandboxed for $packageName", e)
    }
}

private fun launchApp(context: Context, packageName: String, userId: Int = 0) {
    try {
        if (userId != 0) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val activities = launcherApps?.getActivityList(packageName, UserHandle.of(userId))
            if (!activities.isNullOrEmpty()) {
                launcherApps.startMainActivity(activities[0].componentName, UserHandle.of(userId), null, null)
                return
            }
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error launching $packageName", e)
    }
}
