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
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType }),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val vm: SubjectViewModel = viewModel(
                factory = SubjectViewModel.factory(
                    subjectId,
                    container.subjectRepository,
                    container.noteRepository,
                    container.photoRepository,
                ),
            )
            SubjectScreen(
                viewModel = vm,
                photoRepository = container.photoRepository,
                onBack = { navController.popBackStack() },
                onOpenCamera = { navController.navigate(Routes.camera(subjectId)) },
                onWriteNote = { navController.navigate(Routes.noteEditor(subjectId)) },
                onOpenNote = { noteId -> navController.navigate(Routes.noteEditor(subjectId, noteId)) },
                onOpenPhoto = { photoId -> navController.navigate(Routes.photoViewer(subjectId, photoId)) },
            )
        }

        composable(
            route = "note/{subjectId}?noteId={noteId}",
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("noteId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val noteId = entry.arguments?.getString("noteId")
            val vm: NoteEditorViewModel = viewModel(
                factory = NoteEditorViewModel.factory(
                    subjectId = subjectId,
                    noteId = noteId,
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
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType }),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val vm: CameraViewModel = viewModel(
                factory = CameraViewModel.factory(
                    subjectId,
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
                    navController.navigate(Routes.photoViewer(subjectId, photoId))
                },
            )
        }

        composable(
            route = Routes.PhotoViewer,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("photoId") { type = NavType.StringType },
            ),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId").orEmpty()
            val photoId = entry.arguments?.getString("photoId").orEmpty()
            PhotoViewerScreen(
                subjectId = subjectId,
                initialPhotoId = photoId,
                photoRepository = container.photoRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
