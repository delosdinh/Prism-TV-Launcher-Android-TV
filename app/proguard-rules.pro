# Accessibility service is referenced from the manifest only.
-keep class com.prismtv.launcher.system.PrismAccessibilityService { *; }

# Shizuku: newProcess is private in API 13 and called by reflection to run `am force-stop`.
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
