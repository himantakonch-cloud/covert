# Keep the methods the web page calls (window.GeoTagApp.*)
-keepclassmembers class com.assamconverter.geocam.AppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
