# Give independently minified implementation classes a unique namespace.
-repackageclasses 'com.paopaoime.aikeyboard.addon.voice.internal'
# Optional bridge provider and sherpa JNI entry points must retain their names.
-keep class com.paopaoime.aikeyboard.ime.voice.** { *; }
-keep class com.paopaoime.aikeyboard.ime.ui.fragment.VoiceToTextSettingsFragment { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class org.fcitx.fcitx5.android.input.voice.** { *; }
