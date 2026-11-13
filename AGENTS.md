# Agent rules

Every change, no matter how small, must bump both markers below before you open a pull request. Keenin uses them to tell which build is on a device. A docs-only edit, a one-line fix, and a comment change all count. Do not skip either bump because the service worker’s content hash would change anyway.

## User-visible version

In `web/src/js/ui.js`, the Settings line is `Web version MAJOR.MINOR.PATCH`. Always write three numeric parts. Never leave a two-part version such as `1.2`.

If the current line has only two parts, treat the missing patch as `0` (`1.1` is `1.1.0`) and then apply the bump below.

Choose the bump from the change:

- **PATCH** for a tiny fix, tweak, or docs-only edit. Increment the patch: `1.1` becomes `1.1.1`, and `1.1.1` becomes `1.1.2`.
- **MINOR** for a new feature or a noticeable behavior change. Increment the minor and reset the patch to `0`: `1.1.1` becomes `1.2.0`.
- **MAJOR** for a big overhaul or a breaking data change. Increment the major and reset minor and patch to `0`: `1.2.0` becomes `2.0.0`.

One bump per change. Do not leave the previous number in place.

## Service worker cache name

On that same change, increment the source cache token `calbudget-vN` by 1 in all of these:

- `web/src/sw.js` (`const CACHE = 'calbudget-vN'`)
- `web/scripts/build.mjs` (the check, the error text, and the `replaceAll` of that token)
- `web/test/storage-notice.test.js` (the test that reads `sw.js`: it must match the new token and reject the previous one)

`npm run build` rewrites `calbudget-vN` to a content hash in `web/dist`. The source token still has to move, or the build check fails and an installed copy can keep the old worker.

The version and the cache token are independent counters. Bump both. Do not set them equal to each other.

After the edits, run `npm test` and `npm run build` from `web/`.
