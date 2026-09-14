# CalcVault

A working scientific calculator that is also an encrypted vault. Type your code
into the calculator, press `=`, and the vault opens. Type anything else and it
is just a calculator.

Original Kotlin/Compose implementation — no code from any other app.

## Using it

**First run.** Long-press the `=` key to choose an unlock code (4–12 digits).
That gesture is the only entry point, and it disappears once a code is set: from
then on, long-pressing `=` does nothing.

**Unlocking.** Type the code and press `=`. A wrong code is evaluated as
ordinary arithmetic, so `1234 =` simply prints `1234`. Nothing on screen
distinguishes a phone with a vault from one without.

**Decoy code.** Settings can hold a second code that opens a separate, empty
vault. If you are ever pressured to unlock the app, the decoy opens something
harmless and the real vault stays invisible.

## The calculator

Full scientific keypad: trigonometric, inverse and hyperbolic functions,
natural and base-10 logarithms, powers and roots, factorial, percent, `π` and
`e`, `Ans`, memory keys, degree/radian toggle and a history tape.

The parser is recursive descent with the precedence a physical calculator uses —
`2^3^2` is 512, `-2^2` is −4, and adjacent values multiply implicitly, so `2π`
and `2(3+4)` do what you expect. Unclosed parentheses are closed on `=`.

## How the privacy side works

| Layer | What it does |
|---|---|
| Vault key | A random 256-bit AES key encrypts every file and the index. It exists only in memory, only while unlocked. |
| PIN wrapping | The vault key is wrapped with PBKDF2-HMAC-SHA256 (210,000 iterations, random salt). The code itself is never stored, in any form. |
| Device binding | The wrapped blob is encrypted again with a non-exportable Android Keystore key. Copying the app's data to another device yields nothing. |
| Files | AES-256-GCM, streamed, with a fresh IV per file. Names, sizes and types live inside the encrypted index, so even the file list is unreadable while locked. |
| Auto-lock | The key is dropped when the app leaves the foreground, after a configurable delay. |
| Screenshots | `FLAG_SECURE` on the vault screens only, which also removes them from the recent-apps preview. |
| Intruder log | Optional silent front-camera photo after repeated wrong codes, encrypted with the device key since the vault key is unavailable at that point. |
| No network | The app declares no `INTERNET` permission. Nothing in the vault can leave the device. |

**There is no recovery.** No backup code, no email reset, nothing the developer
could unlock. Forgetting the code means the files are gone, which is what makes
the rest of it true.

The one place plaintext touches storage is video playback: a clip is decrypted
into the app's private cache so a player can open it, and deleted when the
viewer closes or the vault locks.

## Building

```bash
./gradlew assembleDebug
```

Requires JDK 17 and an Android SDK with API 36. Unit tests for the expression
engine:

```bash
./gradlew test
```

## Layout

```
app/src/main/java/com/calcvault/
  calc/       Tokenizer, recursive-descent parser, result formatting
  crypto/     Key derivation, Keystore wrapping, AES-GCM helpers
  vault/      Encrypted index and file storage
  security/   Session lifetime, auto-lock, settings, intruder log
  ui/         Compose screens: calculator, vault, viewer, settings, setup
```
