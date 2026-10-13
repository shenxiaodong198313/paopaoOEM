-keep class * implements com.paopaoime.aikeyboard.sdk.core.OemAiReplyAddon { *; }
-keep class com.paopaoime.aikeyboard.addon.aireply.** { *; }

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**
