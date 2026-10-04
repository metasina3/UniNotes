package com.sina.uninotes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sina.uninotes.di.AppContainer
import com.sina.uninotes.ui.backup.BackupScreen
import com.sina.uninotes.ui.camera.CameraScreen
import com.sina.uninotes.ui.camera.CameraViewModel
import com.sina.uninotes.ui.home.HomeScreen
import com.sina.uninotes.ui.home.HomeViewModel
import com.sina.uninotes.ui.notes.NoteEditorScreen
import com.sina.uninotes.ui.notes.NoteEditorViewModel
import com.sina.uninotes.ui.subject.SubjectScreen
import com.sina.uninotes.ui.subject.SubjectViewModel
import com.sina.uninotes.ui.viewer.PhotoViewerScreen

@Composable
fun UniNotesNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.subjectRepository))
            HomeScreen(
                viewModel = vm,
                onOpenSubject = { navController.navigate(Routes.subject(it)) },
                onOpenBackup = { navController.navigate(Routes.Backup) },
            )
        }

        composable(Routes.Backup) {
            BackupScreen(
                backupRepository = container.backupRepository,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.Subject,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("folderId") {
                    type = NavType.StringType
                    defaultValue = "_root"
                },
            ),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val folderId = Routes.decodeFolderId(entry.arguments?.getString("folderId"))
            val vm: SubjectViewModel = viewModel(
                key = "subject-$subjectId-$folderId",
                factory = SubjectViewModel.factory(
                    subjectId,
                    folderId,
                    container.subjectRepository,
                    container.folderRepository,
                    container.noteRepository,
                    container.photoRepository,
                ),
            )
            SubjectScreen(
                viewModel = vm,
                photoRepository = container.photoRepository,
                subjectRepository = container.subjectRepository,
                onBack = { navController.popBackStack() },
                onOpenFolder = { id -> navController.navigate(Routes.subject(subjectId, id)) },
                onOpenCamera = { navController.navigate(Routes.camera(subjectId, folderId)) },
                onWriteNote = { navController.navigate(Routes.noteEditor(subjectId, folderId = folderId)) },
                onOpenNote = { noteId ->
                    navController.navigate(Routes.noteEditor(subjectId, noteId, folderId))
                },
                onOpenPhoto = { photoId ->
                    navController.navigate(Routes.photoViewer(subjectId, photoId, folderId))
                },
            )
        }

        composable(
            route = Routes.NoteEditor,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("noteId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("folderId") {
                    type = NavType.StringType
                    defaultValue = "_root"
                },
            ),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val noteId = entry.arguments?.getString("noteId")
            val folderId = Routes.decodeFolderId(entry.arguments?.getString("folderId"))
            val vm: NoteEditorViewModel = viewModel(
                factory = NoteEditorViewModel.factory(
                    subjectId = subjectId,
                    noteId = noteId,
                    folderId = folderId,
                    noteRepository = container.noteRepository,
                    subjectRepository = container.subjectRepository,
                    persistenceScope = container.applicationScope,
                ),
            )
            NoteEditorScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.Camera,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("folderId") {
                    type = NavType.StringType
                    defaultValue = "_root"
                },
            ),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val folderId = Routes.decodeFolderId(entry.arguments?.getString("folderId"))
            val vm: CameraViewModel = viewModel(
                factory = CameraViewModel.factory(
                    subjectId,
                    folderId,
                    container.photoRepository,
                    container.subjectRepository,
                    container.applicationScope,
                ),
            )
            CameraScreen(
                viewModel = vm,
                photoRepository = container.photoRepository,
                onBack = { navController.popBackStack() },
                onOpenPhoto = { photoId ->
                    navController.navigate(Routes.photoViewer(subjectId, photoId, folderId))
                },
            )
        }

        composable(
            route = Routes.PhotoViewer,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("photoId") { type = NavType.StringType },
                navArgument("folderId") {
                    type = NavType.StringType
                    defaultValue = "_root"
                },
            ),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val photoId = entry.arguments?.getString("photoId").orEmpty()
            val folderId = Routes.decodeFolderId(entry.arguments?.getString("folderId"))
            PhotoViewerScreen(
                subjectId = subjectId,
                folderId = folderId,
                initialPhotoId = photoId,
                photoRepository = container.photoRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
