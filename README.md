# Cal Budget

A calm, local-only Android app that answers one question: how much money needs to be in your bank account until your next payday?

The home screen is a single dollar amount. Bills, credit cards, housing, pay, and settings live in the menu at the top right.

## Open and run

1. Install [Android Studio](https://developer.android.com/studio) with Android SDK 35.
2. Choose **File → Open** and select this repository.
3. Let Gradle sync. If Android Studio offers to install a missing SDK platform or build-tools package, accept it.
4. Pick an emulator or a device running Android 8.0 (API 26) or newer, then press **Run**.

From a terminal, with `ANDROID_HOME` pointing at your SDK:

```bash
./gradlew assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It includes `arm64-v8a` and `x86_64`, so it runs on a current phone and on an x86_64 emulator.

A smaller release APK, signed with the debug key so it can be installed locally, is arm64-only:

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. For an x86_64 emulator build of that same release variant:

```bash
./gradlew assembleRelease -Pabi=x86_64
```

## Using it

- **Pay schedule.** Set when you get paid: weekly, biweekly, monthly, or every N days, weeks, or months. There is no paycheck amount. Pick a recent or upcoming payday as the anchor date. You can set an end date or leave it ongoing. The next payday is what the home screen counts toward. If you add more than one schedule, the soonest date wins.
- **Bills and mortgage / housing.** Same recurrence options, plus a name, amount, and optional notes.
- **Credit cards.** Each card has a name, statement day, when the payment is due (days after the statement, or a day of the month), and the amount owed. You can edit that balance any time.
- **Statement prompt.** The first time you open the app on or after a card’s statement date, if you have not entered that cycle’s balance, a dialog asks for the statement balance before the home number appears. Cards that still need a balance are shown one after another. Skip is only for this visit; the next launch asks again until you save a balance.
- **Settings.** Explains the formula, lets you choose system, light, or dark appearance, and can erase all on-device data.

Data is stored with Room on the device. There is no account, backend, or network.

## How the home number is calculated

Add every bill and housing occurrence due from today through the next payday, including both ends. Add each credit card’s current statement balance when that payment is due on or before payday. A card balance that is already past due stays in the total until you update it. Income is not part of the total. A pay schedule only supplies the next payday.

If today is a payday, the window runs through the following payday, so the number is what you need until more pay arrives. If no pay schedule is saved, the home screen shows $0.00. Open the menu and choose Pay schedule to set when you get paid.
