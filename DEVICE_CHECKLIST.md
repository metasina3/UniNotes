# UniNotes physical device checklist

## Language / editor
- [ ] UI remains English on a Persian system language
- [ ] Subject name `ساختمان داده` displays correctly
- [ ] Mixed sentence: `امروز دربارهٔ Linked List و تفاوتش با Array صحبت کردیم.`
- [ ] Mixed sentence: `پیچیدگی الگوریتم O(n) است و مقدار index برابر ۲ می‌شود.`
- [ ] Mixed sentence: `این گره به Node بعدی اشاره می‌کند.`
- [ ] English paragraph then Persian paragraph keep natural direction
- [ ] Selection, copy/paste, half-space, parentheses, numbers work
- [ ] Cursor / IME composition are not corrupted

## Daily notes
- [ ] Write note opens/creates today’s note only
- [ ] Reopening Write note continues the same note
- [ ] Autosave shows Saving… then Saved
- [ ] Leaving / backgrounding persists content
- [ ] Past notes keep original dates
- [ ] Delete note confirmation works

## Camera / photos
- [ ] Camera permission denied and permanently denied messaging
- [ ] In-app CameraX capture (no stock camera app)
- [ ] Rapid shutter taps do not corrupt saves
- [ ] Flash / zoom / focus hide or work according to device capability
- [ ] Photos stay private (not in system gallery)
- [ ] Viewer zoom, double-tap, swipe, delete

## Reliability
- [ ] Rename subject keeps photos/notes
- [ ] Delete subject removes dependent content
- [ ] Backup export / restore round trip
- [ ] Malformed backup rejected
- [ ] Low storage error is readable
- [ ] App restart preserves data

## Release install and Samsung verification

- Install the complete signed release APK on Galaxy A53 and S21+; capture the exact error if rejected. Both use the universal APK's ARM64 libraries.
- Do not confuse the old debug package with the release package. Export old content before deleting any installation.
- Test an update signed with the same key; verify existing notes/photos survive.
- In three-button and gesture navigation, confirm subject action buttons and camera shutter stay above the system navigation area.
- Check status-bar icon contrast, camera controls in landscape, and large text settings.
- Try permissions denial, return from Settings, front/rear switching, pinch zoom, focus taps near preview edges, flash changes, and background/resume.
- Photograph a real whiteboard on the A53. Check saved JPEG sharpness/rotation and confirm it never appears in the system gallery.
- Native-library alignment, APK signatures and emulated installs are automated checks, not proof of physical-camera quality or Samsung security-policy acceptance.
