# T9-still alive Dialer

Hebrew T9 contact search for Samsung Galaxy phones — type `343` on the keypad, get `אמא`.

**W-are-theim** · v1.3

## Why this exists

Samsung's dialer already ships the Hebrew keypad letters. Pull `SamsungDialer.apk` apart and
they are right there, as the `iw` variants of `dialpad_N_sub_letters`:

| key | letters | key | letters |
|-----|---------|-----|---------|
| 2 | דהו | 6 | זחט |
| 3 | אבג | 7 | רשת |
| 4 | מםנן | 8 | צץק |
| 5 | יכךל | 9 | סעפף |

They are labels only. The matching happens in `SamsungContactsProvider`, whose index tables are
`digit_name_lookup`, `digit_name_lookup_chn`, `digit_name_lookup_full_pinyin` and
`digit_name_lookup_first_pinyin`, fed by a function called `convertKeypadLettersToDigits` —
A–Z only. Script-specific handling exists for Pinyin, Japanese and Korean. Neither the dialer
dex nor the provider dex contains a single Hebrew character.

So typing `343` searches phone numbers, not names. This app is the missing half.

## What it does not do

It is **not** a dialer. It never claims `RoleManager.ROLE_DIALER` and never implements
`InCallService`. Calls go out as `ACTION_CALL`, so Telecom still hands them to Samsung's dialer —
call recording, the call log and the in-call UI are untouched.

## Matching

- Prefix match against **every word**, not just the first: `343` finds `עדי אמא של הודיה`.
- **Whole-word hits outrank prefix hits**: `אמא` ranks above `אמגד`, which merely starts with 343.
- **Spaces can be typed through**: `נדב וירטהיים` indexes `423` + `25762554`, so `4232` hits it.
- Final forms share their base letter's key (ם→4, ן→4, ך→5, ף→9, ץ→8).
- Niqqud and geresh/gershayim never split a word: `צה״ל` stays one word (`825`).
- Numbers still match as a substring, ranked below every name hit.

## Other languages

Only Hebrew is built in. Every other script is a plain-text `.t9` file you import from
**Settings → Keypad layout → Import layout file…** — no new build required.

```
# Arabic
name = Arabic
2 = ا ب ت ث
3 = ج ح خ
```

Keys `2`–`9` only. Spaces between letters are ignored. Sample layouts live in [`layouts/`](layouts/).

## Building

No Gradle. The toolchain is aapt2 → javac → d8 → zipalign → apksigner, entirely offline:

```powershell
.\build.ps1          # -> build\hebt9.apk
```

Requires a JDK 17, an Android SDK with `platforms/android-36` and `build-tools/36.0.0`.
Paths are set at the top of `build.ps1`.

Engine tests run on the desktop JVM — `T9Index` has no Android dependencies:

```powershell
javac -encoding UTF-8 -d build\test src\com\wirth\hebt9\T9Index.java test\T9Test.java
java -cp build\test T9Test
```

## License

CC BY-NC-ND 4.0 — free to use, no modified versions, no commercial use. See [LICENSE](LICENSE).
This is source-available, not open source.
