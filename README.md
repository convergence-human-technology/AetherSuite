![Convergence Cover](https://raw.githubusercontent.com/Convergence-Human-And-Technology/Aether-Suite/main/cover_Aether_Suite_real_HT.jpg)

<p align="center">
<img src="https://raw.githubusercontent.com/convergence-human-technology/site/main/img/Aether-Suite-cover-05t.png" alt="Aether Suite : 9 applications Android open source par Convergence Human and Technology" width="100%" height="auto" />
</p>

<p align="center">
  <img src="https://github.com/convergence-human-technology/AetherSuite/raw/main/cover-aether-suite.png" alt="Aether Suite : 9 open-source Android apps to replace every default app on your phone." width="100%" height="100%">
</p>

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)
![UI: Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)
![Forks](https://img.shields.io/github/forks/Convergence-Human-And-Technology/AetherSuite?style=social)
![Stars](https://img.shields.io/github/stars/Convergence-Human-And-Technology/AetherSuite?style=social)
![Open Source](https://img.shields.io/badge/Open%20Source-Yes-brightgreen.svg)
![Ads: None](https://img.shields.io/badge/Ads-None-red.svg)
![Tracking: None](https://img.shields.io/badge/Tracking-None-red.svg)

---

# AetherSuite

**9 open-source Android apps to replace every default app on your phone.**

No ads. No tracking. No account needed. Your data stays on your phone, always.

---

## What is AetherSuite ?

AetherSuite is a complete set of 9 Android applications. Together, they replace the apps that come pre-installed on your phone : messages, contacts, phone dialer, notes, files, gallery, music, and calendar.

Every app is built with the same visual style, so they all look and feel like one product. They also work together : for example, you can send a photo from the gallery straight into a message, or share a note as an SMS in one tap.

The source code is fully open. Anyone can read it, check it, copy it, or improve it.

---

## Why choose AetherSuite ?

| What you get | What you avoid |
|---|---|
| No ads, ever | Google tracking your habits |
| No account required | Samsung apps sending data to the cloud |
| Photos sent in full quality, no blur | Apps that compress photos until they are unrecognisable |
| Send PDF and any file type by MMS | Default apps that only accept images |
| Voice-to-text in French and English | Typing everything by hand |
| Notes encrypted with AES-256 military-grade encryption | Notes readable by anyone with your phone |
| One consistent design across all 9 apps | A mix of apps with no visual coherence |
| Free, forever | Subscriptions and in-app purchases |
| Runs fully offline | Apps that need internet to work |

The key difference compared to other alternatives : AetherSuite is not one app, it is a complete system. All 9 apps are designed to talk to each other. No other open-source project on Android offers this level of integration across the full set of default apps.

---

## The 9 applications

Each app is independent. You can install only the ones you want. But they are designed to work best together.

| App | What it does | The extra touch |
|---|---|---|
| AetherSMS | SMS and MMS messages, photos in HD, PDF attachments, voice input | Smart photo compression : no blur, ever |
| AetherContacts | Contacts, favourites, search, full editing | Opens AetherPhone or AetherSMS directly from any contact |
| AetherPhone | Dialer, call log, contact identification | Adds an unknown number to AetherContacts in one tap |
| AetherNotes | Notes with AES-256 encryption, colours, grid view | Note content is unreadable without your unlocked phone |
| AetherFiles | File manager, navigation, sorting, sharing | Creates a note in AetherNotes from any file in one tap |
| AetherGallery | Photos and videos, albums, video player | Sends photos to AetherSMS in full HD in one tap |
| AetherMusic | Local music player, MP3 and FLAC, mini player | Plays in the background even when you switch apps |
| AetherCalendar | Local encrypted calendar, reminders | Shares an event by SMS in one gesture |
| aether-core | Shared design system, security layer, inter-app communication | Powers the connection between all 9 apps |

---

## How the apps work together

Here are real examples of what becomes possible when all 9 apps are installed :

- Someone unknown texts you. A button appears in AetherSMS : "Add to contacts." One tap opens AetherContacts with the number already filled in.
- In AetherContacts, tap a contact and press "Call." AetherPhone dials immediately.
- In AetherGallery, hold a photo and press "Send by SMS." AetherSMS opens with the photo attached in full quality.
- In AetherNotes, tap Share. The note is sent as an SMS or MMS in one step.
- In AetherCalendar, share an event. AetherSMS sends the invitation with the date, time, and location.
- In AetherMusic, hold a track and press "Memorise." AetherNotes creates a note with the title, artist, and album.
- In AetherFiles, open the menu on a file and press "Add a note." AetherNotes creates a note with the file name and path.

---

## What you need before installing

You do not need to be a developer. You just need to follow the steps below carefully. If something does not work, read the troubleshooting table at the end.

Here is what you need to have ready :

- An Android phone (Android 8.0 or higher)
- A USB cable that can transfer data (not a charge-only cable)
- A Windows, Mac, or Linux computer with an internet connection
- About 2 GB of free space on your computer
- Around 30 to 45 minutes

---

## How to install AetherSuite, step by step

### Step 1 : Download Android Studio

Android Studio is a free tool made by Google. It is used to send apps to your phone from your computer. You only need it once for the installation.

Go to this address in your browser : https://developer.android.com/studio

Click the big download button. Once the file is downloaded, open it and follow the installer. Leave all options at their default values. This also installs Java and ADB automatically.

When Android Studio opens for the first time, it may download extra files. Wait for it to finish before going to the next step.

---

### Step 2 : Unlock developer mode on your phone

This step tells your phone that it can receive apps from your computer. It looks technical but it takes less than two minutes.

On your phone, go to Settings.

Then find "About phone" or "About device."

Look for a line that says "Build number," "MIUI version," "HyperOS version," or similar, depending on your phone brand.

Tap that line 7 times in a row quickly.

A message appears : "You are now a developer" or something similar. If your phone asks for your PIN or fingerprint, enter it.

Now go back to Settings. You will see a new section called "Developer options" or "Additional settings" then "Developer options."

Open it and turn these two things on :

- USB debugging
- Install via USB

If you have a Xiaomi phone, also turn off "MIUI optimisation" in the same section. This is important.

---

### Step 3 : Connect your phone to your computer

Plug your USB cable between your phone and your computer.

On your phone, a pop-up appears asking : "Allow USB debugging ?"

Tick the box "Always allow from this computer."

Then tap OK.

If no pop-up appears : pull down the notification bar at the top of your screen. Change the USB mode from "Charging" to "File transfer" or "MTP." Then unplug and replug the cable.

---

### Step 4 : Download and unzip AetherSuite

Download the file `AetherSuite_FINAL.zip` from this repository. You will find it in the Releases section on the right side of this page.

Once downloaded, right-click on the file and choose "Extract all" (on Windows) or double-click it (on Mac). Choose a simple location, for example :

On Windows : `C:\AetherSuite\`

On Mac : your Desktop or Documents folder

---

### Step 5 : Open the project in Android Studio

Open Android Studio.

Click "Open" (or go to File, then Open).

Browse to the folder where you extracted AetherSuite.

Select the main folder (called AetherSuite or similar). Do not go inside a sub-folder.

Click OK.

Android Studio will start synchronising. A progress bar appears at the bottom of the screen. Wait for it to disappear completely. This can take 3 to 10 minutes the first time.

---

### Step 6 : Check that your phone is recognised

At the top of Android Studio, you will see a toolbar with a dropdown menu in the middle. It should show the name of your phone.

If you see your phone name there : everything is ready. Go to Step 7.

If you see "No devices" :

- Check that the cable is properly plugged in on both ends.
- Unplug and replug the cable.
- On your phone, accept the USB debugging pop-up again if it appears.
- On Windows, you may need to install drivers for your phone brand. Search for "[your phone brand] USB drivers" and download them from the official website.

---

### Step 7 : Install each app on your phone

In Android Studio, look at the top-left dropdown menu. It shows the name of the current app module.

For each app below, do the following :

1. Click the top-left dropdown and select the app name.
2. Click the green triangle button (or press Shift + F10 on Windows / Control + R on Mac).
3. Wait. Android Studio compiles and installs the app on your phone. This takes 1 to 3 minutes per app.
4. You will see "BUILD SUCCESSFUL" and "Launching activity" in the bar at the bottom. The app opens on your phone.
5. Repeat for the next app.

Here are the 8 modules to install, one by one :

| Module name | App |
|---|---|
| aether-sms | AetherSMS |
| aether-contacts | AetherContacts |
| aether-phone | AetherPhone |
| aether-notes | AetherNotes |
| aether-files | AetherFiles |
| aether-gallery | AetherGallery |
| aether-music | AetherMusic |
| aether-calendar | AetherCalendar |

You do not need to install `aether-core` separately. It is included automatically.

The full installation takes about 15 to 30 minutes depending on your computer.

---

### Step 8 : Set AetherSuite as your default apps

After installing, you need to tell Android to use the new apps instead of the old ones.

On your phone, go to Settings, then Applications, then Default apps.

Set the following :

| Default app type | Choose |
|---|---|
| SMS application | AetherSMS |
| Phone application | AetherPhone |

For the other apps (notes, files, gallery, music, calendar), just open them and use them. They do not need to be set as defaults.

---

### Step 9 : Accept permissions on first launch

When you open each app for the first time, your phone will ask for permission to access contacts, messages, microphone, storage, or other things.

Accept all permissions that appear. These are needed for the apps to work. None of this data leaves your phone.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| "SDK not found" when opening Android Studio | Go to Tools, then SDK Manager. Install Android 14 (API 34). |
| Phone not recognised on Windows | Download USB drivers for your phone brand from the manufacturer website. |
| "INSTALL_FAILED_USER_RESTRICTED" error | Go to Settings, Developer options, Install via USB, and make sure it is on. |
| An app closes immediately after opening | Accept all the permissions it asks for on first launch. |
| "BUILD FAILED" in red | Click on the red text at the bottom of Android Studio. It shows the exact error. You can paste it into a search engine or open an issue here. |
| The pop-up asking for USB debugging never appears | Change USB mode to File transfer in your notification bar, then unplug and replug. |

---

## Installing without Android Studio (advanced users)

If you know how to use a terminal, you can build each APK yourself and install it with ADB :

```bash
./gradlew :aether-sms:assembleDebug
adb install aether-sms/build/outputs/apk/debug/aether-sms-debug.apk
```

Repeat for each module.

---

## Security and updates

AetherSuite is made available to everyone free of charge and in full transparency. The source code is public so that anyone can verify what the apps do and do not do.

However, this project is provided as-is. The creators do not offer ongoing security monitoring, patch management, or automatic updates. This responsibility belongs to you, as the person who installs and uses the apps.

This means :

- You are responsible for checking this repository from time to time for updates.
- You are responsible for keeping the apps up to date on your phone by rebuilding and reinstalling from the latest source.
- You are responsible for assessing whether the apps meet your security requirements before using them.

If you discover a security issue, please read the SECURITY.md file before reporting it.

---

## Creators and contributors

| Name | GitHub |
|---|---|
| Fabien Conéjéro | https://github.com/madjeek-web |
| Charlotte O'Toole | https://github.com/charlotte-otoole |
| Roland Langlois | https://github.com/roland-langlois |
| Convergence - Human And Technology | https://github.com/Convergence-Human-And-Technology |

Convergence - Human And Technology is a software company that designs and markets private and paid desktop PC software applications and IT solutions.

---

## Want to contribute ?

Read the CONTRIBUTING.md file. It explains how to report a bug, suggest a feature, or submit code.

---

## License

AetherSuite is released under the MIT License. See the LICENSE file for the full text.

This means you can use, copy, modify, merge, publish, distribute, and sublicense this software freely, as long as you keep the copyright notice.

#

## Compatible with de-Googled Android

AetherSuite is built on AOSP and depends on zero Google services. It installs manually via USB without any app store. This makes it a natural fit for privacy-focused phones and operating systems.

Tested and compatible environments :

| OS / Device | Notes |
|---|---|
| GrapheneOS | Fully compatible, recommended |
| CalyxOS | Fully compatible |
| /e/OS (Murena) | Fully compatible |
| LineageOS | Fully compatible |
| Punkt MC03 (AphyOS) | Android-based, sideload supported |
| Jolla (Sailfish OS) | Android app layer supported |
| Fairphone + /e/OS | Fully compatible |
| Stock Android (AOSP) | Fully compatible |

If you are running a de-Googled phone and AetherSuite works on your device, feel free to open an issue to add your configuration to this list.
