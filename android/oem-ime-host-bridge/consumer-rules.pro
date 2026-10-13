-keep class com.paopaoime.aikeyboard.ime.** { *; }
-keep class com.paopaoime.aikeyboard.materialhub.** { *; }
-keep class com.paopaoime.aikeyboard.midscene.** { *; }

# Keep the fcitx/native integration chain stable for downstream host-side R8.
-keep class org.fcitx.fcitx5.android.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
