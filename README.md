# Chitthi & Ghanti

One Android app with two sides, each with its own icon on your home screen:

- **Chitthi** is an SMS app that sorts your messages. OTPs and banks go up top, people get their own place, and promos stay out of the way.
- **Ghanti** is a phone app that keeps strangers quiet, except the delivery rider you're waiting for, and tells you who it probably is: *"Probably Bistro delivery"*.

Use either side or both. Each asks for its own role (default SMS app, default phone app) and works without the other. Everything happens on the phone: no accounts, no ads, no analytics, no servers.

Built with Kotlin and Jetpack Compose (Material 3 Expressive).

## Chitthi: messages

- **Four tabs.** Important (OTPs, banks, deliveries), Personal, Promos and Unknown. Move a chat to another tab and Chitthi remembers.
- **Codes first.** A fresh OTP sits at the top and can be copied to the clipboard as it arrives. Old codes from businesses can clear themselves out.
- **Send later.** Hold send to schedule: tonight, tomorrow morning, or a time you pick. It goes out on time, even after a restart.
- **Proper notifications.** People show as Android conversations, with their photo, a reply box and a place in the conversation shelf. Promos can arrive silently.
- **Smart replies** from Gemini Nano on supported phones, all on the device.

## Ghanti: calls

- **Silences strangers, lets deliveries through.** Numbers not in your contacts ring without a sound but still show. While an order or ride is on its way (a notification from a food, grocery, ride or courier app, or an "out for delivery" SMS), strangers ring normally.
- **Says who's calling** on the call screen, the notification, Recents and the missed-call alert.
- **Second call rings.** Someone calling again within 3 minutes usually means it.
- **Remembers who it was.** Confirm the guess during or after a call, and that call keeps the label. A later call from the same number shows "Ownly delivery last time", never a certainty, because delivery apps reuse masked numbers.
- **Call notes** you can write during a call. They save as you type.
- **A full phone app.** Keypad with T9 search, hold, mute, speaker, merge and swap, SIM choice, delivery-aware quick replies, favourites that remember which number to call.

## Blocking, for both

- **Numbers** go on Android's own block list, so the system stops their calls and SMS before any app sees them.
- **Rules** catch what a list can't:
  - Numbers starting with `140`, India's telemarketing series, or a country code like `+92`.
  - Sender IDs such as `*-P` (every promotional sender).
  - Messages containing certain words.
  - Regular expressions.
- **Saved contacts are never caught by a rule.** Stopped SMS wait on the Blocked screen instead of disappearing.

## Small things worth knowing

| Do this | And |
|---|---|
| Hold a tab chip in Chitthi | Mark everything in that tab read |
| Hold a chat | Pin, move, mark read, block, delete |
| Hold the send button | Send later |
| Tap a face | Open the contact, or save a stranger's number |
| Hold a favourite | All their numbers, or ask which one every time |
| Hold a number on the call screen | Copy it |
| Tap / hold the speaker button | Speaker or phone / Bluetooth, headphones, watch |
| Drag the scroll thumb in Contacts | Fly through the alphabet |

## Permissions

| Permission | Why | When |
|---|---|---|
| SMS, MMS | It's an SMS app | Only while it's your default SMS app |
| Call log, phone | It's a phone app | Only while it's your default phone app |
| Contacts | Names, photos, telling saved people from strangers | |
| Notification access (optional) | Spotting deliveries on their way | Only if you turn it on |
| Notifications, full screen | Messages, incoming calls over the lock screen | |
| Exact alarms (optional) | Scheduled messages to the minute | When you allow it |
| Internet | MMS through your carrier, and business logos by their website | |

Full details: [privacy policy](https://jaswantk917.github.io/chitthi-ghanti/privacy.html).

## Build

```sh
./gradlew :app:assembleDebug        # debug build, installs next to a release one
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:bundleRelease        # Play bundle
```

Release builds sign with the keystore named by the `CG_UPLOAD_KEYSTORE`, `CG_UPLOAD_KEYSTORE_PASSWORD`, `CG_UPLOAD_KEY_ALIAS` and `CG_UPLOAD_KEY_PASSWORD` Gradle properties (for example in `~/.gradle/gradle.properties`). Without them the release is unsigned.

The two apps started separately: [chitthi](https://github.com/jaswantk917/chitthi) and [ghanti](https://github.com/jaswantk917/ghanti).
