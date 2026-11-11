# Play Console: what to paste where

Package: `io.github.jaswantk917.chitthighanti`
Privacy policy: https://jaswantk917.github.io/chitthi-ghanti/privacy.html

## Main store listing

**App name** (30 max)

```
Tring: Calls & SMS
```

**Short description** (80 max)

```
SMS that sorts itself, calls that keep strangers quiet. All on your phone.
```

**Full description**

```
Two everyday apps in one install: Chitthi for SMS and Ghanti for calls, each with its own icon on your home screen. Use either, or both.

CHITTHI: MESSAGES THAT SORT THEMSELVES
• OTPs, banks and deliveries up top. People in their own place. Promos out of the way.
• The newest code is waiting at the top, and can be copied the moment it arrives.
• Send later: hold send to schedule a message for tonight, tomorrow morning or any time.
• Messages from people show as proper conversations, with photo, reply and bubble.
• Smart replies and sorting help, on the phone itself, on phones that support it.

GHANTI: CALLS THAT KNOW WHO'S CALLING
• Numbers that aren't in your contacts ring quietly. They still show, so nothing is missed.
• Waiting for an order or a ride? While a food, grocery, ride or courier app says it's on the way, strangers ring normally, and Ghanti tells you who it probably is.
• Someone who calls twice in three minutes rings through. They probably mean it.
• Confirm who called and Ghanti remembers it for that call. Write notes during a call.
• Everything a phone app needs: keypad with search, hold, mute, speaker, merge, quick replies and favourites.

BLOCKING FOR BOTH
• Block a number and Android itself stops its calls and SMS.
• Rules for the rest: numbers starting with 140 (telemarketing), promotional senders, words like "loan approved", or your own regex.
• Saved contacts are never caught. Stopped messages wait for you instead of disappearing.

PRIVATE BY DESIGN
No account. No ads. No analytics. Your messages, calls and contacts never leave your phone.

Chitthi needs to be your default SMS app, and Ghanti your default phone app. Switch back any time in Settings.
```

**Category:** Communication. **Tags:** Messaging, Phone & calls (pick the closest offered).

## Data safety

- Does your app collect or share any of the required user data types? **No**
- (Everything else is skipped once you answer No.)

## Sensitive permissions: SMS and Call Log

Appears after the first bundle upload, under App content → Sensitive app permissions.

- **Core functionality:**
  - Default SMS handler.
  - Default Phone handler.
- **Description:**

```
Tring is an SMS app and a phone app in one install, each with its own launcher icon. The SMS side asks to become the default SMS handler before using any SMS permission, and uses them to show, sort, send, schedule and notify about messages. The phone side asks to become the default Phone handler before using call log or phone permissions, and uses them to show recent calls, place and answer calls through its own in-call screen, and screen calls. While the app is the default SMS handler, the phone side also reads recent SMS on the device to notice "out for delivery" messages, so a delivery rider's call can ring. If the user switches either role to another app, that side stops reading SMS or the call log and asks to become the default again. Nothing leaves the device.
```

- **Video:** link to the unlisted YouTube video (script below).

## Foreground service: phone call

- Type: **Phone call** (`FOREGROUND_SERVICE_PHONE_CALL`).
- Description:

```
The in-call service runs in the foreground during a call to show the ongoing-call notification with hang up, and to keep the call controls working while the screen is off or another app is open. It starts when a call begins and stops when the call ends.
```

- What happens if it's deferred or stopped: `Calls in progress would lose their notification and controls.`
- Video: the phone video below shows it.

## Full-screen intent

- Core functionality: **Calling: receiving calls**. The incoming-call screen shows over the lock screen.

## Content rating answers

- Category: Communication.
- Shares location: No. Digital purchases: No.
- Block users or content: **Yes**. Report users or content: No. Chat moderation: No.

## Video scripts (screen record on the phone, upload as unlisted YouTube)

**SMS video (about 60 s)**
1. Open the blue Chitthi icon and tap Let's go. The "Set as default SMS app" dialog appears; choose it.
2. Allow contacts and notifications.
3. Show the inbox tabs and open a conversation.
4. Send a message, then receive one (text yourself from another phone).
5. Hold send and schedule a message.
6. Long-press a chat and tap Block.

**Phone video (about 60 s)**
1. Open the green Ghanti icon and tap Let's go. The "Set as default phone app" dialog appears; choose it. Allow the Caller ID & spam app dialog.
2. Show Recents (the call log) and place a call from the keypad. Show the in-call screen and the ongoing-call notification, then hang up.
3. Receive a call from a number not in your contacts: it rings silently and the screen says "Ringing silently".
4. Optional: with a delivery notification showing, a stranger's call rings and shows the delivery label.
5. Open Settings → Blocked and add a "140" rule.

## Release notes

1.14:

```
The Tring screen now matches Chitthi and Ghanti all the way: the same blurred header and edges, and your theme colours.
```

1.13:

```
The Tring screen now looks like Chitthi and Ghanti: the name at the top, the two sides below.
```

1.12:

```
Opening Tring from the Play Store now shows both sides every time. Don't use one? Hide its icon there. Hiding both offers to uninstall instead.
```

1.11:

```
In Ghanti, tap the delivery line under the title to see what's on its way. Swipe one away once it's here, and strangers go back to ringing silently.
```

1.10:

```
Strangers ring out loud only while a delivery is really on its way: Ghanti now goes by the arrival time in the app's notification and stops once the order has arrived. A stranger who calls twice still gets through.
```

1.9:

```
The blur at the bottom of your lists now fades in smoothly, with no line where it starts.
```

1.8:

```
Ghanti vibrates when a call connects and when the other side hangs up, so you can feel it with the phone at your ear (switch it off in Ghanti settings). After a conference drops to one person, they show by name again.
```

1.7:

```
While a call connects, big soft shapes drift behind the call screen, then gather into the caller's face when they pick up.
```

1.6:

```
Merged calls show as one conference, with everyone's faces and names, and you can drop someone or talk to them privately. The call screen moves with the call: shapes drift while it connects and gather when it picks up, the face comes forward on speaker, and the background goes still when you mute or hold. Settings sheets slide smoothly.
```

1.4:

```
Ghanti's keypad understands phone codes: *#06# shows your IMEI, and *#*#…#*#* opens hidden test menus like *#*#4636#*#*. Chitthi's settings only show Gemini Nano on phones that have it.
```

1.2:

```
Smoother call screen: the caller's face no longer jumps when you open the keypad or answer.
```

1.1:

```
Now called Tring. Opening it from Play shows what's inside; Chitthi and Ghanti keep their own icons. Ghanti tells you when Android has turned off its lock-screen call screen, keeps a gap above the call buttons, gives mute, speaker and hold a crisp on/off tap, and no longer brings the keyboard back after a contacts search.
```

1.0:

```
First release: Chitthi for SMS and Ghanti for calls, in one app, with blocking for both.
```
