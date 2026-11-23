# Production ProGuard & R8 Configuration for StorePoint POS

# 1. Line numbers & debugging attributes
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Room Database Rules
-keep class androidx.room.Room { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-dontwarn androidx.room.paging.**

# 3. Data Models & Entities (prevent stripping of SQLite reflection/columns)
-keep class com.munzo.storepoint.data.** { *; }
-keepclassmembers class com.munzo.storepoint.data.** { *; }

# 5. ML Kit Barcode Scanning & CameraX
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-dontwarn com.google.mlkit.**

# 6. Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 7. Device Admin & App Receivers
-keep class com.munzo.storepoint.StorePointApplication { *; }
-keep class com.munzo.storepoint.StorePointDeviceAdminReceiver { *; }
-keep class com.munzo.storepoint.BootReceiver { *; }
-keep class com.munzo.storepoint.MainActivity { *; }
-keep class com.munzo.storepoint.util.** { *; }
