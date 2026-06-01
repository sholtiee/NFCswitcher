# NFCswitcher

NFCswitcher is an Android utility for managing local NFC card profiles, switching the active emulated UID on supported rooted devices, and working with ordinary NFC tags in a controlled lab environment.

The app is designed around two modes:

- **Default mode:** a compact card switcher and Apple Pay-style wallet chooser.
- **Advanced mode:** optional NFC tag tools such as extended metadata reading and safe NDEF writing.

## Features

- Dark glass UI with a fast wallet-style card picker.
- Saved card profiles with custom names.
- Manual card ordering by drag-and-drop.
- Android Quick Access Wallet integration.
- Root-backed UID profile switching for supported ST NFC HAL devices.
- Automatic mode switching:
  - outside the app: normal Android NFC tag reading;
  - while NFCswitcher or the wallet picker is open: listen-only emulation mode.
- Non-root mode for card organization, wallet UI, NDEF reading, and NDEF writing.
- GitHub Actions build pipeline with downloadable debug APK artifact.

## Build

```bash
./gradlew :app:assembleDebug
```

The APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions builds the same target on every push and pull request to `main`.

## Installation

Install a locally built APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch:

```bash
adb shell am start -n dev.codex.nfcswitcher/.MainActivity
```

## Root and Non-Root Modes

### Without root

The app can run without root, but it cannot modify vendor NFC HAL configuration.

Available without root:

- card library UI;
- wallet-style picker UI;
- Android Quick Access Wallet registration;
- reading tag metadata exposed by Android NFC APIs;
- writing standard NDEF text and URI records to writable NDEF tags;
- app settings and card ordering.

Unavailable without root:

- changing the low-level emulated UID;
- switching `POLLING_TECH_MASK`;
- restarting the vendor NFC HAL;
- automatic reader/emulation mode switching at HAL level.

### With root

Root mode is required for actual UID switching on the tested implementation. The app expects:

- Magisk or another `su` provider;
- writable Magisk overlay files under `/data/adb/modules/nfc_listen_only/system/vendor/etc`;
- ST NFC HAL configuration files compatible with `libnfc-hal-st.conf`;
- Android NFC service restart permissions through root shell.

The tested device family is Nothing Phone (2a) / Pacman with ST NFC service:

```text
android.hardware.nfc-service-st
```

Other phones may work only if they use a compatible ST NFC HAL configuration layout. Qualcomm, NXP, or strongly vendor-customized stacks usually require different files and are not supported by this repository as-is.

## Root Setup Outline

Rooting can wipe data and may void warranty. Back up the phone before unlocking the bootloader.

General Magisk flow:

1. Unlock the bootloader.
2. Obtain the exact stock boot image for the installed firmware build.
3. Patch the boot image with the Magisk app.
4. Flash or boot the patched image through fastboot.
5. Confirm `su` access from ADB:

```bash
adb shell su -c id
```

Use the official Magisk installation guide for device-specific details:

https://github.com/topjohnwu/Magisk/blob/master/docs/install.md

After root is working, the NFC HAL overlay module must provide the target files under:

```text
/data/adb/modules/nfc_listen_only/system/vendor/etc/
```

NFCswitcher does not ship a universal root module because vendor NFC stacks differ by phone and firmware.

## Safety Model

NFCswitcher is intended for your own devices, test cards, lab tags, and controlled NFC experiments.

The app does not store bank card PAN/CVV/payment cryptograms and does not provide payment-token provisioning. Use Google Wallet or a bank-issued wallet for real bank cards.

Advanced mode intentionally focuses on ordinary NFC/NDEF workflows and diagnostics. Protected credentials, third-party access cards, and payment cards should not be copied or modified without authorization.

## Useful References

- Android NFC basics: https://developer.android.com/guide/topics/connectivity/nfc/nfc
- Android `NfcAdapter`: https://developer.android.com/reference/android/nfc/NfcAdapter
- Android Quick Access Wallet: https://developer.android.com/reference/android/service/quickaccesswallet/QuickAccessWalletService
- Magisk installation: https://github.com/topjohnwu/Magisk/blob/master/docs/install.md

## License

MIT. See [LICENSE](LICENSE).

