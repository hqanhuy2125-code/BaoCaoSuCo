package com.example.baocaosuco.resident.photo;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.ComponentActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * B01 Java handoff. Create once in Activity.onCreate() before STARTED.
 * XML screen owner only wires buttons and preview; this class never writes
 * IncidentRepository, Firebase data, or another teammate's layout.
 */
public final class IncidentPhotoUiBridge {
    public interface Listener {
        void onBusy(boolean busy);
        void onPhotoReady(IncidentPhotoProcessor.PreparedPhoto photo);
        void onPhotoCleared();
        void onCancelled(IncidentPhotoController.Source source);
        void onPickerError(IncidentPhotoController.Source source,
                           IncidentPhotoController.Error error);
        void onProcessingError(IncidentPhotoProcessor.Failure error);
    }

    private final Listener listener;
    private final IncidentPhotoController controller;
    private final IncidentPhotoStore store;
    private final IncidentPhotoProcessor processor;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private IncidentPhotoProcessor.PreparedPhoto current;
    private boolean busy;
    private boolean closed;
    private long generation;

    public IncidentPhotoUiBridge(ComponentActivity activity, Listener listener) {
        if (activity == null || listener == null) {
            throw new IllegalArgumentException("Activity and Listener required");
        }
        this.listener = listener;
        store = new IncidentPhotoStore(activity);
        processor = new IncidentPhotoProcessor(activity, store);
        controller = new IncidentPhotoController(activity,
                new IncidentPhotoController.Listener() {
                    @Override public void onSelected(Uri uri,
                            IncidentPhotoController.Source source) {
                        processSelection(uri);
                    }
                    @Override public void onCancelled(
                            IncidentPhotoController.Source source) {
                        if (closed) return;
                        setBusy(false);
                        listener.onCancelled(source);
                    }
                    @Override public void onError(IncidentPhotoController.Source source,
                            IncidentPhotoController.Error error) {
                        if (closed) return;
                        setBusy(false);
                        listener.onPickerError(source, error);
                    }
                });
    }

    /** Buttons must remain disabled until the existing RESIDENT role gate passes. */
    public boolean capture() {
        if (closed || busy) return false;
        boolean started = controller.capture();
        if (started) setBusy(true);
        return started;
    }

    public boolean pickFromGallery() {
        if (closed || busy) return false;
        boolean started = controller.pickFromGallery();
        if (started) setBusy(true);
        return started;
    }

    private void processSelection(Uri uri) {
        if (closed) return;
        final long token = ++generation;
        setBusy(true);
        try {
            worker.execute(() -> {
                IncidentPhotoProcessor.PreparedPhoto prepared = null;
                IncidentPhotoProcessor.Failure failure = null;
                try {
                    prepared = processor.process(uri);
                } catch (IncidentPhotoProcessor.PhotoException error) {
                    failure = error.getFailure();
                } catch (RuntimeException error) {
                    failure = IncidentPhotoProcessor.Failure.IO_ERROR;
                }
                final IncidentPhotoProcessor.PreparedPhoto ready = prepared;
                final IncidentPhotoProcessor.Failure problem = failure;
                main.post(() -> {
                    if (closed || generation != token) {
                        if (ready != null) store.deletePrepared(ready.getUri());
                        return;
                    }
                    if (ready == null) {
                        setBusy(false);
                        listener.onProcessingError(problem == null
                                ? IncidentPhotoProcessor.Failure.IO_ERROR : problem);
                        return;
                    }
                    IncidentPhotoProcessor.PreparedPhoto previous = current;
                    current = ready;
                    setBusy(false);
                    try {
                        listener.onPhotoReady(ready);
                    } finally {
                        if (previous != null) store.deletePrepared(previous.getUri());
                    }
                });
            });
        } catch (RejectedExecutionException error) {
            setBusy(false);
            listener.onProcessingError(IncidentPhotoProcessor.Failure.IO_ERROR);
        }
    }

    public Uri currentUri() {
        return current == null ? null : current.getUri();
    }

    public boolean isBusy() {
        return busy;
    }

    /** Called by B01 remove button after a photo is selected. */
    public boolean clearSelected() {
        if (closed || busy || current == null) return false;
        Uri previous = current.getUri();
        current = null;
        store.deletePrepared(previous);
        listener.onPhotoCleared();
        return true;
    }

    private void setBusy(boolean next) {
        if (closed || busy == next) return;
        busy = next;
        listener.onBusy(next);
    }

    /** Logout/onDestroy; no callbacks or on-screen changes after closure. */
    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        controller.close();
        worker.shutdownNow();
        if (current != null) store.deletePrepared(current.getUri());
        current = null;
    }
}
