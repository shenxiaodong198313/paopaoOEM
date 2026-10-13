-dontobfuscate

-keep class * implements com.paopaoime.aikeyboard.sdk.core.OemAiReplyAddon { *; }
-keep class com.paopaoime.aikeyboard.addon.aireply.** { *; }

-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateLaunchGate { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateLaunchGate$* { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateOcrCacheStore { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateOcrCacheStore$* { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateOcrSnapshot { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateOcrSnapshot$* { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateReplyService { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateReplyService$* { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateScreenCaptureManager { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateScreenCaptureManager$* { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateScreenCapturePermissionStore { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiCommunicateScreenCapturePermissionStore$* { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiScreenCaptureForegroundService { *; }
-keep class com.paopaoime.aikeyboard.materialhub.AiScreenCaptureForegroundService$* { *; }

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

-dontwarn com.equationl.paddleocr4android.**
-dontwarn okhttp3.**
-dontwarn okio.**
