-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontwarn java.awt.Color
-dontwarn java.awt.Dimension
-dontwarn java.awt.Graphics2D
-dontwarn java.awt.geom.Rectangle2D

-keep class com.paopaoime.aikeyboard.sdk.core.** { *; }
-keep class com.paopaoime.aikeyboard.sdk.ai.** { *; }

-keep class com.paopaoime.aikeyboard.** extends android.app.Activity { *; }
-keep class com.paopaoime.aikeyboard.** extends android.app.Service { *; }
-keep class com.paopaoime.aikeyboard.** extends android.content.ContentProvider { *; }
-keep class com.paopaoime.aikeyboard.** extends android.app.Application { *; }

# feature AAR 按原始全限定名跨模块引用内核类，host-bridge 自身 R8 不得 shrink/混淆任何内核类
-keep class com.paopaoime.aikeyboard.** { *; }
-keep class org.fcitx.** { *; }
