-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontobfuscate

-keep class com.paopaoime.aikeyboard.sdk.core.** { *; }
# DrawerKit（R9 抽屉开放层）公开 API：宿主按原名注册/回调，显式 keep 便于加固审计
-keep class com.paopaoime.aikeyboard.sdk.core.drawer.** { *; }
# midscene 桥接共享模型：被 host-bridge/automation 跨模块按原名引用，core 自身 R8 不得 shrink
-keep class com.paopaoime.aikeyboard.midscene.bridge.model.** { *; }
