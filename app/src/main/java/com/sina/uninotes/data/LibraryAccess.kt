package com.sina.uninotes.data

import kotlinx.coroutines.sync.Mutex

/** Serializes library writes and backup snapshots, including their file operations. */
object LibraryAccess {
    val mutex = Mutex()
}
