I keep this here for myself. It was written with AI for something I wanted, not as a product I maintain for other people. You are welcome to use it or copy it.

# Cal Budget

Cal Budget is an installable progressive web app at [https://budget.keenin.com](https://budget.keenin.com). The home screen shows three numbers: what is due until the next payday, what is due in the period after that, and what is due in the period after that one. Tap a number to see the bills, housing payments, and card remainders that add up to it.

Bills, credit cards, housing, pay schedules, and settings are in the menu. All of that data stays on the device in IndexedDB. There is no account and no server.

## Install

Open [https://budget.keenin.com](https://budget.keenin.com), then add it to the home screen so it opens like an app.

- **iPhone or iPad (Safari).** Tap Share, then **Add to Home Screen**, then **Add**.
- **Android (Chrome).** Open the browser menu and tap **Install app** or **Add to Home screen**, then confirm.
- **Desktop (Chrome or Edge).** Use the install icon in the address bar, or the browser menu’s install command.

After that, launch Cal Budget from the home screen icon. In Settings, **Keep data on this device** asks the browser to retain the IndexedDB data. Browsers can still delete site data, so keep a backup.

## Using it

- **Pay schedule.** Set when you get paid: weekly, biweekly, monthly, or every N days, weeks, or months. Pick a recent or upcoming payday as the anchor. The home screen uses the next three paydays as the ends of its three windows. A paycheck amount is optional and is never added into what you owe. If you add more than one schedule, the soonest date wins.
- **Bills and mortgage / housing.** Same recurrence options, plus a name, amount, and optional notes. Mark the earliest unpaid date paid when you have covered it. Undo paid clears the latest mark. An occurrence that is still unpaid stays in Until payday after its date passes, and shows as overdue the day after it was due.
- **Credit cards.** Each card has a name, statement day, when the payment is due (days after the statement, or a day of the month), and the statement balance. **Paid** applies the full remainder. Record payment applies a partial amount. A remainder that is still unpaid stays in Until payday after its due date.
- **Automatic payment.** On a bill, housing payment, or card, this is a reminder that the amount drops off on its due date. With it off, the amount stays until you mark it paid or, for a card, record a payment that covers it.
- **Statement prompt.** The first time you open the app on or after a card’s statement date, if you have not entered that cycle’s balance, a dialog asks for the statement balance before the home numbers appear. Skip is only for this visit.
- **Settings.** Explains the formula, and lets you choose system, light, or dark appearance, plus a text size. **Erase all data** deletes the budget stored on this device.

## Backup

In Settings, **Export backup** downloads a JSON file of the bills, housing, pay schedules, cards, and appearance stored on this device. **Import backup** reads that file and, after you confirm, replaces the data on this device. Nothing is synced, so the file is how a budget moves to another browser or phone. Keep a copy before you clear site data.

## Local development

Node.js 22 or newer. There are no packages to install.

```bash
cd web && npm test && npm run build
```

`npm start` in `web/` builds `dist` and serves it at <http://localhost:4173>. Use that address rather than opening the HTML file directly, so the service worker and modules can load.

## Deploy

Cloudflare Pages deploys the site automatically from `main`.

- Root directory: `web`
- Build command: `npm run build`
- Build output directory: `dist`

No environment variables. The app uses hash URLs (`/#/bills`), so the host only has to serve the files in `dist`.
