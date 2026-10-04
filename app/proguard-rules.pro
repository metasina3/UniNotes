# UniNotes keeps minify disabled for the first release build.
# Keep Room entities if minify is enabled later.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
