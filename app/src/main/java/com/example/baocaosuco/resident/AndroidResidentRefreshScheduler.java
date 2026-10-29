package com.example.baocaosuco.resident;

import android.os.Handler;
import android.os.Looper;

/**
 * Main-thread only timer for ResidentForegroundStatusRefresh.
 * All calls (including onStart/onStop/close) must be on the UI thread.
 */
public final class AndroidResidentRefreshScheduler implements ResidentForegroundStatusRefresh.Scheduler {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void postDelayed(Runnable work, long delayMillis) {
        handler.postDelayed(work, delayMillis);
    }

    @Override public void removeCallbacks(Runnable work) {
        handler.removeCallbacks(work);
    }
}
