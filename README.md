I keep this here for myself. It was written with AI for something I wanted, not as a product I maintain for other people. You are welcome to use it or copy it.

# Cal Budget


A calm, local-only Android app that shows three numbers: what is due until the next payday, what the next paycheck needs to cover, and what is due in the period after that.


The home screen stacks those amounts, each with the dates it covers (for example, Sep 19 – Oct 2). Tap any one to see the bills, housing, and card remainders that add up to it. Tap a row for a short sheet to mark that bill or housing payment paid, undo that, or pay a card. **Paid** on a card applies the full remainder; type an amount and use Record payment for a partial. Edit details in the sheet still opens the full form. The menus open those forms directly. Bills, credit cards, housing, pay, and settings live in the menu at the top right.


## Open and run


1. Install [Android Studio](https://developer.android.com/studio) with Android SDK 35.
2. Choose **File → Open** and select this repository.
3. Let Gradle sync. If Android Studio offers to install a missing SDK platform or build-tools package, accept it.
4. Pick an emulator or a device running Android 8.0 (API 26) or newer, then press **Run**.


From a terminal, with `ANDROID_HOME` pointing at your SDK:


```bash
./gradlew assembleDebug
```


The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It includes `arm64-v8a` and `x86_64`, so it runs on a current phone and on an x86_64 emulator. It is signed with that machine’s debug key. That signature is not the stable update key, so this APK will not update an install signed with a different key.


A smaller release APK is arm64-only. Without signing environment variables it is unsigned and cannot be installed:


```bash
./gradlew assembleRelease
```


Output: `app/build/outputs/apk/release/app-release-unsigned.apk`. For an x86_64 emulator build of that same release variant:


```bash
./gradlew assembleRelease -Pabi=x86_64
```


To sign that release with the existing key, set `CALBUDGET_KEYSTORE_PATH`, `CALBUDGET_KEYSTORE_PASSWORD`, `CALBUDGET_KEY_ALIAS`, and `CALBUDGET_KEY_PASSWORD` in the environment and run `assembleRelease` again. The build refuses to fall back to a debug key. Do not commit the keystore or those passwords.


The same signed file is what you install the first time and what you install over an existing copy. GitHub Actions builds it and attaches it to a Release on the default branch when those four values are stored as Actions secrets (`CALBUDGET_KEYSTORE_BASE64` for the keystore file, plus the three password and alias secrets). The Release is public because this repository is public. If any secret is missing, that workflow fails and does not upload an APK.


## Using it


- **Pay schedule.** Set when you get paid: weekly, biweekly, monthly, or every N days, weeks, or months. There is no paycheck amount. Pick a recent or upcoming payday as the anchor date. You can set an end date or leave it ongoing. The home screen uses the next three paydays as the ends of its three windows. If you add more than one schedule, the soonest date wins.
- **Bills and mortgage / housing.** Same recurrence options, plus a name, amount, and optional notes. Mark the earliest unpaid date paid when you have covered it; that occurrence drops off, and later ones still count. Undo paid clears the latest mark. An occurrence that is still unpaid stays in Until payday after its date passes. **Automatic payment** is only a reminder and does not mark a due date paid.
- **Credit cards.** Each card has a name, statement day, when the payment is due (days after the statement, or a day of the month), and the statement balance. Record a payment against that balance if you pay only part of it; the home numbers count what is left. **Paid** applies the full remainder. Clear payment undoes that for the current statement. A new statement balance starts with nothing paid. A remainder that is still unpaid stays in Until payday after its due date. **Automatic payment** is only a reminder and does not clear that remainder.
- **Statement prompt.** The first time you open the app on or after a card’s statement date, if you have not entered that cycle’s balance, a dialog asks for the statement balance before the home numbers appear. Cards that still need a balance are shown one after another. Skip is only for this visit; the next launch asks again until you save a balance.
- **Settings.** Explains the formula, lets you choose system, light, or dark appearance, and can erase all on-device data.
