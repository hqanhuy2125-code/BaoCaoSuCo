package com.example.baocaosuco.resident;

import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.IncidentRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B06 foreground-only status refresh, using the existing CON-03 Resident.listMine.
 *
 * This does NOT create a Firestore listener, push notification, new Repository API,
 * background worker or business-state transition. Call onStart() from the visible
 * Resident screen and onStop() when it is no longer visible; close() on destruction
 * or sign-out. All methods/callbacks must run on the Android main thread.
 *
 * Each poll reads at most the Repository's existing 20 newest incidents. Changes
 * outside that page are NOT monitored. No claim of instantaneous notification.
 */
public final class ResidentForegroundStatusRefresh {
    public static final long POLL_INTERVAL_MS = 60_000L;

    public interface Scheduler {
        void postDelayed(Runnable work, long delayMillis);
        void removeCallbacks(Runnable work);
    }

    public interface View {
        /** Current server-owned visible page; never fabricate a status. */
        void onPage(List<IncidentSnapshot> incidents);
        /** Existing item changed status at a strictly higher revision. */
        void onStatusChanged(IncidentSnapshot incident);
        void onError(IncidentRepository.Error error);
    }

    private final IncidentRepository.Resident repository;
    private final Scheduler scheduler;
    private final View view;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible || closed) return;
            refresh();
            scheduleNext();
        }
    };

    private Map<String, IncidentSnapshot> previous = Collections.emptyMap();
    private boolean hasPage;
    private boolean visible;
    private boolean closed;
    private boolean inFlight;
    private int generation;

    public ResidentForegroundStatusRefresh(IncidentRepository.Resident repository,
                                           Scheduler scheduler, View view) {
        if (repository == null || scheduler == null || view == null)
            throw new IllegalArgumentException("Repository, Scheduler and View required");
        this.repository = repository;
        this.scheduler = scheduler;
        this.view = view;
    }

    /**
     * Start or resume screen-only polling. The first result is a baseline, not
     * a notification about an event that may have happened while off-screen.
     */
    public void onStart() {
        if (closed || visible) return;
        visible = true;
        generation++;
        hasPage = false;
        previous = Collections.emptyMap();
        refresh();
        scheduleNext();
    }

    /** Detach timer, discard private data and reject late callbacks on stop. */
    public void onStop() {
        if (!visible) return;
        visible = false;
        generation++;
        scheduler.removeCallbacks(tick);
        inFlight = false;
        previous = Collections.emptyMap();
        hasPage = false;
    }

    public void close() {
        if (closed) return;
        onStop();
        closed = true;
        generation++;
    }

    /** Optional pull-to-refresh hook. No overlapping reads are allowed. */
    public boolean refresh() {
        if (closed || !visible || inFlight) return false;
        inFlight = true;
        final int epoch = generation;
        repository.listMine(new IncidentRepository.Callback<List<IncidentSnapshot>>() {
            @Override public void onSuccess(List<IncidentSnapshot> incidents) {
                if (!accept(epoch)) return;
                inFlight = false;
                if (incidents == null) {
                    view.onError(IncidentRepository.Error.UNKNOWN);
                    return;
                }
                List<IncidentSnapshot> page = new ArrayList<>(incidents);
                Map<String, IncidentSnapshot> latest = new HashMap<>();
                for (IncidentSnapshot item : page) {
                    if (item == null || latest.put(item.getId(), item) != null) {
                        view.onError(IncidentRepository.Error.SECURITY_NOT_READY);
                        return;
                    }
                }
                List<IncidentSnapshot> changed = new ArrayList<>();
                if (hasPage) {
                    for (IncidentSnapshot item : page) {
                        IncidentSnapshot older = previous.get(item.getId());
                        if (older != null && item.getRevision() > older.getRevision()
                                && !item.getStatusCode().equals(older.getStatusCode())) {
                            changed.add(item);
                        }
                    }
                }
                // Publish fresh authoritative page first, then status notices.
                // Do not silently notify on the first load or on unchanged polls.
                if (!hasPage || !samePage(previous, latest)) {
                    view.onPage(Collections.unmodifiableList(page));
                    // A View callback may navigate away or sign out synchronously.
                    if (!accept(epoch)) return;
                }
                previous = latest;
                hasPage = true;
                for (IncidentSnapshot item : changed) {
                    if (!accept(epoch)) return;
                    view.onStatusChanged(item);
                }
            }

            @Override public void onError(IncidentRepository.Error error) {
                if (!accept(epoch)) return;
                inFlight = false;
                view.onError(error == null ? IncidentRepository.Error.UNKNOWN : error);
            }
        });
        return true;
    }

    private boolean accept(int epoch) {
        return !closed && visible && generation == epoch;
    }

    private void scheduleNext() {
        if (!closed && visible) {
            scheduler.removeCallbacks(tick);
            scheduler.postDelayed(tick, POLL_INTERVAL_MS);
        }
    }

    private static boolean samePage(Map<String, IncidentSnapshot> before,
                                    Map<String, IncidentSnapshot> after) {
        if (before.size() != after.size()) return false;
        for (Map.Entry<String, IncidentSnapshot> entry : after.entrySet()) {
            IncidentSnapshot older = before.get(entry.getKey());
            IncidentSnapshot newer = entry.getValue();
            if (older == null || older.getRevision() != newer.getRevision()
                    || !older.getStatusCode().equals(newer.getStatusCode())
                    || older.getUpdatedAtMillis() != newer.getUpdatedAtMillis()) {
                return false;
            }
        }
        return true;
    }
}
