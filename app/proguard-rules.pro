# Xposed module
-keep class com.aicr.gateway.hook.MainHook {
    *;
}
-keep class com.aicr.gateway.hook.AICRHook {
    *;
}
-keep class de.robv.android.xposed.** {
    *;
}
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    *;
}

# NanoHTTPD
-keep class fi.iki.elonen.** {
    *;
}

# Keep all handler classes
-keep class com.aicr.gateway.handler.** {
    *;
}
-keep class com.aicr.gateway.server.** {
    *;
}
-keep class com.aicr.gateway.hook.ServiceProxy {
    *;
}
-keep class com.aicr.gateway.hook.ServiceProxy$* {
    *;
}

# AIDL interfaces
-keep class com.xiaomi.aiasr.** {
    *;
}
-keep class com.xiaomi.nlp.** {
    *;
}
-keep class com.xiaomi.aicr.** {
    *;
}

# Dont warn about missing classes
-dontwarn de.robv.android.xposed.**
-dontwarn org.json.**
