package dev.codex.nfcswitcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class CardOrder {
    private static final String PREFS = "card_order";
    private static final String ORDER = "uids";
    private static final String SEPARATOR = "\n";

    private CardOrder() {}

    static List<String> apply(Context context, List<String> available) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(ORDER, "");
        List<String> ordered = new ArrayList<>();
        for (String uid : raw.split(SEPARATOR)) {
            if (available.contains(uid) && !ordered.contains(uid)) ordered.add(uid);
        }
        for (String uid : available) {
            if (!ordered.contains(uid)) ordered.add(uid);
        }
        save(context, ordered);
        return ordered;
    }

    static void moveTo(Context context, String uid, String targetUid) {
        List<String> cards = apply(context, NfcRootController.listCards());
        int from = cards.indexOf(uid);
        int to = cards.indexOf(targetUid);
        if (from < 0 || to < 0 || from == to) return;
        cards.remove(from);
        cards.add(to, uid);
        save(context, cards);
    }

    static void save(Context context, List<String> cards) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(ORDER, String.join(SEPARATOR, cards))
                .apply();
    }
}
