package dev.codex.nfcswitcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WalletActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout cardsList;
    private TextView hint;
    private View overlay;
    private TextView overlayText;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        setContentView(buildUi());
        refreshCards();
    }

    private View buildUi() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(20), insets.getSystemWindowInsetTop() + dp(18),
                    dp(20), insets.getSystemWindowInsetBottom() + dp(24));
            return insets;
        });
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(top);
        top.addView(text("NFC Switcher", 22, 0xfff5f5f7, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("×", 32, 0xffd1d1d6, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        hint = text("Выбери карту для эмуляции", 15, 0xff8e8e93, false);
        content.addView(hint, top(-1, -2, 8));

        cardsList = new LinearLayout(this);
        cardsList.setOrientation(LinearLayout.VERTICAL);
        content.addView(cardsList, top(-1, -2, 20));

        TextView nfcMark = text("⌁", 54, 0xff4a9cff, true);
        nfcMark.setGravity(Gravity.CENTER);
        content.addView(nfcMark, top(-1, dp(72), 24));
        TextView instruction = text("Поднесите устройство к считывателю", 18, 0xff8e8e93, false);
        instruction.setGravity(Gravity.CENTER);
        content.addView(instruction, top(-1, -2, 8));

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(24), dp(20), dp(24), dp(20));
        loading.setBackground(round(0xee202024, 22, 0xff4a9cff));
        loading.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(52), dp(52)));
        overlayText = text("Применяю карту...", 16, 0xfff5f5f7, true);
        overlayText.setGravity(Gravity.CENTER);
        loading.addView(overlayText, top(-1, -2, 14));
        screen.addView(loading, new FrameLayout.LayoutParams(dp(280), dp(150), Gravity.CENTER));
        overlay = loading;
        overlay.setVisibility(View.GONE);
        return screen;
    }

    private void refreshCards() {
        io.execute(() -> {
            List<String> cards = CardOrder.apply(this, NfcRootController.listCards());
            String active = NfcRootController.readActiveUid();
            main.post(() -> renderCards(cards, active));
        });
    }

    private void renderCards(List<String> cards, String active) {
        cardsList.removeAllViews();
        SharedPreferences names = getSharedPreferences("cards", MODE_PRIVATE);
        for (String uid : cards) {
            boolean selected = uid.equals(active);
            String name = names.getString(uid, uid).toUpperCase();
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(22), dp(18), dp(22), dp(18));
            card.setBackground(cardBackground(selected));
            card.setOnClickListener(v -> switchCard(uid));
            cardsList.addView(card, top(-1, dp(selected ? 190 : 112), selected ? 0 : 12));

            card.addView(text(name, 19, 0xfff5f5f7, true));
            card.addView(text(selected ? "АКТИВНАЯ КАРТА" : "Нажмите, чтобы выбрать", 12,
                    selected ? 0xffd7d4ff : 0xffc7c7cc, true), top(-1, -2, 8));
            TextView uidView = text(uid, selected ? 25 : 20, 0xffffffff, true);
            card.addView(uidView, new LinearLayout.LayoutParams(-1, 0, 1));
            card.addView(text("UID  ••••  " + uid.substring(Math.max(0, uid.length() - 5)), 15,
                    0xffeeeeef, false));
        }
    }

    private void switchCard(String uid) {
        overlayText.setText("Применяю " + uid + "...");
        overlay.setVisibility(View.VISIBLE);
        hint.setText("Перезапускаю NFC-контроллер");
        io.execute(() -> {
            NfcRootController.Result result = NfcRootController.switchUid(uid);
            String active = NfcRootController.readActiveUid();
            main.post(() -> {
                overlay.setVisibility(View.GONE);
                hint.setText(result.code == 0 ? "Карта готова к эмуляции" : "Не удалось применить карту");
                refreshCards();
            });
        });
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private GradientDrawable cardBackground(boolean active) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                active
                        ? new int[]{0xff5f55e8, 0xff25224f, 0xff17171c}
                        : new int[]{0xff34343a, 0xff202024, 0xff151517});
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), active ? 0xffa69dff : 0xff55555b);
        return drawable;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams top(int width, int height, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(margin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
