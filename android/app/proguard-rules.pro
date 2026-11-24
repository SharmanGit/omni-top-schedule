# Room Database
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep class ru.topacademy.omni.data.local.dao.** { *; }
-keep class ru.topacademy.omni.data.local.entity.** { *; }
-keep class ru.topacademy.omni.data.local.OmniDatabase_Impl { *; }
-keep class ru.topacademy.omni.data.db.** { *; }

# Domain & Network Models (Gson / Reflection / Data classes)
-keep class ru.topacademy.omni.domain.model.** { *; }
-keep class ru.topacademy.omni.data.network.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Glance Widgets
-keep class ru.topacademy.omni.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }
