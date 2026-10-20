# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard/proguard-android.txt

# NanoHTTPD — keep all classes so the server works
-keep class fi.iki.elonen.** { *; }

# Keep our own service and server classes
-keep class com.helgi.remote.** { *; }
