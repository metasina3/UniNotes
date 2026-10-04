package com.sina.uninotes.util

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
