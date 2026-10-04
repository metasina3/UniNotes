package com.sina.uninotes

/**
 * Physical-device / instrumentation checklist for mixed Persian/English editing.
 *
 * Automated IME/cursor validation is environment-sensitive; use this checklist on a real device:
 *
 * 1. Create subject: ساختمان داده
 * 2. Write note body:
 *    امروز دربارهٔ Linked List و تفاوتش با Array صحبت کردیم.
 * 3. Confirm caret moves naturally at Persian/Latin boundaries; no reversed storage.
 * 4. Enter:
 *    پیچیدگی الگوریتم O(n) است و مقدار index برابر ۲ می‌شود.
 * 5. Enter:
 *    این گره به Node بعدی اشاره می‌کند.
 * 6. Add an English paragraph, then a separate Persian paragraph; verify paragraph direction.
 * 7. Select, copy, paste, and half-space (نیم‌فاصله) editing.
 * 8. Rotate device while editing; content and cursor semantics remain usable.
 * 9. Background the app and reopen; autosaved text remains.
 */
class MixedLanguageChecklist
