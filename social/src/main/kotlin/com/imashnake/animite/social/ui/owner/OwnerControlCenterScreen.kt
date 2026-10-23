package com.imashnake.animite.social.ui.owner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.imashnake.animite.social.data.OwnerDashboardStats
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuditAction
import com.imashnake.animite.social.model.AuditLog
import com.imashnake.animite.social.model.Permission
import com.imashnake.animite.social.model.SystemSettings
import com.imashnake.animite.social.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerControlCenterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OwnerControlCenterViewModel = hiltViewModel(),
) {
    val ownerAuthState by viewModel.ownerAuthState.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            when (msg) {
                is OwnerUiMessage.Success -> snackbarHostState.showSnackbar(msg.message)
                is OwnerUiMessage.Error -> snackbarHostState.showSnackbar(msg.message)
            }
            viewModel.clearUiMessage()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "مركز تحكم المالك",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Text(
                                text = "Animeta RBAC & System Management",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (val state = ownerAuthState) {
                    is OwnerAuthState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFFD700))
                                Text(
                                    text = "جاري التحقق من صلاحيات المالك...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is OwnerAuthState.Unauthorized -> {
                        UnauthorizedAccessView(onBack = onBack)
                    }

                    is OwnerAuthState.Authorized -> {
                        AuthorizedOwnerContent(
                            owner = state.ownerUser,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnauthorizedAccessView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = "غير مصرح لك بالوصول",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "هذا القسم مخصص حصرياً لمالك تطبيق أنيميتا (OWNER).\nتم قفل لوحة التحكم تلقائياً لحماية أمان النظام والبيانات.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("العودة", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorizedOwnerContent(
    owner: AnimiteUser,
    viewModel: OwnerControlCenterViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "نظرة عامة" to Icons.Default.Dashboard,
        "المستخدمين" to Icons.Default.People,
        "الصلاحيات" to Icons.Default.Security,
        "إعدادات النظام" to Icons.Default.Settings,
        "سجل العمليات" to Icons.Default.History
    )

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Color(0xFFFFD700),
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 3.dp,
                        color = Color(0xFFFFD700)
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> OverviewTab(viewModel)
                1 -> UsersManagementTab(owner, viewModel)
                2 -> RolesMatrixTab()
                3 -> SystemSettingsTab(viewModel)
                4 -> AuditLogsTab(viewModel)
            }
        }
    }
}

@Composable
private fun OverviewTab(
    viewModel: OwnerControlCenterViewModel,
    modifier: Modifier = Modifier,
) {
    val stats by viewModel.stats.collectAsState()
    val settings by viewModel.systemSettings.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            // System status banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.maintenanceMode) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    } else {
                        Color(0xFF1B3828)
                    }
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (settings.maintenanceMode) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (settings.maintenanceMode) "وضع الصيانة نشط حالياً" else "النظام يعمل بشكل طبيعي",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (settings.maintenanceMode) MaterialTheme.colorScheme.error else Color(0xFF81C784)
                        )
                        Text(
                            text = if (settings.maintenanceMode) "التطبيق مغلق أمام المستخدمين العاديين" else "جميع الخدمات والسيرفرات في وضع التشغيل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (settings.maintenanceMode) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        modifier = Modifier.size(12.dp)
                    ) {}
                }
            }
        }

        item {
            Text(
                text = "إحصائيات المجتمع والنظام",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "إجمالي المستخدمين",
                        count = stats.totalUsers,
                        icon = Icons.Default.People,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "الملاك",
                        count = stats.ownersCount,
                        icon = Icons.Default.Security,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "المشرفين",
                        count = stats.moderatorsCount,
                        icon = Icons.Default.Star,
                        tint = Color(0xFFAB47BC),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "أعضاء النخبة",
                        count = stats.premiumCount,
                        icon = Icons.Default.Star,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "الحسابات الموثقة",
                        count = stats.verifiedCount,
                        icon = Icons.Default.CheckCircle,
                        tint = Color(0xFF00B0FF),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "الحسابات المحظورة",
                        count = stats.suspendedCount,
                        icon = Icons.Default.Warning,
                        tint = Color(0xFFF44336),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "التعليقات المنشورة",
                        count = stats.totalComments,
                        icon = Icons.Default.Dashboard,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "البلاغات المفتوحة",
                        count = stats.totalReports,
                        icon = Icons.Default.Warning,
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    count: Long,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tint.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    text = "$count",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsersManagementTab(
    actorOwner: AnimiteUser,
    viewModel: OwnerControlCenterViewModel,
    modifier: Modifier = Modifier,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchedUsers by viewModel.searchedUsers.collectAsState()
    val recentUsers by viewModel.recentUsers.collectAsState()

    val displayUsers = if (searchQuery.isNotBlank()) searchedUsers else recentUsers

    var userForRoleDialog by remember { mutableStateOf<AnimiteUser?>(null) }
    var userForPremiumDialog by remember { mutableStateOf<AnimiteUser?>(null) }
    var userForVerificationDialog by remember { mutableStateOf<AnimiteUser?>(null) }
    var userForSuspensionDialog by remember { mutableStateOf<AnimiteUser?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            placeholder = { Text("بحث باسم المستخدم أو الاسم أو المعرّف (UID)...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "مسح")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFD700),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = if (searchQuery.isNotBlank()) "نتائج البحث (${displayUsers.size})" else "أحدث المستخدمين المسجلين",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (displayUsers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة للبحث" else "جاري تحميل قائمة المستخدمين...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(displayUsers, key = { it.uid }) { targetUser ->
                    UserManagementCard(
                        user = targetUser,
                        isSelf = targetUser.uid == actorOwner.uid,
                        onChangeRoleClick = { userForRoleDialog = targetUser },
                        onToggleVerificationClick = { userForVerificationDialog = targetUser },
                        onManagePremiumClick = { userForPremiumDialog = targetUser },
                        onToggleSuspensionClick = { userForSuspensionDialog = targetUser }
                    )
                }
            }
        }
    }

    // Role Dialog
    userForRoleDialog?.let { targetUser ->
        ChangeRoleDialog(
            actorOwner = actorOwner,
            targetUser = targetUser,
            onDismiss = { userForRoleDialog = null },
            onConfirm = { newRole, customBadge, reason ->
                viewModel.updateUserRole(targetUser, newRole, customBadge, reason)
                userForRoleDialog = null
            }
        )
    }

    // Premium Dialog
    userForPremiumDialog?.let { targetUser ->
        ManagePremiumDialog(
            targetUser = targetUser,
            onDismiss = { userForPremiumDialog = null },
            onConfirm = { durationDays, reason ->
                viewModel.updatePremium(targetUser, durationDays, reason)
                userForPremiumDialog = null
            }
        )
    }

    // Verification Dialog
    userForVerificationDialog?.let { targetUser ->
        ToggleVerificationDialog(
            targetUser = targetUser,
            onDismiss = { userForVerificationDialog = null },
            onConfirm = { isVerified, reason ->
                viewModel.toggleVerification(targetUser, isVerified, reason)
                userForVerificationDialog = null
            }
        )
    }

    // Suspension Dialog
    userForSuspensionDialog?.let { targetUser ->
        ToggleSuspensionDialog(
            targetUser = targetUser,
            onDismiss = { userForSuspensionDialog = null },
            onConfirm = { isSuspended, reason ->
                viewModel.toggleUserSuspension(targetUser, isSuspended, reason)
                userForSuspensionDialog = null
            }
        )
    }
}

@Composable
private fun UserManagementCard(
    user: AnimiteUser,
    isSelf: Boolean,
    onChangeRoleClick: () -> Unit,
    onToggleVerificationClick: () -> Unit,
    onManagePremiumClick: () -> Unit,
    onToggleSuspensionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.isSuspended) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (user.isSuspended) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (!user.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.displayName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.isVerified) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "موثق",
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (isSelf) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFD700).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "أنت",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "@${user.username} • UID: ${user.uid.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Role & Status Badge
                Column(horizontalAlignment = Alignment.End) {
                    RoleBadge(role = user.userRole, customTitle = user.customBadgeTitle)
                    if (user.isSuspended) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "محظور",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeRoleClick,
                    enabled = !isSelf,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("الرتبة", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onToggleVerificationClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (user.isVerified) "إلغاء التوثيق" else "توثيق", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onManagePremiumClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("النخبة", fontSize = 12.sp)
                }

                Button(
                    onClick = onToggleSuspensionClick,
                    enabled = !isSelf && !user.isOwner,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isSuspended) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (user.isSuspended) "فك الحظر" else "حظر", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(
    role: UserRole,
    customTitle: String?,
    modifier: Modifier = Modifier,
) {
    val (badgeBg, badgeFg, label) = when (role) {
        UserRole.OWNER -> Triple(Color(0xFFFFD700).copy(alpha = 0.2f), Color(0xFFFFD700), customTitle ?: "مالك")
        UserRole.MODERATOR -> Triple(Color(0xFFAB47BC).copy(alpha = 0.2f), Color(0xFFAB47BC), customTitle ?: "مشرف")
        UserRole.PREMIUM -> Triple(Color(0xFFFF9800).copy(alpha = 0.2f), Color(0xFFFF9800), customTitle ?: "نخبة")
        UserRole.VERIFIED -> Triple(Color(0xFF00B0FF).copy(alpha = 0.2f), Color(0xFF00B0FF), customTitle ?: "موثق")
        UserRole.MEMBER -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, customTitle ?: "عضو")
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeBg,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = badgeFg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ChangeRoleDialog(
    actorOwner: AnimiteUser,
    targetUser: AnimiteUser,
    onDismiss: () -> Unit,
    onConfirm: (newRole: UserRole, customBadge: String?, reason: String) -> Unit,
) {
    var selectedRole by remember { mutableStateOf(targetUser.userRole) }
    var customBadgeTitle by remember { mutableStateOf(targetUser.customBadgeTitle ?: "") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل رتبة المستخدم", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "المستخدم: ${targetUser.displayName} (@${targetUser.username})",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text("اختر الرتبة الجديدة:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)

                UserRole.entries.forEach { role ->
                    val isSelected = selectedRole == role
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRole = role }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = role.titleAr,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = when (role) {
                                        UserRole.OWNER -> "صلاحيات مطلقة للنظام وإدارة الملاك والمشرفين"
                                        UserRole.MODERATOR -> "صلاحيات إدارة المحتوى والبلاغات وتوثيق الحسابات"
                                        UserRole.PREMIUM -> "مميزات النخبة، جودة فائقة وشارة مميزة"
                                        UserRole.VERIFIED -> "شارة التوثيق الرسمية وأولوية التعليق"
                                        UserRole.MEMBER -> "عضوية قياسية للتصفح والتفاعل"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customBadgeTitle,
                    onValueChange = { customBadgeTitle = it },
                    label = { Text("لقب الشارة المخصص (اختياري)") },
                    placeholder = { Text("مثال: قائد الدعم الفني / المطور") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب التغيير (للتسجيل بسجل العمليات)") },
                    placeholder = { Text("مثال: ترقية للإشراف بناءً على الأداء") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedRole,
                        customBadgeTitle.trim().ifEmpty { null },
                        reason.trim()
                    )
                }
            ) {
                Text("تأكيد التعديل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun ManagePremiumDialog(
    targetUser: AnimiteUser,
    onDismiss: () -> Unit,
    onConfirm: (durationDays: Int?, reason: String) -> Unit,
) {
    var selectedOption by remember { mutableStateOf(30) } // 30, 90, 365, -1 (lifetime), 0 (revoke)
    var reason by remember { mutableStateOf("") }

    val options = listOf(
        30 to "شهر واحد (30 يوماً)",
        90 to "3 أشهر (90 يوماً)",
        365 to "سنة كاملة (365 يوماً)",
        -1 to "عضوية نخبة مدى الحياة",
        0 to "إلغاء عضوية النخبة فوراً"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدارة عضوية النخبة (Premium)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("المستخدم: ${targetUser.displayName} (@${targetUser.username})")

                options.forEach { (days, label) ->
                    val isSelected = selectedOption == days
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = days }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (days == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("السبب (للتسجيل الإداري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val durationDays = when (selectedOption) {
                        0 -> 0 // revoke
                        -1 -> null // lifetime
                        else -> selectedOption
                    }
                    onConfirm(durationDays, reason.trim())
                }
            ) {
                Text("تطبيق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ToggleVerificationDialog(
    targetUser: AnimiteUser,
    onDismiss: () -> Unit,
    onConfirm: (isVerified: Boolean, reason: String) -> Unit,
) {
    val newVerificationStatus = !targetUser.isVerified
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (newVerificationStatus) "منح شارة التوثيق" else "إلغاء شارة التوثيق",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (newVerificationStatus) {
                        "هل أنت متأكد من منح شارة التوثيق الزرقاء للمستخدم ${targetUser.displayName}؟ ستظهر الشارة بجانب اسمه في كافة أنحاء التطبيق."
                    } else {
                        "هل أنت متأكد من سحب شارة التوثيق من المستخدم ${targetUser.displayName}؟"
                    }
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("السبب") },
                    placeholder = { Text("مثال: صانع محتوى موثوق / مخالفة شروط التوثيق") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(newVerificationStatus, reason.trim()) }) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ToggleSuspensionDialog(
    targetUser: AnimiteUser,
    onDismiss: () -> Unit,
    onConfirm: (isSuspended: Boolean, reason: String) -> Unit,
) {
    val newSuspensionStatus = !targetUser.isSuspended
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (newSuspensionStatus) "حظر حساب المستخدم" else "إلغاء حظر الحساب",
                fontWeight = FontWeight.Bold,
                color = if (newSuspensionStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (newSuspensionStatus) {
                        "تحذير: سيتم حظر المستخدم ${targetUser.displayName} فوراً ومنعه من نشر التعليقات أو المراسلة أو التفاعل."
                    } else {
                        "سيتم فك الحظر عن حساب ${targetUser.displayName} واستعادة صلاحيات التفاعل الطبيعية."
                    }
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب الإجراء الإداري") },
                    placeholder = { Text("مثال: سب وقذف متكرر / انتهاء مدة العقوبة") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newSuspensionStatus, reason.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (newSuspensionStatus) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )
            ) {
                Text(if (newSuspensionStatus) "تأكيد الحظر" else "تأكيد فك الحظر")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun RolesMatrixTab(modifier: Modifier = Modifier) {
    val roles = UserRole.entries.sortedByDescending { it.rank }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "مصفوفة الأدوار والصلاحيات (RBAC Invariants)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "يتبع نظام التحكم الصارم بمبدأ الامتياز الأدنى (Least Privilege) والأدوار المنفصلة بين الإدارة وعضوية النخبة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(roles) { role ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoleBadge(role = role, customTitle = null)
                            Text(
                                text = "المستوى ${role.rank}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = role.roleNameEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = when (role) {
                            UserRole.OWNER -> "المالك الأعلى للتطبيق: يملك كامل الصلاحيات بما فيها إدارة المشرفين، تعديل إعدادات النظام، الاطلاع على سجل العمليات، وتعيين ملاك جدد. لا يمكن خفض رتبته إلا بوجود مالك آخر."
                            UserRole.MODERATOR -> "مشرف النظام: يمتلك صلاحيات إدارة المحتوى، معالجة البلاغات، حذف التعليقات، وتوثيق الحسابات. لا يمكنه تعديل إعدادات السيرفر أو ترقية مستخدم إلى مالك."
                            UserRole.PREMIUM -> "عضوية النخبة المدفوعة: تمنح شارة النخبة الذهبية، جودة مشاهدة فائقة، دعم للمطورين، وأولوية في التعليقات."
                            UserRole.VERIFIED -> "عضو موثق: حساب رسمي تم التحقق من هويته، يحمل شارة التوثيق الزرقاء وله حماية من انتحال الشخصية."
                            UserRole.MEMBER -> "عضو قياسي: تصفح الأنمي والمحتوى، كتابة التعليقات، التفاعل والمتابعة."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Text("الصلاحيات الممنوحة:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Permission.entries.forEach { permission ->
                            val hasPermission = role.permissions.contains(permission)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = permission.labelAr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasPermission) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Icon(
                                    imageVector = if (hasPermission) Icons.Rounded.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (hasPermission) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsTab(
    viewModel: OwnerControlCenterViewModel,
    modifier: Modifier = Modifier,
) {
    val currentSettings by viewModel.systemSettings.collectAsState()

    var maintenanceMode by remember(currentSettings) { mutableStateOf(currentSettings.maintenanceMode) }
    var allowRegistrations by remember(currentSettings) { mutableStateOf(currentSettings.allowRegistrations) }
    var commentsEnabled by remember(currentSettings) { mutableStateOf(currentSettings.commentsEnabled) }
    var directMessagesEnabled by remember(currentSettings) { mutableStateOf(currentSettings.directMessagesEnabled) }
    var announcementActive by remember(currentSettings) { mutableStateOf(currentSettings.announcementBanner.isNotBlank()) }
    var announcementText by remember(currentSettings) { mutableStateOf(currentSettings.announcementBanner) }
    var saveReason by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "إعدادات النظام العامة (System Toggles)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "يتم حفظ هذه الإعدادات وتطبيقها فورياً على جميع مستخدمي التطبيق مع تسجيل كل تعديل في سجل العمليات.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingToggleRow(
                        title = "وضع الصيانة (Maintenance Mode)",
                        description = "عند تفعيله، سيتم حجب التطبيق عن كافة المستخدمين عدا المالك والمشرفين.",
                        checked = maintenanceMode,
                        onCheckedChange = { maintenanceMode = it },
                        isDanger = true
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingToggleRow(
                        title = "السماح بتسجيل مستخدمين جدد",
                        description = "إتاحة إنشاء حسابات جديدة في التطبيق.",
                        checked = allowRegistrations,
                        onCheckedChange = { allowRegistrations = it }
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingToggleRow(
                        title = "تفعيل نظام التعليقات الاجتماعي",
                        description = "السماح للمستخدمين بكتابة ونشر التعليقات على الحلقات والصفحات.",
                        checked = commentsEnabled,
                        onCheckedChange = { commentsEnabled = it }
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    SettingToggleRow(
                        title = "تفعيل الرسائل المباشرة (Direct Messages)",
                        description = "السماح بالمحادثات الخاصة بين المستخدمين.",
                        checked = directMessagesEnabled,
                        onCheckedChange = { directMessagesEnabled = it }
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingToggleRow(
                        title = "شريط الإعلانات العام (Announcement Banner)",
                        description = "عرض شريط إعلاني مثبت في أعلى التطبيق لجميع المستخدمين.",
                        checked = announcementActive,
                        onCheckedChange = { announcementActive = it }
                    )

                    if (announcementActive) {
                        OutlinedTextField(
                            value = announcementText,
                            onValueChange = { text -> announcementText = text },
                            label = { Text("نص الإعلان أو التنبيه") },
                            placeholder = { Text("مثال: سيتم إطلاق تحديث جديد الليلة...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { showConfirmDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("حفظ التغييرات في النظام", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("تأكيد حفظ إعدادات النظام", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("هل أنت متأكد من حفظ التعديلات وتطبيقها على جميع المستخدمين فورياً؟")
                    OutlinedTextField(
                        value = saveReason,
                        onValueChange = { saveReason = it },
                        label = { Text("سبب التعديل (اختياري)") },
                        placeholder = { Text("مثال: صيانة دورية مجدولة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newSettings = currentSettings.copy(
                            maintenanceMode = maintenanceMode,
                            allowRegistrations = allowRegistrations,
                            commentsEnabled = commentsEnabled,
                            directMessagesEnabled = directMessagesEnabled,
                            announcementBanner = if (announcementActive) announcementText.trim() else "",
                            updatedAt = System.currentTimeMillis()
                        )
                        viewModel.updateSystemSettings(newSettings, saveReason.trim())
                        showConfirmDialog = false
                    }
                ) {
                    Text("تأكيد وحفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDanger && checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (isDanger) MaterialTheme.colorScheme.error else Color(0xFFFFD700),
                checkedTrackColor = if (isDanger) MaterialTheme.colorScheme.errorContainer else Color(0xFFFFD700).copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun AuditLogsTab(
    viewModel: OwnerControlCenterViewModel,
    modifier: Modifier = Modifier,
) {
    val auditLogs by viewModel.auditLogs.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "سجل العمليات الإدارية المشفر (${auditLogs.size})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "سجل غير قابل للحذف أو التعديل (Append-Only) يوثق كافة التعديلات وصاحب الإجراء.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (auditLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد عمليات مسجلة حتى الآن",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(auditLogs, key = { it.id }) { log ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (log.auditAction) {
                                    AuditAction.ROLE_CHANGED -> Color(0xFFAB47BC).copy(alpha = 0.2f)
                                    AuditAction.VERIFICATION_GRANTED, AuditAction.VERIFICATION_REVOKED -> Color(0xFF00B0FF).copy(alpha = 0.2f)
                                    AuditAction.PREMIUM_GRANTED, AuditAction.PREMIUM_REVOKED -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                    AuditAction.ACCOUNT_SUSPENDED, AuditAction.ACCOUNT_RESTORED -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    AuditAction.SYSTEM_SETTINGS_UPDATED -> Color(0xFFFFD700).copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = log.auditAction.labelAr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = when (log.auditAction) {
                                        AuditAction.ROLE_CHANGED -> Color(0xFFAB47BC)
                                        AuditAction.VERIFICATION_GRANTED, AuditAction.VERIFICATION_REVOKED -> Color(0xFF00B0FF)
                                        AuditAction.PREMIUM_GRANTED, AuditAction.PREMIUM_REVOKED -> Color(0xFFFF9800)
                                        AuditAction.ACCOUNT_SUSPENDED, AuditAction.ACCOUNT_RESTORED -> MaterialTheme.colorScheme.error
                                        AuditAction.SYSTEM_SETTINGS_UPDATED -> Color(0xFFFFD700)
                                    }
                                )
                            }

                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "المنفّذ: ${log.actorName.ifBlank { log.actorUid.take(8) }}  •  الهدف: ${log.targetName.ifBlank { log.targetUid.take(8) }.ifBlank { "النظام العام" }}",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (log.details.isNotBlank()) {
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!log.previousValue.isNullOrBlank() || !log.newValue.isNullOrBlank()) {
                            Text(
                                text = "السابق: ${log.previousValue.orEmpty()}  ⬅  الجديد: ${log.newValue.orEmpty()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
