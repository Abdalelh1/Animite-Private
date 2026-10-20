package com.imashnake.animite

import android.content.Intent
import com.animeta.app.BuildConfig
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.RoundedCorner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.DefaultNavTransitions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.imashnake.animite.anime.AnimeScreen
import com.imashnake.animite.core.ui.Density
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.core.ui.rememberDefaultPaddings
import com.imashnake.animite.explore.ExploreScreen
import com.imashnake.animite.explore.searchbar.SearchFrontDrop
import com.imashnake.animite.theme.AnimiteTheme
import com.imashnake.animite.theme.cairoFontFamily
import com.imashnake.animite.theme.manropeFontFamily
import com.imashnake.animite.manga.MangaScreen
import com.imashnake.animite.media.EpisodesPage
import com.imashnake.animite.media.MediaPage
import com.imashnake.animite.media.player.VideoPlayerScreen
import com.imashnake.animite.navigation.AnimeRoute
import com.imashnake.animite.navigation.EpisodesRoute
import com.imashnake.animite.navigation.ExploreRoute
import com.imashnake.animite.navigation.MangaRoute
import com.imashnake.animite.navigation.RawAnimeRoute
import com.imashnake.animite.navigation.VideoPlayerRoute
import com.imashnake.animite.raw.ui.RawAnimeScreen
import com.imashnake.animite.navigation.NavigationBar
import com.imashnake.animite.navigation.NavigationBarPaths
import com.imashnake.animite.navigation.NavigationRail
import com.imashnake.animite.navigation.ProfileRoute
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.imashnake.animite.navigation.SocialRoute
import com.imashnake.animite.navigation.UserProfileRoute
import com.imashnake.animite.navigation.ChatRoute
import com.imashnake.animite.navigation.FollowListRoute
import com.imashnake.animite.profile.ProfileScreen
import com.imashnake.animite.profile.ProfileViewModel
import com.imashnake.animite.settings.SettingsPage
import com.imashnake.animite.settings.SettingsViewModel
import com.imashnake.animite.settings.Theme
import com.imashnake.animite.social.SocialScreen
import com.imashnake.animite.social.ui.chat.ChatScreen
import com.imashnake.animite.social.ui.profile.FollowListScreen
import com.imashnake.animite.social.ui.profile.UserProfileScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import com.imashnake.animite.media.EpisodeWatchManager
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.FirestoreSyncRepository
import com.imashnake.animite.social.model.FirestoreWatchProgress

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var firestoreSyncRepository: FirestoreSyncRepository

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    var showSplashScreen = true

    override fun attachBaseContext(newBase: android.content.Context) {
        val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocales = newBase.getSystemService(android.app.LocaleManager::class.java).applicationLocales
            if (!appLocales.isEmpty) appLocales[0]?.language else null
        } else {
            val prefs = newBase.getSharedPreferences("app_locale", android.content.Context.MODE_PRIVATE)
            prefs.getString("language", null)
        }
        val context = if (!language.isNullOrEmpty()) {
            val locale = java.util.Locale.forLanguageTag(language)
            java.util.Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EpisodeWatchManager.onWatchProgressChanged = { animeId, epNum, prog, posMs, durMs ->
            val uid = authRepository.currentUid
            if (!uid.isNullOrBlank() && animeId > 0) {
                lifecycleScope.launch(Dispatchers.IO) {
                    firestoreSyncRepository.saveWatchProgress(
                        uid = uid,
                        progress = FirestoreWatchProgress(
                            animeId = animeId,
                            lastWatchedEpisode = epNum,
                            lastPositionMs = posMs,
                            durationMs = durMs,
                            episodesProgress = mapOf(epNum.toString() to prog.toDouble()),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition { showSplashScreen }
        enableEdgeToEdge()
        setContent {
            val fontFamilyResolver = LocalFontFamilyResolver.current
            LaunchedEffect(Unit) {
                try {
                    fontFamilyResolver.preload(manropeFontFamily)
                    fontFamilyResolver.preload(cairoFontFamily)
                } catch (e: Exception) {
                    Log.e(TAG, "onCreate: Failed to load font: ${e.cause}: ${e.message}")
                }
                showSplashScreen = false
            }

            val theme by settingsViewModel.theme.collectAsState(initial = Theme.DEVICE_THEME.name)
            val useSystemColorScheme by settingsViewModel.useSystemColorScheme.collectAsState(initial = false)
            val isAmoled by settingsViewModel.isAmoled.collectAsState(initial = false)
            val density by settingsViewModel.density.collectAsState(initial = Density.COMFY.name)
            val showUserDescription by settingsViewModel.showUserDescription.collectAsStateWithLifecycle(initialValue = true)

            val useDarkTheme = when(Theme.valueOf(theme)) {
                Theme.DARK -> true
                Theme.LIGHT -> false
                Theme.DEVICE_THEME -> isSystemInDarkTheme()
            }

            val dayHour by settingsViewModel.dayHour.collectAsState(initial = null)

            val avatar by profileViewModel.viewerAvatar.collectAsState(initial = null)

            AnimiteTheme(
                paddings = rememberDefaultPaddings(Density.valueOf(density)),
                useDarkTheme = useDarkTheme,
                isAmoled = isAmoled,
                useSystemColorScheme = useSystemColorScheme,
                dayHour = dayHour
            ) {
                MainScreen(
                    deviceScreenCornerRadius = getDeviceScreenCornerRadius(),
                    useDarkTheme = useDarkTheme,
                    isAmoled = isAmoled,
                    avatar = avatar,
                    showUserDescription = showUserDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }

    private fun getDeviceScreenCornerRadius(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return windowManager
                .currentWindowMetrics
                .windowInsets
                .getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                ?.radius
                ?: 0
        }
        return 0
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    deviceScreenCornerRadius: Int,
    useDarkTheme: Boolean,
    isAmoled: Boolean,
    avatar: String?,
    showUserDescription: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isNavBarVisible = rememberSaveable(currentBackStackEntry) {
        if (currentBackStackEntry != null) {
            NavigationBarPaths.entries.any {
                it.matchesDestination(currentBackStackEntry!!)
            }
        } else false
    }

    // TODO: Refactor to use Scaffold once AnimatedVisibility issues are fixed;
    //  see https://issuetracker.google.com/issues/258270139.
    Box(modifier) {
        val animeScrollState = rememberScrollState()
        val mangaScrollState = rememberScrollState()
        val exploreScrollState = rememberLazyListState()

        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground
        ) {
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = AnimeRoute,
                    predictivePopEnterTransition = {
                        DefaultNavTransitions.enterTransition.invoke(this)
                    },
                    predictivePopExitTransition = {
                        DefaultNavTransitions.exitTransition.invoke(this)
                    }
                ) {
                    composable<AnimeRoute> {
                        AnimeScreen(
                            onNavigateToMediaItem = navController::navigate,
                            onNavigateToExplore = navController::navigate,
                            scrollState = animeScrollState,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                        )
                    }
                    composable<MangaRoute> {
                        MangaScreen(
                            onNavigateToMediaItem = navController::navigate,
                            onNavigateToExplore = navController::navigate,
                            scrollState = mangaScrollState,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                        )
                    }
                    composable<MediaPage> {
                        MediaPage(
                            onBack = navController::navigateUp,
                            onNavigateToMediaItem = navController::navigate,
                            onNavigateToExplore = navController::navigate,
                            onNavigateToPlayer = navController::navigate,
                            onNavigateToEpisodes = navController::navigate,
                            onNavigateToUserProfile = { userId ->
                                navController.navigate(UserProfileRoute(userId))
                            },
                            useDarkTheme = useDarkTheme,
                            isAmoled = isAmoled,
                            deviceScreenCornerRadius = deviceScreenCornerRadius,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                        )
                    }
                    composable<EpisodesRoute> { backStackEntry ->
                        val episodesRoute = backStackEntry.toRoute<EpisodesRoute>()
                        EpisodesPage(
                            route = episodesRoute,
                            useDarkTheme = useDarkTheme,
                            isAmoled = isAmoled,
                            onBack = navController::navigateUp,
                            onNavigateToPlayer = navController::navigate,
                        )
                    }
                    composable<VideoPlayerRoute> { backStackEntry ->
                        val playerRoute = backStackEntry.toRoute<VideoPlayerRoute>()
                        VideoPlayerScreen(
                            route = playerRoute,
                            onBack = navController::navigateUp,
                        )
                    }
                    composable<RawAnimeRoute> {
                        RawAnimeScreen(
                            onBack = navController::navigateUp,
                            onNavigateToPlayer = { playerRoute ->
                                navController.navigate(playerRoute)
                            }
                        )
                    }
                    composable<ProfileRoute> {
                        ProfileScreen(
                            onNavigateToMediaItem = navController::navigate,
                            onNavigateToSettings = navController::navigate,
                            showUserDescription = showUserDescription,
                            deviceScreenCornerRadius = deviceScreenCornerRadius,
                            useDarkTheme = useDarkTheme,
                            isAmoled = isAmoled,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                        )
                    }
                    composable<SettingsPage> {
                        SettingsPage(versionName = BuildConfig.VERSION_NAME)
                    }
                    composable<SocialRoute> {
                        SocialScreen(
                            onNavigateToUserProfile = { userId ->
                                navController.navigate(UserProfileRoute(userId))
                            },
                            onNavigateToChat = { convId, recipientId, name, avatar ->
                                navController.navigate(ChatRoute(convId, recipientId, name, avatar))
                            }
                        )
                    }
                    composable<UserProfileRoute> {
                        UserProfileScreen(
                            onBack = navController::navigateUp,
                            onNavigateToFollowers = { userId ->
                                navController.navigate(FollowListRoute(userId, 0))
                            },
                            onNavigateToFollowing = { userId ->
                                navController.navigate(FollowListRoute(userId, 1))
                            },
                            onNavigateToChat = { recipientId, name, avatar ->
                                val currentUid = com.google.firebase.Firebase.auth.currentUser?.uid.orEmpty()
                                val convId = if (currentUid < recipientId) "${currentUid}_${recipientId}" else "${recipientId}_${currentUid}"
                                navController.navigate(ChatRoute(convId, recipientId, name, avatar))
                            }
                        )
                    }
                    composable<ChatRoute> {
                        ChatScreen(
                            onBack = navController::navigateUp
                        )
                    }
                    composable<FollowListRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<FollowListRoute>()
                        FollowListScreen(
                            userId = route.userId,
                            initialTab = route.initialTab,
                            onBack = navController::navigateUp,
                            onNavigateToUserProfile = { userId ->
                                navController.navigate(UserProfileRoute(userId))
                            }
                        )
                    }
                    composable<ExploreRoute> {
                        ExploreScreen(
                            listState = exploreScrollState,
                            deviceScreenCornerRadius = deviceScreenCornerRadius,
                            onItemClick = { id, mediaType ->
                                navController.navigate(
                                    MediaPage(
                                        id = id,
                                        source = "Search",
                                        mediaType = mediaType.rawValue,
                                    )
                                )
                            },
                            onNavigateToRawAnime = {
                                navController.navigate(RawAnimeRoute)
                            }
                        )
                    }
                }
            }
        }

        when(LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                AnimatedVisibility(
                    visible = isNavBarVisible,
                    modifier = Modifier.align(Alignment.CenterStart),
                    enter = slideInHorizontally { -it },
                    exit = slideOutHorizontally { -it }
                ) { NavigationRail(navController, avatar) }
            }
            else -> {
                val scope = rememberCoroutineScope()
                AnimatedVisibility(
                    visible = isNavBarVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    NavigationBar(
                        navController = navController,
                        avatar = avatar,
                        scrollToTop = {
                            scope.launch {
                                when (it) {
                                    NavigationBarPaths.Explore -> { exploreScrollState.animateScrollToItem(0) }
                                    // TODO: Fix ClassCastException.
//                                    NavigationBarPaths.Anime -> { animeScrollState.animateScrollTo(0) }
//                                    NavigationBarPaths.Manga -> { mangaScrollState.animateScrollTo(0) }
                                    else -> {}
                                }
                            }
                        }
                    )
                }
            }
        }

        val isVideoPlayer = currentBackStackEntry?.destination?.hasRoute<VideoPlayerRoute>() == true
        val isEpisodesPage = currentBackStackEntry?.destination?.hasRoute<EpisodesRoute>() == true
        if (!isVideoPlayer && !isEpisodesPage) {
            SearchFrontDrop(
                hasExtraPadding = isNavBarVisible &&
                        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
                onItemClick = { id, mediaType ->
                    navController.navigate(
                        MediaPage(
                            id = id,
                            source = "Quick Search",
                            mediaType = mediaType.rawValue,
                        )
                    )
                },
                isFabVisible = currentBackStackEntry?.destination?.hasRoute<SettingsPage>() == false
                        && currentBackStackEntry?.destination?.hasRoute<ExploreRoute>() == false
                        && !isVideoPlayer && !isEpisodesPage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        start = LocalPaddings.current.large,
                        end = LocalPaddings.current.large,
                        bottom = LocalPaddings.current.large,
                    )
            )
        }
    }
}
