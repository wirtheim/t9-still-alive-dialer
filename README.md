# T9 Still Alive Dialer 🇮🇱

**Made in Israel. Built for everyone.**

*[עברית](README.he.md)*

---

Your phone can edit photos with AI.

It can recognise the Moon, erase people from pictures, translate conversations in real time, and recommend a restaurant 8,700 kilometres away.

But if your system language isn't one of the few Samsung chose to support, it suddenly forgets how to find your contacts.

Or Mum.

Or Dad.

Or أحمد.

Or Γιώργος.

Or almost anyone who doesn't happen to use one of the lucky alphabets supported by Samsung's T9 engine.

The funny part is that the letters are already there. They're hiding inside the dialer like movie extras who never got a single line. You can find them in the code. They even appear on the keypad.

The search engine simply ignores them with the quiet confidence of someone who stopped caring at 4 p.m.

So we wrote the missing half.

Not a new dialer.

Not a replacement.

Not a revolution.

Just the part that should have existed in the first place.

Samsung's dialer stays exactly as it is.

Call recording stays.

Call logs stay.

The in-call screen stays.

The only difference is that your phone finally remembers not everyone speaks the same twenty-six letters.

## Features

* Real T9 contact search.
* Hebrew included out of the box.
* Support for virtually any language through simple `.t9` mapping files.
* Matches every word, not just the first.
* Language-aware character mapping.
* Frequent contacts.
* Default numbers for multi-number contacts.
* Themes and icons.
* Around 37 KB of code doing a job a multi-billion-dollar company decided could wait another year.

## Privacy

No Internet permission.

No cloud.

No servers.

No analytics.

Nobody knows who you call.

Just you, your contacts, and a phone that's finally doing its job.

---

## The letters really are in there

Pull `SamsungDialer.apk` apart and the Hebrew keypad letters are right where you would
expect them, as the `iw` variants of `dialpad_N_sub_letters`:

| key | letters | key | letters |
|-----|---------|-----|---------|
| 2 | דהו | 6 | זחט |
| 3 | אבג | 7 | רשת |
| 4 | מםנן | 8 | צץק |
| 5 | יכךל | 9 | סעפף |

They are labels. Nothing reads them back.

The matching happens in `SamsungContactsProvider`, whose index tables are
`digit_name_lookup`, `digit_name_lookup_chn`, `digit_name_lookup_full_pinyin` and
`digit_name_lookup_first_pinyin`, all fed by a function called
`convertKeypadLettersToDigits` — A–Z only. Script-specific handling exists for Pinyin,
Japanese and Korean. Neither the dialer dex nor the provider dex contains a single Hebrew
character.

So typing `343` searches phone numbers, not names. This app is the other half.

## Not a dialer

It never claims `RoleManager.ROLE_DIALER` and never implements `InCallService`. Calls go
out as `ACTION_CALL`, so Telecom still hands them to Samsung's dialer — call recording,
the call log and the in-call UI are untouched.

## Matching

- Prefix match against **every word**, not just the first: `343` finds `עדי אמא של הודיה`.
- **Whole-word hits outrank prefix hits**: `אמא` ranks above `אמגד`, which merely starts with 343.
- **Spaces can be typed through**: `נדב וירטהיים` indexes `423` + `25762554`, so `4232` hits it.
- Final forms share their base letter's key (ם→4, ן→4, ך→5, ף→9, ץ→8).
- Niqqud, Arabic harakat and combining marks never split a word: `צה״ל` stays one word (`825`).
- Numbers still match as a substring, ranked below every name hit.

## Other languages

Only Hebrew is built in. Every other script is a plain-text `.t9` file you import from
**Settings → Keypad layout → Import layout file…** — no new build required.

```
name = Greek
2 = α β γ ά
3 = δ ε ζ έ
```

Keys `2`–`9` only. Spaces between letters are ignored, so accented forms can share a key
with their base letter. Ready-made layouts for [Arabic](layouts/Arabic.t9),
[Greek](layouts/Greek.t9) and [Russian](layouts/Russian.t9) live in [`layouts/`](layouts/).

## Building

No Gradle. Sideload APK:

```powershell
.\build.ps1          # -> build\hebt9.apk
```

Play-ready App Bundle:

```powershell
.\build-aab.ps1      # -> build-aab\t9-still-alive-dialer.aab
```

Requires a JDK 17, an Android SDK with `platforms/android-36` and `build-tools/36.0.0`,
and — for the bundle — `bundletool`. Paths are set at the top of each script.

Engine tests run on the desktop JVM; `T9Index` has no Android dependencies:

```powershell
javac -encoding UTF-8 -d build\test src\com\wirth\hebt9\T9Index.java test\T9Test.java
java -cp build\test T9Test
```

Store screenshots use synthetic contacts so no real one is ever published:

```
adb shell am start -n com.wirth.hebt9/.MainActivity --ez demo true
```

## License

Released under CC BY-NC-ND 4.0.

Free to use, study and redistribute with attribution.

No commercial use.

No modified redistributions.

Because Android was supposed to be open.

And an operating system that forgets languages is a bit like a dictionary that only knows
the letter A.

*(For the avoidance of doubt: this is source-available, not open source. The OSI
definition requires that modification and commercial use both be permitted, and this
license allows neither. See [LICENSE](LICENSE).)*

---

**Some phones learn AI.**

**We just taught one to remember your language.**
