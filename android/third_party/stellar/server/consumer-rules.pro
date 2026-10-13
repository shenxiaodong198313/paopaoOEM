-keep class roro.stellar.server.** { *; }
-keep class roro.stellar.shared.** { *; }
-keep class com.stellar.api.** { *; }

# Stellar server runs inside app_process and uses Android hidden framework APIs.
# OEM host apps should not need to define these suppressions themselves.
-dontwarn android.app.ActivityThread
-dontwarn android.app.ContentProviderHolder
-dontwarn android.app.ContextImpl
-dontwarn android.app.IApplicationThread
-dontwarn android.app.IProcessObserver$Stub
-dontwarn android.app.IProcessObserver
-dontwarn android.app.IUidObserver$Stub
-dontwarn android.app.IUidObserver
-dontwarn android.app.ProfilerInfo
-dontwarn android.content.IContentProvider
-dontwarn android.content.IIntentReceiver
-dontwarn android.content.pm.ParceledListSlice
-dontwarn android.ddm.DdmHandleAppName
-dontwarn android.os.SELinux
