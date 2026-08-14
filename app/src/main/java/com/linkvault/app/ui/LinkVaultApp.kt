package com.linkvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkvault.app.LinkVaultApplication
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.ui.archived.ArchivedScreen
import com.linkvault.app.ui.archived.ArchivedViewModel
import com.linkvault.app.ui.folder.FolderDetailScreen
import com.linkvault.app.ui.folder.FolderDetailViewModel
import com.linkvault.app.ui.home.HomeScreen
import com.linkvault.app.ui.home.HomeViewModel
import com.linkvault.app.ui.tags.TagsScreen
import com.linkvault.app.ui.tags.TagsViewModel

private enum class Screen { HOME, FOLDER, ARCHIVED, TAGS }

/**
 * Root composable. Plain Compose state instead of a navigation library —
 * see README. [Screen] plus a couple of saveable id/name fields covers
 * every destination (Home, one folder's detail, Archived, Tags) with the
 * same "one active screen, explicit back" model used since Phase 2, just
 * with two more destinations added in Phase 4.5.
 */
@Composable
fun LinkVaultApp(modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as LinkVaultApplication

    var folderIdStack by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var lastFolderName by rememberSaveable { mutableStateOf("") }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }

    val goHome = {
        folderIdStack = emptyList()
        currentScreen = Screen.HOME
    }

    val popFolder = {
        if (folderIdStack.isNotEmpty()) {
            folderIdStack = folderIdStack.dropLast(1)
        }
        if (folderIdStack.isEmpty()) {
            currentScreen = Screen.HOME
        }
    }

    val activeScreen = if (folderIdStack.isNotEmpty()) Screen.FOLDER else currentScreen

    AnimatedContent(
        targetState = activeScreen,
        transitionSpec = {
            if (targetState != Screen.HOME && initialState == Screen.HOME) {
                (slideInHorizontally(initialOffsetX = { it }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { -it }) + fadeOut())
            } else {
                (slideInHorizontally(initialOffsetX = { -it }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it }) + fadeOut())
            }
        },
        modifier = modifier.fillMaxSize(),
        label = "screen-transition"
    ) { screen ->
        when (screen) {
            Screen.HOME -> {
                val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(application))
                HomeScreen(
                    viewModel = homeViewModel,
                    onFolderClick = { folder ->
                        folderIdStack = listOf(folder.id)
                        lastFolderName = folder.name
                    },
                    onViewArchived = { currentScreen = Screen.ARCHIVED },
                    onViewTags = { currentScreen = Screen.TAGS }
                )
            }
            Screen.FOLDER -> {
                val folderId = folderIdStack.lastOrNull()
                if (folderId != null) {
                    val folderDetailViewModel: FolderDetailViewModel = viewModel(
                        key = "folder-detail-$folderId",
                        factory = FolderDetailViewModel.factory(application, folderId)
                    )
                    FolderDetailScreen(
                        viewModel = folderDetailViewModel,
                        folderNameFallback = lastFolderName,
                        onBack = popFolder,
                        onFolderClick = { subFolder ->
                            folderIdStack = folderIdStack + subFolder.id
                            lastFolderName = subFolder.name
                        },
                        onBreadcrumbClick = { breadcrumbFolder ->
                            if (breadcrumbFolder == null) {
                                goHome()
                            } else {
                                val idx = folderIdStack.indexOf(breadcrumbFolder.id)
                                if (idx != -1) {
                                    folderIdStack = folderIdStack.take(idx + 1)
                                    lastFolderName = breadcrumbFolder.name
                                }
                            }
                        }
                    )
                    BackHandler(onBack = popFolder)
                }
            }
            Screen.ARCHIVED -> {
                val archivedViewModel: ArchivedViewModel = viewModel(factory = ArchivedViewModel.factory(application))
                val homeViewModelForPickers: HomeViewModel = viewModel(factory = HomeViewModel.factory(application))
                val foldersForPicker by homeViewModelForPickers.foldersForPicker.collectAsStateWithLifecycle()
                val allTags by homeViewModelForPickers.allTags.collectAsStateWithLifecycle()
                ArchivedScreen(
                    viewModel = archivedViewModel,
                    foldersForPicker = foldersForPicker,
                    allTags = allTags,
                    onBack = goHome
                )
                BackHandler(onBack = goHome)
            }
            Screen.TAGS -> {
                val tagsViewModel: TagsViewModel = viewModel(factory = TagsViewModel.factory(application))
                val homeViewModelForPickers: HomeViewModel = viewModel(factory = HomeViewModel.factory(application))
                val foldersForPicker by homeViewModelForPickers.foldersForPicker.collectAsStateWithLifecycle()
                TagsScreen(
                    viewModel = tagsViewModel,
                    foldersForPicker = foldersForPicker,
                    onBack = goHome
                )
                BackHandler(onBack = goHome)
            }
        }
    }
}
