# CameraX and AndroidX ship their own rules. Keep our model classes readable in crash logs.
-keep class com.assamconverter.geocam.** { *; }
-dontwarn org.slf4j.**
