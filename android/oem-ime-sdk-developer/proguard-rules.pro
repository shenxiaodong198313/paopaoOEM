-dontobfuscate

-keep class * implements com.paopaoime.aikeyboard.sdk.core.OemDeveloperAddon { *; }
-keep class com.paopaoime.aikeyboard.addon.developer.** { *; }
-keep class com.paopaoime.aikeyboard.oem.debug.** { *; }
-keep class com.paopaoime.aikeyboard.ime.ui.fragment.DeveloperSettingsFragment { *; }
-keep class com.paopaoime.aikeyboard.ime.ui.fragment.DeveloperAiModelConfigFragment { *; }
-keep class com.paopaoime.aikeyboard.ime.ui.fragment.DeveloperAiReplyConfigFragment { *; }
-keep class com.paopaoime.aikeyboard.ime.ui.fragment.DeveloperSessionArchiveFragment { *; }

-dontwarn com.equationl.paddleocr4android.**
-dontwarn okhttp3.**
-dontwarn okio.**
