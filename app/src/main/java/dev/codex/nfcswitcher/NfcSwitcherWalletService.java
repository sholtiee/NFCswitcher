package dev.codex.nfcswitcher;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.content.SharedPreferences;
import android.service.quickaccesswallet.GetWalletCardsCallback;
import android.service.quickaccesswallet.GetWalletCardsRequest;
import android.service.quickaccesswallet.GetWalletCardsResponse;
import android.service.quickaccesswallet.QuickAccessWalletService;
import android.service.quickaccesswallet.SelectWalletCardRequest;
import android.service.quickaccesswallet.WalletCard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NfcSwitcherWalletService extends QuickAccessWalletService {
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void onWalletCardsRequested(GetWalletCardsRequest request, GetWalletCardsCallback callback) {
        io.execute(() -> {
            List<String> uids = CardOrder.apply(this, NfcRootController.listCards());
            String active = NfcRootController.readActiveUid();
            SharedPreferences names = getSharedPreferences("cards", MODE_PRIVATE);
            List<WalletCard> cards = new ArrayList<>();
            int selected = 0;
            int limit = Math.min(request.getMaxCards(), uids.size());
            for (int i = 0; i < limit; i++) {
                String uid = uids.get(i);
                String name = names.getString(uid, uid).toUpperCase();
                if (uid.equals(active)) selected = i;
                Intent intent = new Intent(this, WalletActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent openApp = PendingIntent.getActivity(this, i, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                WalletCard card = new WalletCard.Builder(
                        uid,
                        WalletCard.CARD_TYPE_NON_PAYMENT,
                        Icon.createWithResource(this, R.drawable.wallet_card),
                        name,
                        openApp)
                        .setCardLabel(name)
                        .build();
                cards.add(card);
            }
            callback.onSuccess(new GetWalletCardsResponse(cards, cards.isEmpty() ? 0 : selected));
        });
    }

    @Override
    public void onWalletCardSelected(SelectWalletCardRequest request) {
        io.execute(() -> NfcRootController.switchUid(request.getCardId()));
    }

    @Override
    public void onWalletDismissed() {}
}
