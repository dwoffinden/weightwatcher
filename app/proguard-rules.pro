# Keep Protobuf Lite fields for reflection, but allow classes and methods to be minified/obfuscated to save size.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep the zero-arg constructor for Glance App Widget ActionCallbacks, which are instantiated via reflection.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
