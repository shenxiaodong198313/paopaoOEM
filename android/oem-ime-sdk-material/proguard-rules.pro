-dontobfuscate

-keep class * implements com.paopaoime.aikeyboard.sdk.core.OemMaterialHubAddon { *; }
-keep class com.paopaoime.aikeyboard.addon.material.** { *; }
-keep class com.paopaoime.aikeyboard.materialhub.MaterialHubActivity { *; }
-keep class com.paopaoime.aikeyboard.materialhub.MaterialHubLaunchAuthorizer { *; }
-keep class com.paopaoime.aikeyboard.materialhub.OemRemoteMaterialAutoSync { *; }
-keep class com.paopaoime.aikeyboard.materialhub.OemRemoteMaterialService { *; }
-keep class com.paopaoime.aikeyboard.materialhub.OemRemoteMaterialSyncScheduler { *; }
-keep class com.paopaoime.aikeyboard.materialhub.OemRemoteMaterialSyncScheduler$* { *; }
-keep class com.paopaoime.aikeyboard.materialhub.OemRemoteMaterialCatalogWorker { *; }
-keep class com.paopaoime.aikeyboard.materialhub.OemRemoteMaterialCatalogWorker$* { *; }

-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.**
-dontwarn org.w3.**
-dontwarn org.etscoop.**
-dontwarn schemaorg_apache_xmlbeans.**
-dontwarn javax.xml.**

-dontwarn com.equationl.paddleocr4android.**
-dontwarn okhttp3.**
-dontwarn okio.**
