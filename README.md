# Cal Budget

A calm, local-only Android app that shows two numbers: what is due until the next payday, and what the paycheck after that needs to cover.

The home screen stacks those amounts. Bills, credit cards, housing, pay, and settings live in the menu at the top right.

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

- **Pay schedule.** Set when you get paid: weekly, biweekly, monthly, or every N days, weeks, or months. There is no paycheck amount. Pick a recent or upcoming payday as the anchor date. You can set an end date or leave it ongoing. The home screen uses the next two paydays as the ends of its two windows. If you add more than one schedule, the soonest date wins.
- **Bills and mortgage / housing.** Same recurrence options, plus a name, amount, and optional notes. Mark the next due date paid if you pay early; that occurrence drops off whichever number it would have been in, and later ones still count. Undo paid clears the latest mark.
- **Credit cards.** Each card has a name, statement day, when the payment is due (days after the statement, or a day of the month), and the amount owed. You can edit that balance any time.
- **Statement prompt.** The first time you open the app on or after a card’s statement date, if you have not entered that cycle’s balance, a dialog asks for the statement balance before the home numbers appear. Cards that still need a balance are shown one after another. Skip is only for this visit; the next launch asks again until you save a balance.
- **Settings.** Explains the formula, lets you choose system, light, or dark appearance, and can erase all on-device data.

Data is stored with Room on the device. There is no account, backend, or network.

## How the two numbers are calculated

**Until payday** adds every unpaid bill and housing occurrence due from today through the next payday, including that payday, plus each credit card balance due on or before that payday. A card balance that is already past due stays in this number until you update it. If today is a payday, the next payday is the following one, so today does not close this window.

**Next period** adds unpaid bills and housing due strictly after that payday through the payday after it, plus card balances due in that same span. That is what the next check needs to cover. A card due after the second payday is in neither number.

Income is not part of either number. A pay schedule only supplies the dates. Marking a bill paid removes that occurrence from whichever window it falls in. If there is no later payday, Next period is $0.00. If no pay schedule is saved, both numbers are $0.00. Open the menu and choose Pay schedule to set when you get paid.
