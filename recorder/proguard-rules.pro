# WorkManager (the daily update check) builds its Room database by reflection; AGP 9's full-mode R8 drops the constructor
-keep class * extends androidx.room.RoomDatabase { <init>(); }
