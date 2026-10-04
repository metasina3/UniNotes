package com.sina.uninotes.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

private val localDay = flow {
    while (true) {
        emit(LocalDate.now())
        delay(30_000)
    }
}

@Composable
fun currentLocalDay(): LocalDate = localDay.collectAsStateWithLifecycle(initialValue = LocalDate.now()).value
