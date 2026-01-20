package com.android.axion.sandbox.ui

import android.content.Context
import android.database.ContentObserver
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.axion.sandbox.R
import com.android.internal.app.IHiddenNotificationListener
import com.android.internal.app.HiddenNotificationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SandboxApp"

private object ExpressiveShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(20.dp)
    val large = RoundedCornerShape(28.dp)
    val extraLarge = RoundedCornerShape(32.dp)
    val full = CircleShape
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val uid: Int,
    val isLocked: Boolean,
    val isHidden: Boolean,
    val isSandboxed: Boolean,
    val isDevOptionsHidden: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onSetupSecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    
    var savedTabIndex by rememberSaveable { mutableStateOf(0) }
    
    val pagerState = rememberPagerState(
        initialPage = savedTabIndex,
        pageCount = { 2 }
    )
    
    LaunchedEffect(pagerState.currentPage) {
        savedTabIndex = pagerState.currentPage
    }
    
    val reloadApps: () -> Unit = {
        scope.launch {
            apps = loadInstalledApps(context)
            selectedApp?.let { selected ->
                selectedApp = apps.find { it.packageName == selected.packageName }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        apps = loadInstalledApps(context)
        isLoading = false
    }
    
    DisposableEffect(Unit) {
        val contentResolver = context.contentResolver
        val handler = Handler(Looper.getMainLooper())
        
        val sandboxObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                Log.d(TAG, "Sandbox settings changed")
                scope.launch {
                    apps = loadInstalledApps(context)
                }
            }
        }
        
        try {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("sandbox_config"),
                false, sandboxObserver
            )
            Log.d(TAG, "Registered Settings observer for sandbox_config")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Settings observer", e)
        }
        
        onDispose {
            try {
                contentResolver.unregisterContentObserver(sandboxObserver)
                Log.d(TAG, "Unregistered Settings observer")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister Settings observer", e)
            }
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
                    val newState = !app.isLocked
                    toggleAppLock(context, app.packageName, newState)
                    reloadApps()
                }
            },
            onHideToggle = { app ->
                scope.launch {
                    val newState = !app.isHidden
                    toggleAppHidden(context, app.packageName, newState)
                    reloadApps()
                }
            },
            onSandboxToggle = { app ->
                scope.launch {
                    val newState = !app.isSandboxed
                    toggleAppSandboxed(context, app.packageName, newState)
                    reloadApps()
                }
            },
            onDevOptionsToggle = { app ->
                scope.launch {
                    val newState = !app.isDevOptionsHidden
                    toggleDevOptionsHidden(context, app.packageName, newState)
                    reloadApps()
                }
            },
            onLaunch = { app ->
                launchApp(context, app.packageName)
            },
            isSecuritySetup = isSecuritySetup
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                ExpressiveNavigationBar(
                    selectedIndex = pagerState.currentPage,
                    onItemSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ExpressiveHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchExpanded = isSearchExpanded,
                    onSearchExpandedChange = { expanded ->
                        onSearchExpandedChange(expanded)
                        if (!expanded) searchQuery = ""
                    },
                    onNotificationsClick = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    },
                    notificationCount = notificationCount,
                    onSettingsClick = onSettingsClick
                )
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
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
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: AppInfo,
    onBackClick: () -> Unit,
    onLockToggle: (AppInfo) -> Unit,
    onHideToggle: (AppInfo) -> Unit,
    onSandboxToggle: (AppInfo) -> Unit,
    onDevOptionsToggle: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    isSecuritySetup: Boolean = false
) {
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(128, 128)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.label) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onLaunch(app) }) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = stringResource(R.string.launch)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(ExpressiveShapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
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
                    SettingsCard(
                        icon = Icons.Outlined.Lock,
                        activeIcon = Icons.Filled.Lock,
                        title = stringResource(R.string.lock_app),
                        description = stringResource(R.string.lock_app_description),
                        isEnabled = app.isLocked,
                        onToggle = { onLockToggle(app) },
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
                
                item {
                    SettingsCard(
                        icon = Icons.Outlined.VisibilityOff,
                        activeIcon = Icons.Filled.VisibilityOff,
                        title = stringResource(R.string.hide_app),
                        description = stringResource(R.string.hide_app_description),
                        isEnabled = app.isHidden,
                        onToggle = { onHideToggle(app) },
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            item {
                SettingsCard(
                    icon = Icons.Outlined.Security,
                    activeIcon = Icons.Filled.Security,
                    title = stringResource(R.string.sandbox),
                    description = stringResource(R.string.sandbox_description),
                    isEnabled = app.isSandboxed,
                    onToggle = { onSandboxToggle(app) },
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }
            
            item {
                SettingsCard(
                    icon = Icons.Outlined.Code,
                    activeIcon = Icons.Filled.Code,
                    title = stringResource(R.string.hide_dev_options),
                    description = stringResource(R.string.hide_dev_options_description),
                    isEnabled = app.isDevOptionsHidden,
                    onToggle = { onDevOptionsToggle(app) },
                    accentColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    activeIcon: ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    accentColor: Color
) {
    val cardColor by animateColorAsState(
        targetValue = if (isEnabled) {
            accentColor.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardColor"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconColor"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ExpressiveShapes.medium)
                    .background(
                        if (isEnabled) accentColor.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isEnabled) activeIcon else icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
fun ExpressiveHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onNotificationsClick: () -> Unit = {},
    notificationCount: Int = 0,
    onSettingsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (isSearchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { 
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = ExpressiveShapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sandbox),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            FilledIconToggleButton(
                checked = isSearchExpanded,
                onCheckedChange = { 
                    onSearchExpandedChange(it)
                    if (!it) onSearchQueryChange("")
                },
                shape = ExpressiveShapes.full,
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Box {
                FilledIconButton(
                    onClick = onNotificationsClick,
                    shape = ExpressiveShapes.full,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (notificationCount > 0) 
                            MaterialTheme.colorScheme.primaryContainer
                        else 
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(
                        imageVector = if (notificationCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.notifications)
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
                            text = if (notificationCount > 9) "9+" else stringResource(R.string.badge_count_format, notificationCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FilledIconButton(
                onClick = onSettingsClick,
                shape = ExpressiveShapes.full,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }
    }
}

@Composable
fun ExpressiveNavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .navigationBarsPadding()
            .clip(ExpressiveShapes.extraLarge)
            .shadow(
                elevation = 16.dp,
                shape = ExpressiveShapes.extraLarge,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedIndex == 0,
            onClick = { onItemSelected(0) },
            icon = {
                Icon(
                    imageVector = if (selectedIndex == 0) Icons.Filled.Apps else Icons.Outlined.Apps,
                    contentDescription = stringResource(R.string.apps)
                )
            },
            label = { Text(stringResource(R.string.apps)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            selected = selectedIndex == 1,
            onClick = { onItemSelected(1) },
            icon = {
                Icon(
                    imageVector = if (selectedIndex == 1) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.notifications)
                )
            },
            label = { Text(stringResource(R.string.notifications)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState()
    
    val privateApps = remember(apps) { apps.filter { it.isLocked || it.isHidden } }
    val sandboxedApps = remember(apps) { apps.filter { it.isSandboxed && !it.isLocked && !it.isHidden } }
    val devOptsHiddenApps = remember(apps) { apps.filter { it.isDevOptionsHidden && !it.isLocked && !it.isHidden && !it.isSandboxed } }
    val regularApps = remember(apps) { apps.filter { !it.isLocked && !it.isHidden && !it.isSandboxed && !it.isDevOptionsHidden } }
    
    val effectiveExpanded = isPrivateUnlocked && isPrivateAreaExpanded
    
    val onLockToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            val newState = !app.isLocked
            toggleAppLock(context, app.packageName, newState)
            selectedApp = app.copy(isLocked = newState)
        }
    }
    
    val onHideToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            val newState = !app.isHidden
            toggleAppHidden(context, app.packageName, newState)
            selectedApp = app.copy(isHidden = newState)
        }
    }
    
    val onSandboxToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            val newState = !app.isSandboxed
            toggleAppSandboxed(context, app.packageName, newState)
            selectedApp = app.copy(isSandboxed = newState)
        }
    }
    
    val onDevOptionsToggle: (AppInfo) -> Unit = { app ->
        scope.launch {
            val newState = !app.isDevOptionsHidden
            toggleDevOptionsHidden(context, app.packageName, newState)
            selectedApp = app.copy(isDevOptionsHidden = newState)
        }
    }
    
    if (showBottomSheet && selectedApp != null) {
        ModalBottomSheet(
            onDismissRequest = { 
                showBottomSheet = false
                selectedApp = null 
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            AppQuickActionsSheet(
                app = selectedApp!!,
                onLockToggle = onLockToggle,
                onHideToggle = onHideToggle,
                onSandboxToggle = onSandboxToggle,
                onDevOptionsToggle = onDevOptionsToggle,
                onLaunch = { app -> launchApp(context, app.packageName) },
                onDismiss = { 
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                            selectedApp = null
                        }
                    }
                }
            )
        }
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else if (apps.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(4) }) {
                if (!isSecuritySetup) {
                    SetupPrivateAppsCard(onSetupClick = onSetupSecurity)
                } else if (privateApps.isNotEmpty()) {
                    CollapsibleSectionHeader(
                        icon = if (effectiveExpanded) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        title = stringResource(R.string.private_apps),
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
                        title = stringResource(R.string.sandboxed_apps),
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
            
            if (devOptsHiddenApps.isNotEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    SectionHeader(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.dev_opts_hidden),
                        count = devOptsHiddenApps.size,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                items(devOptsHiddenApps, key = { "devopt_${it.packageName}" }) { app ->
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
                    title = stringResource(R.string.all_apps),
                    count = regularApps.size,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            
            item(span = { GridItemSpan(4) }) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun SetupPrivateAppsCard(
    onSetupClick: () -> Unit
) {
    Card(
        onClick = onSetupClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = ExpressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ExpressiveShapes.medium)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setup_private_apps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.setup_private_apps_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.setup_private_apps),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.count_format, count),
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun CollapsibleSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevron"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .clickable { onExpandChange(!isExpanded) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .wrapContentSize()
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.badge_count_format, count),
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = color,
                modifier = Modifier
                    .size(20.dp)
                    .scale(1f, if (isExpanded) -1f else 1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun AppGridItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    val protectionStates = listOfNotNull(
        if (app.isLocked) Pair(Icons.Filled.Lock, MaterialTheme.colorScheme.primary) else null,
        if (app.isHidden) Pair(Icons.Filled.VisibilityOff, MaterialTheme.colorScheme.tertiary) else null,
        if (app.isSandboxed) Pair(Icons.Filled.Security, MaterialTheme.colorScheme.secondary) else null,
        if (app.isDevOptionsHidden) Pair(Icons.Filled.Code, MaterialTheme.colorScheme.error) else null
    )
    val hasAnyProtection = protectionStates.isNotEmpty()
    
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(64, 64)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
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
                    .clip(ExpressiveShapes.medium)
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
                            tint = Color.White,
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
                            text = stringResource(R.string.badge_count_format, protectionStates.size),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 4.dp, y = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy((-2).dp)
                    ) {
                        protectionStates.take(3).forEachIndexed { index, (_, color) ->
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
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AppQuickActionsSheet(
    app: AppInfo,
    onLockToggle: (AppInfo) -> Unit,
    onHideToggle: (AppInfo) -> Unit,
    onSandboxToggle: (AppInfo) -> Unit,
    onDevOptionsToggle: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(80, 80)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = app.label,
                modifier = Modifier
                    .size(56.dp)
                    .clip(ExpressiveShapes.medium)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            FilledTonalIconButton(
                onClick = { onLaunch(app) }
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = stringResource(R.string.launch)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        QuickActionRow(
            icon = Icons.Outlined.Lock,
            activeIcon = Icons.Filled.Lock,
            title = stringResource(R.string.lock),
            isEnabled = app.isLocked,
            color = MaterialTheme.colorScheme.primary,
            onToggle = { onLockToggle(app) }
        )
        
        QuickActionRow(
            icon = Icons.Outlined.VisibilityOff,
            activeIcon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide),
            isEnabled = app.isHidden,
            color = MaterialTheme.colorScheme.tertiary,
            onToggle = { onHideToggle(app) }
        )
        
        QuickActionRow(
            icon = Icons.Outlined.Security,
            activeIcon = Icons.Filled.Security,
            title = stringResource(R.string.sandbox),
            isEnabled = app.isSandboxed,
            color = MaterialTheme.colorScheme.secondary,
            onToggle = { onSandboxToggle(app) }
        )
        
        QuickActionRow(
            icon = Icons.Outlined.Code,
            activeIcon = Icons.Filled.Code,
            title = stringResource(R.string.dev_opt),
            isEnabled = app.isDevOptionsHidden,
            color = MaterialTheme.colorScheme.error,
            onToggle = { onDevOptionsToggle(app) }
        )
    }
}

@Composable
fun QuickActionRow(
    icon: ImageVector,
    activeIcon: ImageVector,
    title: String,
    isEnabled: Boolean,
    color: Color,
    onToggle: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) color.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bg"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .background(backgroundColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isEnabled) activeIcon else icon,
            contentDescription = null,
            tint = if (isEnabled) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (isEnabled) color else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = color,
                checkedTrackColor = color.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    val hasAnyProtection = app.isLocked || app.isHidden || app.isSandboxed || app.isDevOptionsHidden
    val cardColor by animateColorAsState(
        targetValue = if (hasAnyProtection) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardColor"
    )
    
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(48, 48)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ExpressiveShapes.medium,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = app.label,
                modifier = Modifier
                    .size(44.dp)
                    .clip(ExpressiveShapes.small)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (hasAnyProtection) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (app.isLocked) {
                            TextChip(
                                icon = Icons.Filled.Lock,
                                text = stringResource(R.string.lock),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (app.isHidden) {
                            TextChip(
                                icon = Icons.Filled.VisibilityOff,
                                text = stringResource(R.string.hide),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (app.isSandboxed) {
                            TextChip(
                                icon = Icons.Filled.Security,
                                text = stringResource(R.string.sandbox),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (app.isDevOptionsHidden) {
                            TextChip(
                                icon = Icons.Filled.Code,
                                text = stringResource(R.string.dev_opt),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextChip(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}


data class HiddenNotification(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val icon: Drawable? = null,
    val contentIntent: android.app.PendingIntent? = null
)

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
                        .clip(ExpressiveShapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surface),
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
                    text = stringResource(R.string.notifications_locked),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.unlock_private_area_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onUnlockRequest,
                    shape = ExpressiveShapes.large
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.unlock))
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
                        contentIntent = info.contentIntent
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
                            contentIntent = info.contentIntent
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
                        .clip(ExpressiveShapes.extraLarge)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        ),
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
                    text = stringResource(R.string.no_private_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.notifications_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(notifications, key = { it.key }) { notification ->
                ExpressiveNotificationItem(
                    notification = notification,
                    onLaunch = {
                        try {
                            notification.contentIntent?.send()
                                ?: launchApp(context, notification.packageName)
                        } catch (e: Exception) {
                            launchApp(context, notification.packageName)
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
fun ExpressiveNotificationItem(
    notification: HiddenNotification,
    onLaunch: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = ExpressiveShapes.medium,
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
                            .clip(ExpressiveShapes.small)
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
                            .clip(ExpressiveShapes.small)
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
                            fontWeight = FontWeight.Bold,
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
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onLaunch,
                    shape = ExpressiveShapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.open))
                }
            }
        }
    }
}

private suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? android.app.AxSandboxManager
    
    if (sandboxManager == null) {
        Log.e(TAG, "AxSandboxManager not available")
        return@withContext emptyList()
    }
    
    val lockablePackages = sandboxManager.getLockablePackages() ?: emptyList()
    
    val hiddenPackages = sandboxManager.getHiddenPackages() ?: emptyList()
    
    val allPackages = (lockablePackages + hiddenPackages).toSet()
    
    Log.d(TAG, "Got ${lockablePackages.size} lockable + ${hiddenPackages.size} hidden = ${allPackages.size} total packages")
    
    allPackages
        .mapNotNull { packageName ->
            try {
                pm.getApplicationInfo(packageName, 
                    PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Package not found: $packageName")
                null
            }
        }
        .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        .map { appInfo ->
            val isLocked = try {
                sandboxManager.isAppLocked(appInfo.packageName) ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if ${appInfo.packageName} is locked", e)
                false
            }
            
            val isHidden = try {
                sandboxManager.isPackageHidden(appInfo.packageName) ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if ${appInfo.packageName} is hidden", e)
                false
            }
            
            val isSandboxed = try {
                sandboxManager.isPackageSandboxed(appInfo.packageName) ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if ${appInfo.packageName} is sandboxed", e)
                false
            }
            
            val isDevOptionsHidden = try {
                sandboxManager.isDevOptionsHidden(appInfo.packageName) ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if ${appInfo.packageName} dev options hidden", e)
                false
            }
            
            AppInfo(
                packageName = appInfo.packageName,
                label = pm.getApplicationLabel(appInfo).toString(),
                icon = pm.getApplicationIcon(appInfo),
                uid = appInfo.uid,
                isLocked = isLocked,
                isHidden = isHidden,
                isSandboxed = isSandboxed,
                isDevOptionsHidden = isDevOptionsHidden
            )
        }
}

private suspend fun toggleAppLock(context: Context, packageName: String, lock: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? android.app.AxSandboxManager
        if (sandboxManager == null) {
            Log.e(TAG, "AxSandboxManager is null!")
            return@withContext
        }
        if (lock) {
            sandboxManager.addLockedApp(packageName)
        } else {
            sandboxManager.removeLockedApp(packageName)
        }
        Log.d(TAG, "toggleAppLock completed for $packageName, lock=$lock")
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling app lock for $packageName", e)
    }
}

private suspend fun toggleAppHidden(context: Context, packageName: String, hidden: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? android.app.AxSandboxManager
        if (sandboxManager == null) {
            Log.e(TAG, "AxSandboxManager is null!")
            return@withContext
        }
        sandboxManager.setPackageHidden(packageName, hidden)
        Log.d(TAG, "toggleAppHidden completed for $packageName, hidden=$hidden")
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling app hidden for $packageName", e)
    }
}

private suspend fun toggleAppSandboxed(context: Context, packageName: String, sandboxed: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? android.app.AxSandboxManager
        if (sandboxManager == null) {
            Log.e(TAG, "AxSandboxManager is null!")
            return@withContext
        }
        if (sandboxed) {
            sandboxManager.addSandboxedPackage(packageName)
        } else {
            sandboxManager.removeSandboxedPackage(packageName)
        }
        Log.d(TAG, "toggleAppSandboxed completed for $packageName, sandboxed=$sandboxed")
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling app sandboxed for $packageName", e)
    }
}

private suspend fun toggleDevOptionsHidden(context: Context, packageName: String, hidden: Boolean) = withContext(Dispatchers.IO) {
    try {
        val sandboxManager = context.getSystemService(Context.AX_SANDBOX_SERVICE) as? android.app.AxSandboxManager
        if (sandboxManager == null) {
            Log.e(TAG, "AxSandboxManager is null!")
            return@withContext
        }
        if (hidden) {
            sandboxManager.addDevOptionsHiddenPackage(packageName)
        } else {
            sandboxManager.removeDevOptionsHiddenPackage(packageName)
        }
        Log.d(TAG, "toggleDevOptionsHidden completed for $packageName, hidden=$hidden")
    } catch (e: Exception) {
        Log.e(TAG, "Error toggling dev options hidden for $packageName", e)
    }
}

private fun launchApp(context: Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Log.w(TAG, "No launch intent for $packageName")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error launching $packageName", e)
    }
}
