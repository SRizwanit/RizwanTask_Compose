-keepattributes SourceFile,LineNumberTable

# WorkManager (Push): Room creates its database (WorkDatabase_Impl) by reflection through the no-argument constructor.
# R8 removed that constructor in the release build -> crash at app start (InitializationProvider).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }

# WorkManager creates workers by class name + (Context, WorkerParameters) constructor.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
