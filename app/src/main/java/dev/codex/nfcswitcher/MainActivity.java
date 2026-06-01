package dev.codex.nfcswitcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String CARDS = "/data/adb/nfc_switcher/cards";
    private static final String MODULE = "/data/adb/modules/nfc_listen_only/system/vendor/etc";
    private static final String STATE = "/data/adb/cardemulator-autoswitch.current";
    private static final String STORE = "cards";
    private static final String DETAILS_STORE = "card_details";
    private static final String TAG = "NfcSwitcher";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NfcAdapter nfcAdapter;
    private boolean scanning;
    private LinearLayout cardsList;
    private TextView activeUidView;
    private TextView rootView;
    private TextView nfcView;
    private TextView modeView;
    private TextView logView;
    private Button refreshButton;
    private Button scanButton;
    private EditText searchInput;
    private LinearLayout advancedTools;
    private NdefMessage pendingNdefWrite;
    private String lastActiveUid = "";
    private View draggedCard;
    private boolean reorderMode;
    private Button reorderButton;
    private View progressOverlay;
    private TextView progressText;
    private final Runnable scanTimeout = () -> {
        if (!scanning) return;
        scanning = false;
        pendingNdefWrite = null;
        disableReaderMode();
        appendLog("Время ожидания вышло. Возвращаю режим эмуляции.");
        restoreListenMode();
    };

    private final Map<String, CardInfo> cards = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(18, 18, 20));
        window.setNavigationBarColor(Color.rgb(18, 18, 20));
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setContentView(buildUi());
        refresh();
    }

    private View buildUi() {
        FrameLayout screen = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(makeBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(18), insets.getSystemWindowInsetTop() + dp(14),
                    dp(18), insets.getSystemWindowInsetBottom() + dp(18));
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView logo = text("⌁", 26, 0xfff2f0ff, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(0xff7165ff, 18, 0x557d72ff));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        top.addView(logo, logoLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(14), 0, 0, 0);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        titleBox.addView(text("NFC Switcher", 22, 0xfff4f3fb, true));
        titleBox.addView(text("Переключатель карт", 14, 0xff8d8b94, false));

        Button appSettings = glassButton("⚙");
        appSettings.setTextSize(20);
        appSettings.setContentDescription("Настройки приложения");
        appSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        settingsLp.rightMargin = dp(8);
        top.addView(appSettings, settingsLp);

        refreshButton = glassButton("Обновить");
        refreshButton.setOnClickListener(v -> refresh());
        top.addView(refreshButton, new LinearLayout.LayoutParams(dp(104), dp(44)));

        LinearLayout status = panel();
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(status, lpTop(-1, -2, 18));

        status.addView(text("Текущий статус", 18, 0xfff4f3fb, true));
        activeUidView = text("UID: ...", 24, 0xff9d8cff, true);
        status.addView(activeUidView, lpTop(-1, -2, 10));
        rootView = text("Root: проверка", 14, 0xffaaa7b4, false);
        nfcView = text("NFC: проверка", 14, 0xffaaa7b4, false);
        modeView = text("Режим: эмуляция", 14, 0xffd9d2ff, true);
        status.addView(rootView, lpTop(-1, -2, 8));
        status.addView(nfcView, lpTop(-1, -2, 4));
        status.addView(modeView, lpTop(-1, -2, 4));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        status.addView(modes, lpTop(-1, dp(44), 12));
        Button emulateButton = glassButton("Эмуляция");
        emulateButton.setOnClickListener(v -> enterEmulationMode());
        modes.addView(emulateButton, new LinearLayout.LayoutParams(0, -1, 1));
        Button readerButton = glassButton("Считывание");
        readerButton.setOnClickListener(v -> startScan());
        LinearLayout.LayoutParams readerLp = new LinearLayout.LayoutParams(0, -1, 1);
        readerLp.leftMargin = dp(8);
        modes.addView(readerButton, readerLp);

        LinearLayout walletActions = new LinearLayout(this);
        walletActions.setOrientation(LinearLayout.HORIZONTAL);
        status.addView(walletActions, lpTop(-1, dp(44), 8));
        Button quickWallet = glassButton("Быстрый выбор");
        quickWallet.setOnClickListener(v -> startActivity(new Intent(this, WalletActivity.class)));
        walletActions.addView(quickWallet, new LinearLayout.LayoutParams(0, -1, 1));
        Button walletSettings = glassButton("Настроить Wallet");
        walletSettings.setOnClickListener(v -> openWalletSettings());
        LinearLayout.LayoutParams walletSettingsLp = new LinearLayout.LayoutParams(0, -1, 1);
        walletSettingsLp.leftMargin = dp(8);
        walletActions.addView(walletSettings, walletSettingsLp);

        TextView section = text("Карты", 20, 0xfff4f3fb, true);
        root.addView(section, lpTop(-1, -2, 22));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actions, lpTop(-1, dp(46), 10));
        Button addButton = glassButton("+ Добавить");
        addButton.setOnClickListener(v -> showAddDialog("", ""));
        actions.addView(addButton, new LinearLayout.LayoutParams(0, -1, 1));
        scanButton = glassButton("Считать карту");
        scanButton.setOnClickListener(v -> startScan());
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(0, -1, 1);
        scanLp.leftMargin = dp(10);
        actions.addView(scanButton, scanLp);

        reorderButton = glassButton("Изменить порядок");
        reorderButton.setOnClickListener(v -> {
            reorderMode = !reorderMode;
            reorderButton.setText(reorderMode ? "Готово" : "Изменить порядок");
            renderCards(lastActiveUid);
        });
        root.addView(reorderButton, lpTop(-1, dp(44), 8));

        searchInput = new EditText(this);
        searchInput.setHint("Поиск по названию или UID");
        searchInput.setSingleLine(true);
        searchInput.setTextColor(0xfff4f3fb);
        searchInput.setHintTextColor(0xff77757f);
        searchInput.setBackground(round(0x332e2e33, 12, 0x447c6dff));
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setVisibility(View.GONE);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderCards(lastActiveUid);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchInput, lpTop(-1, dp(46), 10));

        advancedTools = new LinearLayout(this);
        advancedTools.setOrientation(LinearLayout.VERTICAL);
        root.addView(advancedTools, lpTop(-1, -2, 10));

        cardsList = new LinearLayout(this);
        cardsList.setOrientation(LinearLayout.VERTICAL);
        root.addView(cardsList, lpTop(-1, -2, 10));

        LinearLayout logPanel = panel();
        logPanel.setOrientation(LinearLayout.VERTICAL);
        logPanel.setPadding(dp(18), dp(16), dp(18), dp(16));
        root.addView(logPanel, lpTop(-1, -2, 18));
        logPanel.addView(text("Журнал", 18, 0xfff4f3fb, true));
        logView = text("Готов.", 13, 0xffaaa7b4, false);
        logView.setLineSpacing(2, 1.05f);
        logPanel.addView(logView, lpTop(-1, -2, 10));

        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(26), dp(22), dp(26), dp(22));
        overlay.setBackground(round(0xee242427, 22, 0x557c6dff));
        ProgressBar spinner = new ProgressBar(this);
        overlay.addView(spinner, new LinearLayout.LayoutParams(dp(52), dp(52)));
        progressText = text("Перезапускаю NFC...", 16, 0xfff4f3fb, true);
        progressText.setGravity(Gravity.CENTER);
        overlay.addView(progressText, lpTop(-1, -2, 14));

        FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(dp(280), dp(150), Gravity.CENTER);
        screen.addView(overlay, overlayLp);
        progressOverlay = overlay;
        progressOverlay.setVisibility(View.GONE);
        return screen;
    }

    private void refresh() {
        setBusy(true);
        appendLog("Обновляю состояние...");
        io.execute(() -> {
            boolean hasRoot = runRoot("id").out.contains("uid=0");
            String active = readActiveUid();
            String nfc = runRoot("service check nfc").out.trim();
            CardsLoadResult cardsResult = loadCards();
            main.post(() -> {
                cards.clear();
                for (CardInfo card : cardsResult.cards) cards.put(card.uid, card);
                rootView.setText(hasRoot ? "Root: доступ есть" : "Root: нет доступа");
                nfcView.setText(nfc.contains("found") ? "NFC: включен" : "NFC: " + oneLine(nfc));
                activeUidView.setText("UID: " + (active.isEmpty() ? "не найден" : active));
                lastActiveUid = active;
                renderCards(active);
                renderAdvancedTools();
                appendLog("Найдено карт: " + cards.size() +
                        (cardsResult.debug.isEmpty() ? "" : " · " + cardsResult.debug));
                setBusy(false);
            });
        });
    }

    private CardsLoadResult loadCards() {
        Map<String, CardInfo> result = new LinkedHashMap<>();
        ShellResult list = runRoot("for d in '" + CARDS + "'/*; do [ -d \"$d\" ] && basename \"$d\"; done; exit 0");
        for (String line : list.out.split("\\R")) {
            String uid = line.trim();
            if (uid.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){3,9}")) {
                result.put(uid.toUpperCase(), new CardInfo(uid.toUpperCase(), uid.toUpperCase()));
            }
        }

        SharedPreferences prefs = getSharedPreferences(STORE, MODE_PRIVATE);
        for (String uid : prefs.getAll().keySet()) {
            if (isValidUid(uid)) result.put(uid, new CardInfo(uid, uid));
        }
        for (Map.Entry<String, CardInfo> entry : new ArrayList<>(result.entrySet())) {
            String uid = entry.getKey();
            String name = prefs.getString(uid, "");
            if (!TextUtils.isEmpty(name)) {
                result.put(uid, new CardInfo(uid, name));
            }
        }

        Log.d(TAG, "loadCards exit=" + list.code + " out=" + oneLine(list.out));
        String debug = list.code == 0 ? "" : "list exit=" + list.code + " " + oneLine(list.out);
        List<String> orderedUids = CardOrder.apply(this, new ArrayList<>(result.keySet()));
        List<CardInfo> orderedCards = new ArrayList<>();
        for (String uid : orderedUids) {
            CardInfo card = result.get(uid);
            if (card != null) orderedCards.add(card);
        }
        return new CardsLoadResult(orderedCards, debug);
    }

    private void renderCards(String activeUid) {
        cardsList.removeAllViews();
        if (cards.isEmpty()) {
            cardsList.addView(emptyCard("Карты не найдены", "Проверь, что Card Emulator уже создал карты и конфиги."));
            return;
        }
        searchInput.setVisibility(cards.size() > 8 ? View.VISIBLE : View.GONE);
        String query = searchInput.getText().toString().trim().toLowerCase();
        List<CardInfo> visibleCards = new ArrayList<>();
        for (CardInfo card : cards.values()) {
            if (!query.isEmpty() && !card.name.toLowerCase().contains(query) &&
                    !card.uid.toLowerCase().contains(query)) continue;
            visibleCards.add(card);
        }
        for (int index = 0; index < visibleCards.size(); index++) {
            CardInfo card = visibleCards.get(index);
            cardsList.addView(cardView(card, card.uid.equalsIgnoreCase(activeUid)), lpTop(-1, -2, 10));
        }
    }

    private void renderAdvancedTools() {
        advancedTools.removeAllViews();
        if (!setting(SettingsActivity.ADVANCED, false)) return;
        LinearLayout box = panel();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.addView(text("Advanced mode", 18, 0xfff4f3fb, true));
        box.addView(text("Инструменты для обычных NDEF-меток", 14, 0xffaaa7b4, false), lpTop(-1, -2, 5));
        Button write = glassButton("Записать NDEF на метку");
        write.setOnClickListener(v -> showNdefWriteDialog());
        box.addView(write, lpTop(-1, dp(44), 10));
        advancedTools.addView(box);
    }

    private View cardView(CardInfo card, boolean active) {
        LinearLayout box = panel();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row);

        TextView avatar = text(initials(card.name), 18, 0xfff2f0ff, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(active ? 0xff786cff : 0xff3a3a3f, 22, active ? 0x997d72ff : 0x44ffffff));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(text(card.name, 20, 0xfff4f3fb, true));
        labels.addView(text(card.uid, 14, 0xff8d8b94, false));

        TextView badge = text(active ? "Активна" : "Готова", 13, active ? 0xffd9d2ff : 0xffaaa7b4, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), 0, dp(12), 0);
        badge.setBackground(round(active ? 0x337c6dff : 0x22333338, 14, active ? 0x887c6dff : 0x33ffffff));
        row.addView(badge, new LinearLayout.LayoutParams(-2, dp(34)));

        box.setTag(card.uid);
        if (reorderMode) {
            TextView drag = text("≡", 26, 0xffd9d2ff, true);
            drag.setGravity(Gravity.CENTER);
            drag.setContentDescription("Перетащи карту");
            row.addView(drag, new LinearLayout.LayoutParams(dp(46), dp(38)));
            box.setOnTouchListener((view, event) -> handleCardTouch(box, event));
            return box;
        }

        Button button = glassButton(active ? "Перезапустить NFC" : "Выбрать");
        button.setOnClickListener(v -> switchCard(card));
        box.addView(button, lpTop(-1, dp(46), 14));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(tools, lpTop(-1, dp(42), 8));
        Button edit = glassButton("Редактировать");
        edit.setOnClickListener(v -> showAddDialog(card.uid, card.name, card.uid, ""));
        tools.addView(edit, new LinearLayout.LayoutParams(0, -1, 1));
        Button delete = glassButton("Удалить");
        delete.setTextColor(0xffff9f9f);
        delete.setOnClickListener(v -> confirmDelete(card));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, -1, 1);
        deleteLp.leftMargin = dp(8);
        tools.addView(delete, deleteLp);

        String details = getSharedPreferences(DETAILS_STORE, MODE_PRIVATE).getString(card.uid, "");
        if (!TextUtils.isEmpty(details)) {
            Button detailsButton = glassButton("Данные карты");
            detailsButton.setOnClickListener(v -> showCardDetails(card.uid, details));
            box.addView(detailsButton, lpTop(-1, dp(42), 8));
        }
        return box;
    }

    private boolean handleCardTouch(View box, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                draggedCard = box;
                box.setAlpha(0.68f);
                cardsList.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                moveDraggedCard(event.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                box.setAlpha(1f);
                cardsList.getParent().requestDisallowInterceptTouchEvent(false);
                saveRenderedCardOrder();
                draggedCard = null;
                return true;
            default:
                return false;
        }
    }

    private void moveDraggedCard(float rawY) {
        if (draggedCard == null) return;
        int from = cardsList.indexOfChild(draggedCard);
        for (int index = 0; index < cardsList.getChildCount(); index++) {
            View child = cardsList.getChildAt(index);
            if (child == draggedCard) continue;
            int[] location = new int[2];
            child.getLocationOnScreen(location);
            float middle = location[1] + child.getHeight() / 2f;
            if ((index < from && rawY < middle) || (index > from && rawY > middle)) {
                cardsList.removeView(draggedCard);
                cardsList.addView(draggedCard, index);
                return;
            }
        }
    }

    private void saveRenderedCardOrder() {
        List<String> order = new ArrayList<>();
        for (int index = 0; index < cardsList.getChildCount(); index++) {
            Object tag = cardsList.getChildAt(index).getTag();
            if (tag != null) order.add(String.valueOf(tag));
        }
        if (!order.isEmpty()) CardOrder.save(this, order);
    }

    private void showAddDialog(String initialUid, String initialName) {
        showAddDialog(initialUid, initialName, "", "");
    }

    private void showAddDialog(String initialUid, String initialName, String originalUid, String details) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), 0);
        EditText name = new EditText(this);
        name.setHint("Название");
        name.setText(initialName);
        form.addView(name);
        EditText uid = new EditText(this);
        uid.setHint("UID: CA:36:FD:C0");
        uid.setText(initialUid);
        uid.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        form.addView(uid);
        new AlertDialog.Builder(this)
                .setTitle(TextUtils.isEmpty(initialUid) ? "Новая карта" : "Редактировать карту")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) ->
                        saveCard(uid.getText().toString(), name.getText().toString(), originalUid, details))
                .show();
    }

    private void saveCard(String rawUid, String rawName, String originalUid, String details) {
        String uid = normalizeUid(rawUid);
        String name = rawName.trim();
        if (!isValidUid(uid)) {
            appendLog("Некорректный UID. Используй формат CA:36:FD:C0.");
            return;
        }
        if (name.isEmpty()) name = uid;
        SharedPreferences.Editor names = getSharedPreferences(STORE, MODE_PRIVATE).edit().putString(uid, name);
        SharedPreferences.Editor savedDetails = getSharedPreferences(DETAILS_STORE, MODE_PRIVATE).edit();
        if (!TextUtils.isEmpty(details)) savedDetails.putString(uid, details);
        if (!TextUtils.isEmpty(originalUid) && !uid.equals(originalUid)) {
            names.remove(originalUid);
            savedDetails.remove(originalUid);
        }
        names.apply();
        savedDetails.apply();
        String finalName = name;
        io.execute(() -> {
            ShellResult result = createConfigs(uid);
            if (result.code == 0 && !TextUtils.isEmpty(originalUid) && !uid.equals(originalUid)) {
                runRoot("rm -rf '" + CARDS + "/" + originalUid + "'");
            }
            main.post(() -> {
                appendLog(result.code == 0 ? "Сохранена карта: " + finalName : "Ошибка сохранения: " + oneLine(result.err));
                refresh();
            });
        });
    }

    private ShellResult createConfigs(String uid) {
        String suffix = toConfigSuffix(uid);
        String dst = CARDS + "/" + uid;
        String cmd =
                "mkdir -p '" + dst + "'; " +
                "for f in libnfc-hal-st.conf libnfc-hal-st-st54j.conf; do " +
                "src='" + MODULE + "'/$f; " +
                "sed 's/33,[^}]*/" + suffix + "/' \"$src\" > '" + dst + "'/$f; " +
                "done; " +
                "chmod 0600 '" + dst + "'/*";
        return runRoot(cmd);
    }

    private void confirmDelete(CardInfo card) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить карту?")
                .setMessage(card.name + "\n" + card.uid)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    getSharedPreferences(STORE, MODE_PRIVATE).edit().remove(card.uid).apply();
                    getSharedPreferences(DETAILS_STORE, MODE_PRIVATE).edit().remove(card.uid).apply();
                    io.execute(() -> {
                        runRoot("rm -rf '" + CARDS + "/" + card.uid + "'");
                        main.post(() -> {
                            appendLog("Удалена карта: " + card.name);
                            refresh();
                        });
                    });
                }).show();
    }

    private void startScan() {
        if (scanning || nfcAdapter == null) return;
        scanning = true;
        scanButton.setEnabled(false);
        showProgress("Включаю считывание...");
        modeView.setText("Режим: считывание · ожидание карты");
        activeUidView.setText("UID: считывание...");
        appendLog(pendingNdefWrite == null
                ? "Включаю чтение. Приложи физическую карту..."
                : "Включаю запись. Приложи NDEF-метку...");
        io.execute(() -> {
            setPollingMask("0x0F");
            restartNfc();
            main.postDelayed(() -> {
                nfcAdapter = NfcAdapter.getDefaultAdapter(this);
                if (nfcAdapter != null && scanning) {
                    registerScanHandlers();
                    main.postDelayed(() -> {
                        if (!scanning) return;
                        registerScanHandlers();
                        main.postDelayed(scanTimeout, scanTimeoutMs());
                        hideProgress();
                    }, 1400);
                }
            }, 1600);
        });
    }

    private void registerScanHandlers() {
        try {
            nfcAdapter.enableReaderMode(this, this::onTagRead,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V,
                    null);
            enableForegroundScan();
        } catch (Exception e) {
            Log.w(TAG, "NFC scan handler retry needed", e);
        }
    }

    private void onTagRead(Tag tag) {
        if (!scanning) return;
        scanning = false;
        main.removeCallbacks(scanTimeout);
        String uid = bytesToUid(tag.getId());
        io.execute(() -> {
            if (pendingNdefWrite != null) {
                String result = writeNdef(tag, pendingNdefWrite);
                pendingNdefWrite = null;
                main.post(() -> {
                    disableReaderMode();
                    appendLog(result);
                    hideProgress();
                });
                restoreListenMode();
                return;
            }
            String details = readTagDetails(tag);
            main.post(() -> {
                disableReaderMode();
                appendLog("Считана карта: " + uid);
                showScannedCard(uid, details);
            });
            restoreListenMode();
        });
    }

    private void showNdefWriteDialog() {
        if (!setting(SettingsActivity.ADVANCED, false)) return;
        EditText value = new EditText(this);
        value.setHint("Текст или https://example.com");
        value.setSingleLine(false);
        value.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle("Записать NDEF")
                .setMessage("Запись заменит текущую NDEF-запись на физической метке.")
                .setView(value)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Продолжить", (dialog, which) -> {
                    String raw = value.getText().toString().trim();
                    if (raw.isEmpty()) return;
                    NdefRecord record = raw.matches("(?i)^https?://.*")
                            ? NdefRecord.createUri(raw)
                            : NdefRecord.createTextRecord("ru", raw);
                    pendingNdefWrite = new NdefMessage(new NdefRecord[]{record});
                    startScan();
                })
                .show();
    }

    private String writeNdef(Tag tag, NdefMessage message) {
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) return "Метка защищена от записи.";
                if (message.toByteArray().length > ndef.getMaxSize()) return "Запись не помещается на метку.";
                ndef.writeNdefMessage(message);
                ndef.close();
                return "NDEF успешно записан.";
            }
            NdefFormatable formatable = NdefFormatable.get(tag);
            if (formatable != null) {
                formatable.connect();
                formatable.format(message);
                formatable.close();
                return "Метка отформатирована, NDEF записан.";
            }
            return "Эта метка не поддерживает запись NDEF.";
        } catch (Exception e) {
            return "Ошибка записи NDEF: " + oneLine(e.getMessage());
        }
    }

    private void disableReaderMode() {
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
            nfcAdapter.disableForegroundDispatch(this);
        }
        scanButton.setEnabled(true);
    }

    private void enableForegroundScan() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        IntentFilter tag = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, new IntentFilter[]{tag}, null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) onTagRead(tag);
        }
    }

    private void enterEmulationMode() {
        scanning = false;
        main.removeCallbacks(scanTimeout);
        disableReaderMode();
        modeView.setText("Режим: эмуляция");
        showProgress("Включаю эмуляцию...");
        appendLog("Включаю режим эмуляции...");
        restoreListenMode();
        main.postDelayed(this::refresh, 2500);
    }

    private void restoreListenMode() {
        io.execute(() -> {
            setPollingMask("0x00");
            restartNfc();
            String active = readActiveUid();
            main.post(() -> {
                modeView.setText("Режим: эмуляция");
                activeUidView.setText("UID: " + active);
                hideProgress();
            });
        });
    }

    private String readTagDetails(Tag tag) {
        StringBuilder out = new StringBuilder();
        out.append("UID: ").append(bytesToUid(tag.getId())).append('\n');
        out.append("Технологии:\n");
        for (String tech : tag.getTechList()) out.append("• ").append(shortTech(tech)).append('\n');
        try {
            NfcA a = NfcA.get(tag);
            if (a != null) {
                out.append("\nNFC-A\nATQA: ").append(hex(a.getAtqa()));
                out.append("\nSAK: ").append(String.format("0x%02X", a.getSak()));
            }
            NfcB b = NfcB.get(tag);
            if (b != null) {
                out.append("\n\nNFC-B\nApplication data: ").append(hex(b.getApplicationData()));
                out.append("\nProtocol info: ").append(hex(b.getProtocolInfo()));
            }
            NfcF f = NfcF.get(tag);
            if (f != null) {
                out.append("\n\nNFC-F\nManufacturer: ").append(hex(f.getManufacturer()));
                out.append("\nSystem code: ").append(hex(f.getSystemCode()));
            }
            NfcV v = NfcV.get(tag);
            if (v != null) {
                out.append("\n\nNFC-V\nDSFID: ").append(String.format("0x%02X", v.getDsfId()));
                out.append("\nResponse flags: ").append(String.format("0x%02X", v.getResponseFlags()));
            }
            IsoDep iso = IsoDep.get(tag);
            if (iso != null) {
                out.append("\n\nISO-DEP\nHistorical bytes: ").append(hex(iso.getHistoricalBytes()));
                out.append("\nHi-layer response: ").append(hex(iso.getHiLayerResponse()));
                out.append("\nMax transceive: ").append(iso.getMaxTransceiveLength()).append(" bytes");
            }
            MifareClassic classic = MifareClassic.get(tag);
            if (classic != null) {
                out.append("\n\nMIFARE Classic\nType: ").append(mifareClassicType(classic.getType()));
                out.append("\nРазмер: ").append(classic.getSize()).append(" bytes");
                out.append("\nСекторы: ").append(classic.getSectorCount());
                out.append("\nБлоки: ").append(classic.getBlockCount());
            }
            MifareUltralight ultralight = MifareUltralight.get(tag);
            if (ultralight != null) {
                out.append("\n\nMIFARE Ultralight\nType: ").append(mifareUltralightType(ultralight.getType()));
            }
            Ndef ndef = Ndef.get(tag);
            if (ndef != null && setting(SettingsActivity.READ_NDEF, true)) {
                ndef.connect();
                NdefMessage message = ndef.getNdefMessage();
                out.append("\n\nNDEF\nType: ").append(ndef.getType());
                out.append("\nMax size: ").append(ndef.getMaxSize()).append(" bytes");
                out.append("\nWritable: ").append(ndef.isWritable() ? "да" : "нет");
                out.append("\nRecords: ").append(message == null ? 0 : message.getRecords().length);
                if (message != null) out.append("\nPayload (hex): ").append(hex(message.toByteArray()));
                ndef.close();
            }
        } catch (Exception e) {
            out.append("\n\nЧасть данных недоступна: ").append(oneLine(e.getMessage()));
        }
        return out.toString().trim();
    }

    private void showCardDetails(String uid, String details) {
        showCardDetails(uid, details, null, "Закрыть");
    }

    private void showScannedCard(String uid, String details) {
        showCardDetails(uid, details, () -> showAddDialog(uid, "", "", details), "Добавить карту");
    }

    private void showCardDetails(String uid, String details, Runnable onConfirm, String confirmLabel) {
        TextView body = text(details, 14, 0xffe4e2eb, false);
        body.setTextIsSelectable(true);
        body.setPadding(dp(18), dp(8), dp(18), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle("Данные карты " + uid)
                .setView(scroll)
                .setPositiveButton(confirmLabel, (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton(onConfirm == null ? null : "Закрыть", null)
                .show();
    }

    private String shortTech(String tech) {
        int index = tech.lastIndexOf('.');
        return index < 0 ? tech : tech.substring(index + 1);
    }

    private String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "нет";
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            if (out.length() > 0) out.append(' ');
            out.append(String.format("%02X", b & 0xff));
        }
        return out.toString();
    }

    private String mifareClassicType(int type) {
        if (type == MifareClassic.TYPE_CLASSIC) return "Classic";
        if (type == MifareClassic.TYPE_PLUS) return "Plus";
        if (type == MifareClassic.TYPE_PRO) return "Pro";
        return "Unknown";
    }

    private String mifareUltralightType(int type) {
        if (type == MifareUltralight.TYPE_ULTRALIGHT) return "Ultralight";
        if (type == MifareUltralight.TYPE_ULTRALIGHT_C) return "Ultralight C";
        return "Unknown";
    }

    private void setPollingMask(String mask) {
        runRoot("sed -i 's/POLLING_TECH_MASK=.*/POLLING_TECH_MASK=" + mask + "/' " +
                MODULE + "/libnfc-nci.conf " + MODULE + "/libnfc-nci-felica.conf");
    }

    private void restartNfc() {
        runRoot("cmd nfc disable-nfc >/dev/null 2>&1; " +
                "pid=$(pidof com.android.nfc 2>/dev/null); [ -n \"$pid\" ] && kill -9 \"$pid\"; " +
                "oldhal=$(pidof android.hardware.nfc-service-st 2>/dev/null); [ -n \"$oldhal\" ] && kill -9 \"$oldhal\"; " +
                "i=0; while [ $i -lt 20 ]; do hal=$(pidof android.hardware.nfc-service-st 2>/dev/null); " +
                "[ -n \"$hal\" ] && [ \"$hal\" != \"$oldhal\" ] && break; sleep 0.2; i=$((i+1)); done; " +
                "cmd nfc enable-nfc >/dev/null 2>&1; " +
                "i=0; while [ $i -lt 25 ]; do pidof com.android.nfc >/dev/null 2>&1 && " +
                "dumpsys nfc 2>/dev/null | grep -q '^mState=on' && break; sleep 0.2; i=$((i+1)); done");
    }

    private View emptyCard(String title, String body) {
        LinearLayout box = panel();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.addView(text(title, 18, 0xfff4f3fb, true));
        box.addView(text(body, 14, 0xffaaa7b4, false), lpTop(-1, -2, 8));
        return box;
    }

    private void switchCard(CardInfo card) {
        setBusy(true);
        showProgress("Переключаю на " + card.name + "...");
        appendLog("Переключаю на " + card.name + " (" + card.uid + ")...");
        io.execute(() -> {
            String uid = card.uid;
            String cmd =
                    "src='" + CARDS + "/" + uid + "'; " +
                    "mod='" + MODULE + "'; " +
                    "[ -f \"$src/libnfc-hal-st.conf\" ] || exit 2; " +
                    "cp \"$src/libnfc-hal-st.conf\" \"$mod/libnfc-hal-st.conf\"; " +
                    "cp \"$src/libnfc-hal-st-st54j.conf\" \"$mod/libnfc-hal-st-st54j.conf\"; " +
                    "chown root:root \"$mod/libnfc-hal-st.conf\" \"$mod/libnfc-hal-st-st54j.conf\"; " +
                    "chmod 0644 \"$mod/libnfc-hal-st.conf\" \"$mod/libnfc-hal-st-st54j.conf\"; " +
                    "echo '" + uid + "' > " + STATE + "; " +
                    "sync; " +
                    "cmd nfc disable-nfc >/dev/null 2>&1; " +
                    "pid=$(pidof com.android.nfc 2>/dev/null); [ -n \"$pid\" ] && kill -9 \"$pid\"; " +
                    "oldhal=$(pidof android.hardware.nfc-service-st 2>/dev/null); [ -n \"$oldhal\" ] && kill -9 \"$oldhal\"; " +
                    "i=0; while [ $i -lt 20 ]; do hal=$(pidof android.hardware.nfc-service-st 2>/dev/null); " +
                    "[ -n \"$hal\" ] && [ \"$hal\" != \"$oldhal\" ] && break; sleep 0.2; i=$((i+1)); done; " +
                    "cmd nfc enable-nfc >/dev/null 2>&1; " +
                    "i=0; while [ $i -lt 25 ]; do pidof com.android.nfc >/dev/null 2>&1 && " +
                    "dumpsys nfc 2>/dev/null | grep -q '^mState=on' && break; sleep 0.2; i=$((i+1)); done";
            ShellResult result = runRoot(cmd);
            String active = readActiveUid();
            main.post(() -> {
                appendLog(result.code == 0
                        ? "Готово. Активный UID: " + active
                        : "Ошибка переключения: " + oneLine(result.err + " " + result.out));
                activeUidView.setText("UID: " + (active.isEmpty() ? "не найден" : active));
                lastActiveUid = active;
                renderCards(active);
                setBusy(false);
                hideProgress();
            });
        });
    }

    private String readActiveUid() {
        ShellResult r = runRoot("sed -n 's/.*33,04,\\([^}]*\\)}.*/\\1/p' " + MODULE + "/libnfc-hal-st.conf | tail -n 1");
        String raw = r.out.trim().replace(",", ":").replace(" ", "").toUpperCase();
        return raw.matches("[0-9A-F]{2}(:[0-9A-F]{2}){3,9}") ? raw : "";
    }

    private String normalizeUid(String uid) {
        String value = uid == null ? "" : uid.trim().toUpperCase().replace("-", ":").replace(" ", ":");
        if (!value.contains(":") && value.matches("[0-9A-F]+") && value.length() % 2 == 0) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < value.length(); i += 2) {
                if (out.length() > 0) out.append(':');
                out.append(value, i, i + 2);
            }
            value = out.toString();
        }
        return value;
    }

    private boolean isValidUid(String uid) {
        return uid.matches("[0-9A-F]{2}(:[0-9A-F]{2}){3,9}");
    }

    private String toConfigSuffix(String uid) {
        String[] bytes = uid.split(":");
        return "33," + String.format("%02X", bytes.length) + "," + uid.replace(':', ',');
    }

    private String bytesToUid(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            if (out.length() > 0) out.append(':');
            out.append(String.format("%02X", b & 0xff));
        }
        return out.toString();
    }

    private ShellResult runRoot(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(12, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ShellResult(124, "", "timeout");
            }
            String out = read(process.getInputStream());
            return new ShellResult(process.exitValue(), out, "");
        } catch (Exception e) {
            return new ShellResult(1, "", e.getMessage() == null ? "unknown error" : e.getMessage());
        }
    }

    private String read(java.io.InputStream stream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private void setBusy(boolean busy) {
        refreshButton.setEnabled(!busy);
        refreshButton.setAlpha(busy ? 0.55f : 1f);
    }

    private void openWalletSettings() {
        try {
            startActivity(new Intent("android.settings.NFC_PAYMENT_SETTINGS"));
        } catch (Exception e) {
            appendLog("Системные настройки Wallet недоступны.");
        }
    }

    private void showProgress(String message) {
        progressText.setText(message);
        progressOverlay.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        progressOverlay.setVisibility(View.GONE);
    }

    private boolean setting(String key, boolean defaultValue) {
        return getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE).getBoolean(key, defaultValue);
    }

    private long scanTimeoutMs() {
        return getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
                .getInt(SettingsActivity.SCAN_TIMEOUT, 45) * 1000L;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (advancedTools != null) renderAdvancedTools();
    }

    private void appendLog(String line) {
        String current = logView.getText().toString();
        if ("Готов.".equals(current)) current = "";
        logView.setText((line + "\n" + current).trim());
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(true);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button glassButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(0xfff2f0ff);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(0x22333338, 14, 0x447c6dff));
        return button;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setBackground(round(0xaa242427, 22, 0x33ffffff));
        return panel;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private GradientDrawable makeBackground() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xff171719, 0xff202024, 0xff111113});
        return d;
    }

    private LinearLayout.LayoutParams lpTop(int w, int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String initials(String name) {
        if (TextUtils.isEmpty(name)) return "N";
        String trimmed = name.trim();
        if (trimmed.length() == 1) return trimmed.toUpperCase();
        return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase();
    }

    private String oneLine(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static class CardInfo {
        final String uid;
        final String name;

        CardInfo(String uid, String name) {
            this.uid = uid;
            this.name = name;
        }
    }

    private static class CardsLoadResult {
        final List<CardInfo> cards;
        final String debug;

        CardsLoadResult(List<CardInfo> cards, String debug) {
            this.cards = cards;
            this.debug = debug;
        }
    }

    private static class ShellResult {
        final int code;
        final String out;
        final String err;

        ShellResult(int code, String out, String err) {
            this.code = code;
            this.out = out == null ? "" : out;
            this.err = err == null ? "" : err;
        }
    }
}
