-dontobfuscate

-keep class * implements com.paopaoime.aikeyboard.sdk.core.OemAutomationAddon { *; }
-keep class com.paopaoime.aikeyboard.addon.automation.** { *; }

-keep class com.paopaoime.aikeyboard.automation.** { *; }
-keep class com.paopaoime.aikeyboard.midscene.** { *; }

-dontwarn com.equationl.paddleocr4android.**
-dontwarn okhttp3.**
-dontwarn okio.**

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.paopaoime.aikeyboard.**$$serializer { *; }
-keepclassmembers class com.paopaoime.aikeyboard.** { *** Companion; }
-keepclasseswithmembers class com.paopaoime.aikeyboard.** { kotlinx.serialization.KSerializer serializer(...); }
