# Room persists these enum names with valueOf/name.
-keep enum com.keenin.calbudget.data.db.** { *; }

# Room database and generated implementation.
-keep class com.keenin.calbudget.data.db.AppDatabase { *; }
-keep class com.keenin.calbudget.data.db.AppDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Navigation Compose and Kotlin metadata used by the compiler plugins.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-dontwarn androidx.compose.**

# DataStore may reference optional protobuf/native helpers.
-dontwarn com.google.protobuf.**
-dontwarn androidx.datastore.core.**
