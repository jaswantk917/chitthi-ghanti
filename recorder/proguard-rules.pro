# ML Kit GenAI (Gemini Nano summaries) wires its components and telemetry up by reflection (same rules as Tring and Hey re)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_** { *; }
-keep class com.google.android.datatransport.** { *; }

# WorkManager (the daily update check) builds its Room database by reflection; AGP 9's full-mode R8 drops the constructor
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ONNX Runtime (clearer transcripts): its native code finds these Java classes by name
-keep class ai.onnxruntime.** { *; }

# Shizuku starts the shell service by its class name, in a process of its own
-keep class io.github.jaswantk917.tring.recorder.ShellService { *; }
-keep class io.github.jaswantk917.tring.recorder.IScreenShell** { *; }
