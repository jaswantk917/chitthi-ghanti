# ML Kit GenAI (Gemini Nano summaries) wires its components and telemetry up by reflection (same rules as Tring and Hey re)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_** { *; }
-keep class com.google.android.datatransport.** { *; }

# WorkManager (the daily update check) builds its Room database by reflection; AGP 9's full-mode R8 drops the constructor
-keep class * extends androidx.room.RoomDatabase { <init>(); }
