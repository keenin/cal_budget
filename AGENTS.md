# Agent rules

Every change, no matter how small, must bump both version markers below before you open a pull request. Keenin uses them to tell which build is on a device. A docs-only edit, a one-line fix, and a comment change all count. Do not skip the bump because the service worker’s content hash would change anyway.

## User-visible version

In `web/src/js/ui.js`, the Settings line is `Web version X.Y`. Increment the minor number by 1 (`1.2` becomes `1.3`). Do not leave the previous number in place.

## Service worker cache name

The source cache token is `calbudget-vN`. Increment `N` by 1 in all of these, in the same change:

- `web/src/sw.js` (`const CACHE = 'calbudget-vN'`)
- `web/scripts/build.mjs` (the check, the error text, and the `replaceAll` of that token)
- `web/test/storage-notice.test.js` (the test that reads `sw.js`: it must match the new token and reject the previous one)

`npm run build` rewrites `calbudget-vN` to a content hash in `web/dist`. The source token still has to move, or the build check fails and an installed copy can keep the old worker.

The two numbers are independent. Bump both. Do not set them equal to each other.

After the edits, run `npm test` and `npm run build` from `web/`.
