# Keep public Core SDK APIs stable after host-side shrinking.
-keep class com.paopaoime.aikeyboard.sdk.core.** { *; }
# DrawerKit（R9 抽屉开放层）：宿主实现的工厂/监听回调按原名保留
-keep class com.paopaoime.aikeyboard.sdk.core.drawer.** { *; }
