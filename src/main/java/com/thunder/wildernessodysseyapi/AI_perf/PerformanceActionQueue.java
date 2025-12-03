package com.thunder.wildernessodysseyapi.AI_perf;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tracks advisory actions that require human approval before execution.
 */
public class PerformanceActionQueue {
    private final List<PerformanceAction> actions = new ArrayList<>();

    public synchronized void replacePendingMitigations(List<PerformanceAction> proposals) {
        actions.removeIf(action -> action.getStatus() == PerformanceActionStatus.PENDING && !action.isRollback());
        actions.addAll(proposals);
    }

    public synchronized List<PerformanceAction> getActions() {
        return Collections.unmodifiableList(new ArrayList<>(actions));
    }

    public synchronized Optional<PerformanceAction> findPending(String id) {
        return actions.stream()
                .filter(action -> action.getId().equals(id) && action.getStatus() == PerformanceActionStatus.PENDING)
                .findFirst();
    }

    public synchronized Optional<PerformanceAction> findActive(String id) {
        return actions.stream()
                .filter(action -> action.getId().equals(id)
                        && (action.getStatus() == PerformanceActionStatus.APPLIED
                        || action.getStatus() == PerformanceActionStatus.APPROVED))
                .findFirst();
    }

    public synchronized boolean hasPendingOrActiveMitigation(String subsystem) {
        return actions.stream()
                .anyMatch(action -> action.getSubsystem().equals(subsystem)
                        && !action.isRollback()
                        && (action.getStatus() == PerformanceActionStatus.PENDING
                        || action.getStatus() == PerformanceActionStatus.APPROVED
                        || action.getStatus() == PerformanceActionStatus.APPLIED));
    }

    public synchronized boolean hasPendingRollback(String subsystem) {
        return actions.stream()
                .anyMatch(action -> action.getSubsystem().equals(subsystem)
                        && action.isRollback()
                        && action.getStatus() == PerformanceActionStatus.PENDING);
    }

    public synchronized void enqueueRollbacks(List<PerformanceAction> rollbacks) {
        actions.addAll(rollbacks);
    }

    public synchronized void markExpired(List<String> ids) {
        for (String id : ids) {
            actions.stream()
                    .filter(action -> action.getId().equals(id))
                    .forEach(PerformanceAction::markExpired);
        }
    }

    public synchronized List<PerformanceAction> getPending() {
        return actions.stream()
                .filter(action -> action.getStatus() == PerformanceActionStatus.PENDING)
                .collect(Collectors.toList());
    }

    public synchronized List<PerformanceAction> getActive() {
        return actions.stream()
                .filter(action -> action.getStatus() == PerformanceActionStatus.APPROVED
                        || action.getStatus() == PerformanceActionStatus.APPLIED)
                .collect(Collectors.toList());
    }

    public synchronized void reject(String id) {
        actions.stream()
                .filter(action -> action.getId().equals(id))
                .findFirst()
                .ifPresent(PerformanceAction::markRejected);
    }

    public synchronized void cleanupExpired() {
        Iterator<PerformanceAction> iterator = actions.iterator();
        while (iterator.hasNext()) {
            PerformanceAction action = iterator.next();
            if (action.getStatus() == PerformanceActionStatus.EXPIRED
                    || action.getStatus() == PerformanceActionStatus.REJECTED) {
                iterator.remove();
            }
        }
    }

    public synchronized List<PerformanceAction> expirePendingOlderThan(Instant cutoff) {
        List<PerformanceAction> expired = new ArrayList<>();
        for (PerformanceAction action : actions) {
            if (action.getStatus() == PerformanceActionStatus.PENDING
                    && action.getCreatedAt().isBefore(cutoff)) {
                action.markExpired();
                expired.add(action);
            }
        }
        return expired;
    }
}
