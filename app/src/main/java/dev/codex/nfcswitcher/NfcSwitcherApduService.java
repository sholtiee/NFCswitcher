package dev.codex.nfcswitcher;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class NfcSwitcherApduService extends HostApduService {
    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        return new byte[]{(byte) 0x6A, (byte) 0x82};
    }

    @Override
    public void onDeactivated(int reason) {}
}
