-keep class * implements com.paopaoime.aikeyboard.sdk.core.OemAutomationAddon { *; }
-keep class com.paopaoime.aikeyboard.addon.automation.** { *; }

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.paopaoime.aikeyboard.**$$serializer { *; }
-keepclassmembers class com.paopaoime.aikeyboard.** { *** Companion; }
-keepclasseswithmembers class com.paopaoime.aikeyboard.** { kotlinx.serialization.KSerializer serializer(...); }
