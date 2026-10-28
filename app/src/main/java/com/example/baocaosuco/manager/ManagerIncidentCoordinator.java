package com.example.baocaosuco.manager;

import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.CommandPrecondition;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * B03/B04 Java coordinator backed only by the secure Manager Repository API.
 * Filtering and sorting are explicitly limited to the last server-fetched page
 * (maximum 50); no fake global counts, Manager get-by-id or history endpoint.
 */
public final class ManagerIncidentCoordinator {
    public interface View {
        void onBusy(boolean busy);
        void onRows(List<IncidentSnapshot> rows);
        void onDetail(IncidentSnapshot selected);
        void onSaved(IncidentSnapshot incident);
        void onError(IncidentRepository.Error error);
    }

    private final IncidentRepository.Manager repository;
    private final View view;
    private List<IncidentSnapshot> loaded = Collections.emptyList();
    private String statusFilter = "ALL";
    private boolean dueFirst;
    private boolean active = true;
    private boolean pending;
    private int generation;

    public ManagerIncidentCoordinator(IncidentRepository.Manager repository, View view) {
        if (repository == null || view == null) throw new IllegalArgumentException("Repository and View required");
        this.repository = repository;
        this.view = view;
    }

    public void close() {
        active = false;
        generation++;
        pending = false;
        loaded = Collections.emptyList();
    }

    private int begin() {
        if (!active || pending) return -1;
        pending = true;
        int token = ++generation;
        view.onBusy(true);
        return token;
    }

    private boolean current(int token) {
        return active && pending && generation == token;
    }

    private void finish(int token) {
        if (!current(token)) return;
        pending = false;
        view.onBusy(false);
    }

    private void failure(int token, IncidentRepository.Error error) {
        if (!current(token)) return;
        finish(token);
        view.onError(error == null ? IncidentRepository.Error.UNKNOWN : error);
    }

    private void invalid(IncidentRepository.Error error) {
        if (active && !pending) view.onError(error);
    }

    public boolean loadList() {
        final int token = begin();
        if (token < 0) return false;
        repository.listAll(new IncidentRepository.Callback<List<IncidentSnapshot>>() {
            @Override public void onSuccess(List<IncidentSnapshot> rows) {
                if (!current(token)) return;
                if (rows == null) { failure(token, IncidentRepository.Error.UNKNOWN); return; }
                loaded = Collections.unmodifiableList(new ArrayList<>(rows));
                finish(token);
                publishRows();
            }
            @Override public void onError(IncidentRepository.Error error) { failure(token, error); }
        });
        return true;
    }

    /** B03: local page filtering only; does not issue an unbounded Firestore query. */
    public boolean setStatusFilter(String status) {
        if (!validFilter(status)) { invalid(IncidentRepository.Error.VALIDATION); return false; }
        if (!active) return false;
        statusFilter = status;
        if (!pending) publishRows();
        return true;
    }

    public void setDueDateFirst(boolean value) {
        if (!active) return;
        dueFirst = value;
        if (!pending) publishRows();
    }

    private static boolean validFilter(String status) {
        if ("ALL".equals(status)) return true;
        if (status == null) return false;
        for (IncidentStatus candidate : IncidentStatus.values()) {
            if (candidate.name().equals(status)) return true;
        }
        return false;
    }

    private void publishRows() {
        if (!active) return;
        List<IncidentSnapshot> page = new ArrayList<>();
        for (IncidentSnapshot incident : loaded) {
            if ("ALL".equals(statusFilter) || statusFilter.equals(incident.getStatusCode()))
                page.add(incident);
        }
        if (dueFirst) {
            page.sort(Comparator.comparingLong(IncidentSnapshot::getDueAtMillis)
                    .thenComparing(IncidentSnapshot::getId));
        } else {
            page.sort(Comparator.comparingLong(IncidentSnapshot::getCreatedAtMillis).reversed()
                    .thenComparing(IncidentSnapshot::getId));
        }
        view.onRows(Collections.unmodifiableList(page));
    }

    /** B04: detail comes from an explicitly selected row of the loaded Manager page. */
    public boolean select(String incidentId) {
        if (!active || pending || incidentId == null) return false;
        for (IncidentSnapshot row : loaded) {
            if (incidentId.equals(row.getId())) {
                view.onDetail(row);
                return true;
            }
        }
        invalid(IncidentRepository.Error.NOT_FOUND);
        return false;
    }

    public boolean assign(IncidentSnapshot observed, String handler) {
        if (observed == null || observed.getStatus() != IncidentStatus.NEW
                || handler == null || handler.trim().isEmpty()
                || handler.trim().codePointCount(0, handler.trim().length()) > 120) {
            invalid(IncidentRepository.Error.VALIDATION);
            return false;
        }
        return transition(observed, "ASSIGN", handler.trim());
    }

    public boolean start(IncidentSnapshot observed) {
        if (observed == null || observed.getStatus() != IncidentStatus.ASSIGNED) {
            invalid(IncidentRepository.Error.VALIDATION);
            return false;
        }
        return transition(observed, "START", null);
    }

    public boolean complete(IncidentSnapshot observed) {
        if (observed == null || observed.getStatus() != IncidentStatus.IN_PROGRESS) {
            invalid(IncidentRepository.Error.VALIDATION);
            return false;
        }
        return transition(observed, "COMPLETE", null);
    }

    private boolean transition(IncidentSnapshot observed, String action, String handler) {
        // Never act on a snapshot outside the current Manager page.
        boolean loadedAndCurrent = false;
        for (IncidentSnapshot row : loaded) {
            if (row.getId().equals(observed.getId()) &&
                    row.getRevision() == observed.getRevision() &&
                    row.getStatus() == observed.getStatus()) {
                loadedAndCurrent = true;
                break;
            }
        }
        if (!loadedAndCurrent) { invalid(IncidentRepository.Error.CONFLICT); return false; }
        final int token = begin();
        if (token < 0) return false;
        CommandPrecondition expected = new CommandPrecondition(
                observed.getStatusCode(), observed.getRevision(), UUID.randomUUID().toString());
        IncidentRepository.Callback<IncidentSnapshot> callback =
                new IncidentRepository.Callback<IncidentSnapshot>() {
            @Override public void onSuccess(IncidentSnapshot updated) {
                if (!current(token)) return;
                if (updated == null || !observed.getId().equals(updated.getId())) {
                    failure(token, IncidentRepository.Error.SECURITY_NOT_READY);
                    return;
                }
                List<IncidentSnapshot> next = new ArrayList<>(loaded);
                for (int i = 0; i < next.size(); i++) {
                    if (next.get(i).getId().equals(updated.getId())) {
                        next.set(i, updated);
                        break;
                    }
                }
                loaded = Collections.unmodifiableList(next);
                finish(token);
                view.onSaved(updated);
                publishRows();
            }
            @Override public void onError(IncidentRepository.Error error) { failure(token, error); }
        };
        switch (action) {
            case "ASSIGN": repository.assign(observed.getId(), expected, handler, callback); break;
            case "START": repository.start(observed.getId(), expected, callback); break;
            case "COMPLETE": repository.complete(observed.getId(), expected, callback); break;
            default: failure(token, IncidentRepository.Error.VALIDATION); return false;
        }
        return true;
    }

    /** LOG SLA is derived from createdAt; CLOSED/COMPLETED incidents are not overdue. */
    public static boolean isOverdue(IncidentSnapshot item, long nowMillis) {
        return item != null && nowMillis > item.getDueAtMillis()
                && (item.getStatus() == IncidentStatus.NEW
                || item.getStatus() == IncidentStatus.ASSIGNED
                || item.getStatus() == IncidentStatus.IN_PROGRESS);
    }
}
