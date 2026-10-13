-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class org.fcitx.fcitx5.android.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Optional voice addon contract is shared across independently shrunk AARs.
-keep class org.fcitx.fcitx5.android.input.voice.** { *; }
