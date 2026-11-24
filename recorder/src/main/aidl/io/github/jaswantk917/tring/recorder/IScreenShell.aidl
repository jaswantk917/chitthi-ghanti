package io.github.jaswantk917.tring.recorder;

import android.os.ParcelFileDescriptor;

/** What only the adb shell may do, run through Shizuku. */
interface IScreenShell {
    void destroy() = 16777114;
    boolean allowBeside(String packageName) = 1;
    boolean enter(IBinder adapter, String callId, IBinder app) = 2;
    void settled() = 3;
    ParcelFileDescriptor listen(in ParcelFileDescriptor greeting) = 4;
    void speak(in ParcelFileDescriptor pcm) = 5;
    void stopAudio() = 6;
    boolean audioReady() = 7;
}
