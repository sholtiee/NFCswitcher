package dev.codex.nfcswitcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    static final String PREFS = "settings";
    static final String ADVANCED = "advanced";
    static final String READ_NDEF = "read_ndef";
    static final String DIAGNOSTICS = "diagnostics";
    static final String SCAN_TIMEOUT = "scan_timeout";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18, 18, 20));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 20));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff151517);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(18), insets.getSystemWindowInsetTop() + dp(14),
                    dp(18), insets.getSystemWindowInsetBottom() + dp(18));
            return insets;
        });
        scroll.addView(root);

        TextView title = text("Настройки", 27, 0xfff4f3fb, true);
        root.addView(title);
        root.addView(text("NFC Switcher", 15, 0xff8d8b94, false), top(-1, -2, 2));

        LinearLayout general = panel();
        root.addView(general, top(-1, -2, 20));
        general.addView(text("Основное", 19, 0xfff4f3fb, true));
        general.addView(toggle("Advanced mode", ADVANCED, false), top(-1, -2, 12));
        general.addView(toggle("Собирать NDEF при чтении", READ_NDEF, true), top(-1, -2, 8));
        general.addView(toggle("Расширенная диагностика", DIAGNOSTICS, false), top(-1, -2, 8));

        LinearLayout timing = panel();
        root.addView(timing, top(-1, -2, 14));
        timing.addView(text("Ожидание карты", 19, 0xfff4f3fb, true));
        TextView timeout = text("", 15, 0xffaaa7b4, false);
        timing.addView(timeout, top(-1, -2, 8));
        SeekBar timeoutBar = new SeekBar(this);
        timeoutBar.setMax(75);
        timeoutBar.setProgress(prefs.getInt(SCAN_TIMEOUT, 45) - 15);
        timeout.setText("Таймаут считывания: " + (timeoutBar.getProgress() + 15) + " сек.");
        timeoutBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                int seconds = value + 15;
                timeout.setText("Таймаут считывания: " + seconds + " сек.");
                prefs.edit().putInt(SCAN_TIMEOUT, seconds).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        timing.addView(timeoutBar, top(-1, -2, 8));

        LinearLayout wallet = panel();
        root.addView(wallet, top(-1, -2, 14));
        wallet.addView(text("Кошелек", 19, 0xfff4f3fb, true));
        wallet.addView(text("Для банковских карт используй токенизированный кошелек. NFC Switcher не хранит реквизиты и платежные ключи.",
                14, 0xffaaa7b4, false), top(-1, -2, 8));
        Button walletSettings = button("Системный Wallet");
        walletSettings.setOnClickListener(v -> startActivity(new Intent("android.settings.NFC_PAYMENT_SETTINGS")));
        wallet.addView(walletSettings, top(-1, dp(46), 12));
        Button googleWallet = button("Открыть Google Wallet");
        googleWallet.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.walletnfcrel");
            if (launch != null) startActivity(launch);
            else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.walletnfcrel")));
        });
        wallet.addView(googleWallet, top(-1, dp(46), 8));

        LinearLayout system = panel();
        root.addView(system, top(-1, -2, 14));
        system.addView(text("Система", 19, 0xfff4f3fb, true));
        Button nfcSettings = button("Настройки NFC");
        nfcSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
        system.addView(nfcSettings, top(-1, dp(46), 12));
        return scroll;
    }

    private Switch toggle(String title, String key, boolean defaultValue) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextColor(0xffe7e5ee);
        toggle.setTextSize(15);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        return toggle;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(round(0xff242427, 18, 0x33ffffff));
        return panel;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(0xfff2f0ff);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(0x22333338, 12, 0x557c6dff));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams top(int width, int height, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(top);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
