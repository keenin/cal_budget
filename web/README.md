# Cal Budget for the web

A static, installable copy of Cal Budget. It opens from a link, can be added to an iPhone or Android home screen, and keeps every bill, card, and paycheck date on that device. There is no account, no server, and no analytics.

The numbers match the Android app. Until payday, Next period, and Following period are the three pay windows. Automatic payment is only a reminder: an item stays until you mark it paid, and it is labeled Overdue starting the day after its due date. Amounts are integer cents.

## Run locally

Node.js 22 or newer. No packages to install.

```bash
cd web
npm test
npm start
```

`npm start` writes `web/dist` and serves it at <http://localhost:4173>. Use that address rather than opening the HTML file directly, so the service worker and modules can load.

## Deploy to Cloudflare Pages

The site is the contents of `web/dist`.

- Root directory: `web`
- Build command: `npm run build`
- Build output directory: `dist`

No environment variables. Attach the custom subdomain in the Pages project. The app uses hash URLs (`/#/bills`), so the host only has to serve the files.

`/_headers` asks the browser not to cache `sw.js` or `index.html`. The service worker caches the rest. A new build changes the cache name, the new worker takes over, and IndexedDB is left alone.

## Backups

Settings → Export backup downloads a JSON file. Import backup replaces the data on this device after you confirm. That is the way to move a budget between phones, because nothing is synced. The Android app does not write this file, so an Android install cannot be imported here.

## Data

IndexedDB database `calbudget`, schema version 1 (`events`, `cards`, `meta`). Later versions may only add stores or fields. The app requests persistent storage with `navigator.storage.persist()`. Browsers can still delete site data; the JSON backup is the durable copy.
