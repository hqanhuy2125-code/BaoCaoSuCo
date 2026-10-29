package com.example.baocaosuco.resident;

import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.repository.CommandPrecondition;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * B01/B02/B05 Java-to-View coordinator. Does not own XML, Firebase credentials,
 * authentication roles or LOG transitions. All authorization stays in Repository/Rules.
 * Call on the UI thread; production repository callbacks already return on main thread.
 */
public final class ResidentIncidentCoordinator {
    public interface View {
        void onBusy(boolean busy);
        void onList(List<IncidentSnapshot> incidents);
        void onDetail(IncidentSnapshot incident, List<IncidentHistorySnapshot> history);
        void onSaved(IncidentSnapshot incident);
        void onError(IncidentRepository.Error error);
    }

    private final IncidentRepository.Resident repository;
    private final View view;
    private boolean active = true;
    private boolean pending;
    private int generation;

    public ResidentIncidentCoordinator(IncidentRepository.Resident repository, View view) {
        if (repository == null || view == null) throw new IllegalArgumentException("Repository and View required");
        this.repository = repository;
        this.view = view;
    }

    /** Stops delivery to a destroyed Activity; safe to call more than once. */
    public void close() {
        active = false;
        generation++;
        pending = false;
    }

    private int begin() {
        if (!active || pending) return -1;
        pending = true;
        int token = ++generation;
        view.onBusy(true);
        // onBusy may synchronously close the screen; do not start work then.
        return current(token) ? token : -1;
    }

    private boolean current(int token) {
        return active && pending && generation == token;
    }

    private boolean canDeliver(int token) {
        // A View may synchronously close or start a new request on onBusy(false).
        return active && !pending && generation == token;
    }

    private void finish(int token) {
        if (!current(token)) return;
        pending = false;
        view.onBusy(false);
    }

    private void failure(int token, IncidentRepository.Error error) {
        if (!current(token)) return;
        finish(token);
        if (canDeliver(token)) view.onError(
                error == null ? IncidentRepository.Error.UNKNOWN : error);
    }

    private void invalid() {
        if (active && !pending) view.onError(IncidentRepository.Error.VALIDATION);
    }

    public boolean loadList() {
        final int token = begin();
        if (token < 0) return false;
        repository.listMine(new IncidentRepository.Callback<List<IncidentSnapshot>>() {
            @Override public void onSuccess(List<IncidentSnapshot> data) {
                if (!current(token)) return;
                if (data == null) { failure(token, IncidentRepository.Error.UNKNOWN); return; }
                List<IncidentSnapshot> snapshot = Collections.unmodifiableList(new ArrayList<>(data));
                finish(token);
                if (canDeliver(token)) view.onList(snapshot); // Repository already applies own-UID and 20-item limit.
            }
            @Override public void onError(IncidentRepository.Error error) { failure(token, error); }
        });
        return true;
    }

    /** B02: get own incident first, then nested history, both server-validated. */
    public boolean loadDetail(String incidentId) {
        if (incidentId == null || incidentId.trim().isEmpty() || incidentId.contains("/")) {
            invalid();
            return false;
        }
        final int token = begin();
        if (token < 0) return false;
        repository.getMine(incidentId, new IncidentRepository.Callback<IncidentSnapshot>() {
            @Override public void onSuccess(IncidentSnapshot incident) {
                if (!current(token)) return;
                if (incident == null || !incidentId.equals(incident.getId())) {
                    failure(token, IncidentRepository.Error.SECURITY_NOT_READY);
                    return;
                }
                repository.historyMine(incidentId,
                        new IncidentRepository.Callback<List<IncidentHistorySnapshot>>() {
                    @Override public void onSuccess(List<IncidentHistorySnapshot> history) {
                        if (!current(token)) return;
                        if (history == null) { failure(token, IncidentRepository.Error.UNKNOWN); return; }
                        List<IncidentHistorySnapshot> snapshot =
                                Collections.unmodifiableList(new ArrayList<>(history));
                        finish(token);
                        if (canDeliver(token)) view.onDetail(incident, snapshot);
                    }
                    @Override public void onError(IncidentRepository.Error error) { failure(token, error); }
                });
            }
            @Override public void onError(IncidentRepository.Error error) { failure(token, error); }
        });
        return true;
    }

    /** B01: only the four CON-01 fields are accepted; UID, role/status and times are server-owned. */
    public boolean submit(String title, String description, String location, String priority) {
        final NewIncidentInput input;
        try {
            input = new NewIncidentInput(title, description, location, priority);
        } catch (IllegalArgumentException badInput) {
            invalid();
            return false;
        }
        final int token = begin();
        if (token < 0) return false;
        repository.create(input, new IncidentRepository.Callback<IncidentSnapshot>() {
            @Override public void onSuccess(IncidentSnapshot incident) {
                if (!current(token)) return;
                if (incident == null) { failure(token, IncidentRepository.Error.UNKNOWN); return; }
                finish(token);
                if (canDeliver(token)) view.onSaved(incident);
            }
            @Override public void onError(IncidentRepository.Error error) { failure(token, error); }
        });
        return true;
    }

    /** B05: only a displayed COMPLETED snapshot may be confirmed/reopened. */
    public boolean confirm(IncidentSnapshot observed, boolean resolved) {
        if (observed == null || observed.getStatus() != IncidentStatus.COMPLETED) {
            invalid();
            return false;
        }
        final int token = begin();
        if (token < 0) return false;
        CommandPrecondition expected = new CommandPrecondition(
                observed.getStatusCode(), observed.getRevision(), UUID.randomUUID().toString());
        repository.confirm(observed.getId(), expected, resolved,
                new IncidentRepository.Callback<IncidentSnapshot>() {
            @Override public void onSuccess(IncidentSnapshot incident) {
                if (!current(token)) return;
                if (incident == null) { failure(token, IncidentRepository.Error.UNKNOWN); return; }
                finish(token);
                if (canDeliver(token)) view.onSaved(incident);
            }
            @Override public void onError(IncidentRepository.Error error) { failure(token, error); }
        });
        return true;
    }
}
