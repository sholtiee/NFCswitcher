package dev.codex.nfcswitcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class NfcRootController {
    static final String CARDS = "/data/adb/nfc_switcher/cards";
    static final String MODULE = "/data/adb/modules/nfc_listen_only/system/vendor/etc";
    static final String STATE = "/data/adb/cardemulator-autoswitch.current";

    private NfcRootController() {}

    static List<String> listCards() {
        Result result = run("for d in '" + CARDS + "'/*; do [ -d \"$d\" ] && basename \"$d\"; done; exit 0");
        List<String> cards = new ArrayList<>();
        for (String line : result.out.split("\\R")) {
            String uid = line.trim().toUpperCase();
            if (uid.matches("[0-9A-F]{2}(:[0-9A-F]{2}){3,9}")) cards.add(uid);
        }
        return cards;
    }

    static String readActiveUid() {
        Result result = run("sed -n 's/.*33,04,\\([^}]*\\)}.*/\\1/p' " + MODULE + "/libnfc-hal-st.conf | tail -n 1");
        String raw = result.out.trim().replace(",", ":").replace(" ", "").toUpperCase();
        return raw.matches("[0-9A-F]{2}(:[0-9A-F]{2}){3,9}") ? raw : "";
    }

    static Result switchUid(String uid) {
        String command =
                "src='" + CARDS + "/" + uid + "'; " +
                "mod='" + MODULE + "'; " +
                "[ -f \"$src/libnfc-hal-st.conf\" ] || exit 2; " +
                "cp \"$src/libnfc-hal-st.conf\" \"$mod/libnfc-hal-st.conf\"; " +
                "cp \"$src/libnfc-hal-st-st54j.conf\" \"$mod/libnfc-hal-st-st54j.conf\"; " +
                "chown root:root \"$mod/libnfc-hal-st.conf\" \"$mod/libnfc-hal-st-st54j.conf\"; " +
                "chmod 0644 \"$mod/libnfc-hal-st.conf\" \"$mod/libnfc-hal-st-st54j.conf\"; " +
                "echo '" + uid + "' > " + STATE + "; sync; " +
                "cmd nfc disable-nfc >/dev/null 2>&1; " +
                "pid=$(pidof com.android.nfc 2>/dev/null); [ -n \"$pid\" ] && kill -9 \"$pid\"; " +
                "oldhal=$(pidof android.hardware.nfc-service-st 2>/dev/null); [ -n \"$oldhal\" ] && kill -9 \"$oldhal\"; " +
                "i=0; while [ $i -lt 20 ]; do hal=$(pidof android.hardware.nfc-service-st 2>/dev/null); " +
                "[ -n \"$hal\" ] && [ \"$hal\" != \"$oldhal\" ] && break; sleep 0.2; i=$((i+1)); done; " +
                "cmd nfc enable-nfc >/dev/null 2>&1; " +
                "i=0; while [ $i -lt 25 ]; do pidof com.android.nfc >/dev/null 2>&1 && " +
                "dumpsys nfc 2>/dev/null | grep -q '^mState=on' && break; sleep 0.2; i=$((i+1)); done";
        return run(command);
    }

    static Result setPollingMode(String mask) {
        String command =
                "mod='" + MODULE + "'; " +
                "current=$(sed -n 's/^POLLING_TECH_MASK=//p' \"$mod/libnfc-nci.conf\" | tail -n 1); " +
                "[ \"$current\" = '" + mask + "' ] && exit 0; " +
                "sed -i 's/POLLING_TECH_MASK=.*/POLLING_TECH_MASK=" + mask + "/' " +
                "\"$mod/libnfc-nci.conf\" \"$mod/libnfc-nci-felica.conf\"; sync; " +
                restartNfcCommand();
        return run(command);
    }

    private static String restartNfcCommand() {
        return "cmd nfc disable-nfc >/dev/null 2>&1; " +
                "pid=$(pidof com.android.nfc 2>/dev/null); [ -n \"$pid\" ] && kill -9 \"$pid\"; " +
                "oldhal=$(pidof android.hardware.nfc-service-st 2>/dev/null); [ -n \"$oldhal\" ] && kill -9 \"$oldhal\"; " +
                "i=0; while [ $i -lt 20 ]; do hal=$(pidof android.hardware.nfc-service-st 2>/dev/null); " +
                "[ -n \"$hal\" ] && [ \"$hal\" != \"$oldhal\" ] && break; sleep 0.2; i=$((i+1)); done; " +
                "cmd nfc enable-nfc >/dev/null 2>&1; " +
                "i=0; while [ $i -lt 25 ]; do pidof com.android.nfc >/dev/null 2>&1 && " +
                "dumpsys nfc 2>/dev/null | grep -q '^mState=on' && break; sleep 0.2; i=$((i+1)); done";
    }

    static Result run(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(16, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(124, "timeout");
            }
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
            }
            return new Result(process.exitValue(), out.toString());
        } catch (Exception e) {
            return new Result(1, e.getMessage() == null ? "unknown error" : e.getMessage());
        }
    }

    static final class Result {
        final int code;
        final String out;

        Result(int code, String out) {
            this.code = code;
            this.out = out == null ? "" : out;
        }
    }
}
