package dev.codex.nfcswitcher;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NfcSwitcherApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Set<Activity> resumed = new HashSet<>();
    private int modeGeneration;
    private final Runnable enterDefaultReader = () -> {
        if (!resumed.isEmpty()) return;
        requestMode("0x0F");
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    private void requestMode(String mask) {
        int generation = ++modeGeneration;
        io.execute(() -> {
            if (generation != modeGeneration) return;
            NfcRootController.setPollingMode(mask);
        });
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumed.add(activity);
        main.removeCallbacks(enterDefaultReader);
        requestMode("0x00");
    }

    @Override
    public void onActivityPaused(Activity activity) {
        resumed.remove(activity);
        main.removeCallbacks(enterDefaultReader);
        main.postDelayed(enterDefaultReader, 650);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
