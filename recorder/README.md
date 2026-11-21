<h1 align="center">Tring Recorder</h1>

<p align="center">Call recording for <a href="https://play.google.com/store/apps/details?id=io.github.jaswantk917.chitthighanti">Tring</a>, as a small add-on you install yourself.</p>

Android lets only the phone's own system app record the call line. Any other app has one way in: an Accessibility service may use the microphone during a call. Google Play doesn't allow call recorders built that way, so Tring Recorder lives here instead of on Play, and does only this one thing.

Once it's installed and switched on, Tring's call screen gets a **Record** button, and Tring's settings get **Record calls by themselves**: every call, strangers only, or the people you pick.

## Set it up

You need Tring as your phone app (its Ghanti side), on Android 10 or newer.

1. **Download** `tring-recorder-x.y.apk` from [Releases](https://github.com/jaswantk917/tring-recorder/releases) on your phone and open it. If Android asks, allow your browser or file manager to install apps.
2. **Open Tring Recorder** and tap **Allow** next to *Microphone*, then next to *A note when a recording is saved*.
3. Tap **Open** next to *Turn on Tring Recorder in Accessibility*, pick **Tring Recorder**, and switch it on.
4. **Is the switch greyed out?** Android 13 and newer block Accessibility for apps installed from a file until you say otherwise:
   1. Go back to Tring Recorder and tap **Open App info**.
   2. Tap **⋮** in the top corner, then **Allow restricted settings**, and confirm with your PIN or fingerprint.
   3. Repeat step 3.
5. Tring Recorder says **All set**. Close Tring from your recent apps once and open it again, so it finds the recorder.

To test it, call someone, tap **Record**, say a few words each, and hang up. A *Call recorded* note appears; tap it to play the recording.

## What to expect

- **Where recordings go:** `Recordings/Tring` on Android 12 and newer, `Music/Tring recordings` before that. Each one is named after the person and the time.
- **How they sound:** the recorder hears the room, not the phone line. You come through clearly. The other person reaches the microphone from the earpiece or speaker, so after the call the recorder tells the two of you apart and brings them up to your loudness. On speaker they sound close to natural. On the earpiece they're quieter to begin with and come out thinner, like a phone voice.
- **Your phone's own call recorder**, if it has one, records the line itself and will always sound better. Use this where there isn't one.

## Privacy

- It has no internet permission. Recordings never leave your phone unless you share them.
- It can't read your screen. Its Accessibility service asks for no window content, only for being allowed the microphone during a call.
- It records only when Tring asks it to: your tap, or the auto-record setting you chose.
- Recording calls is legal in some places and needs everyone's consent in others. Know the rule where you are, and tell people when they're being recorded.

## Turn it off

Switch Tring Recorder off in Accessibility, or uninstall it. Tring's Record button and auto-record setting disappear with it.

## Source

The code lives in [`recorder/`](https://github.com/jaswantk917/chitthi-ghanti/tree/main/recorder) in Tring's repository, beside the app it works with.
