package com.autogradingsystem.web.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AssessmentProgressRegistry {

    private static final ConcurrentHashMap<String, ProgressState> STATES = new ConcurrentHashMap<>();

    private AssessmentProgressRegistry() {
    }

    public static void start(String assessment) {
        ProgressState state = new ProgressState();
        state.stage = "Starting";
        state.message = "Preparing grading run...";
        state.percent = 0;
        state.startedAtMs = System.currentTimeMillis();
        state.updatedAtMs = state.startedAtMs;
        STATES.put(assessment, state);
    }

    public static void updatePercent(String assessment, int percent, String stage, String message) {
        ProgressState state = STATES.computeIfAbsent(assessment, k -> new ProgressState());
        state.percent = clamp(percent);
        state.stage = stage;
        state.message = message;
        state.updatedAtMs = System.currentTimeMillis();
        if (state.startedAtMs == 0) {
            state.startedAtMs = state.updatedAtMs;
        }
    }

    public static void updateProgress(String assessment, String stage, int percentBase,
                                      int completedUnits, int totalUnits, String message) {
        ProgressState state = STATES.computeIfAbsent(assessment, k -> new ProgressState());
        state.stage = stage;
        if (totalUnits > 0) {
            int extra = (int) Math.round((completedUnits * 1.0 / totalUnits) * 30.0);
            state.percent = clamp(percentBase + extra);
        } else {
            state.percent = clamp(percentBase);
        }
        state.message = message;
        state.updatedAtMs = System.currentTimeMillis();
        if (state.startedAtMs == 0) {
            state.startedAtMs = state.updatedAtMs;
        }
    }

    public static void complete(String assessment, String message) {
        ProgressState state = STATES.computeIfAbsent(assessment, k -> new ProgressState());
        state.percent = 100;
        state.stage = "Completed";
        state.message = message;
        state.done = true;
        state.success = true;
        state.updatedAtMs = System.currentTimeMillis();
        if (state.startedAtMs == 0) {
            state.startedAtMs = state.updatedAtMs;
        }
    }

    public static void fail(String assessment, String message) {
        ProgressState state = STATES.computeIfAbsent(assessment, k -> new ProgressState());
        state.stage = "Failed";
        state.message = message;
        state.done = true;
        state.success = false;
        state.updatedAtMs = System.currentTimeMillis();
        if (state.startedAtMs == 0) {
            state.startedAtMs = state.updatedAtMs;
        }
    }

    public static Map<String, Object> snapshot(String assessment) {
        ProgressState state = STATES.get(assessment);
        Map<String, Object> out = new LinkedHashMap<>();
        if (state == null) {
            out.put("known", false);
            out.put("percent", 0);
            out.put("stage", "Waiting");
            out.put("message", "No grading run has started yet.");
            out.put("done", false);
            return out;
        }

        out.put("known", true);
        out.put("percent", state.percent);
        out.put("stage", state.stage);
        out.put("message", state.message);
        out.put("done", state.done);
        out.put("success", state.success);
        out.put("etaSeconds", estimateEtaSeconds(state));
        return out;
    }

    public static void clear(String assessment) {
        if (assessment == null || assessment.isBlank()) {
            return;
        }
        STATES.remove(assessment);
    }

    public static void clearAll() {
        STATES.clear();
    }

    private static long estimateEtaSeconds(ProgressState state) {
        if (state.done || state.percent <= 0 || state.percent >= 100) {
            return 0;
        }
        long elapsed = Math.max(1, state.updatedAtMs - state.startedAtMs);
        double remainingFraction = (100.0 - state.percent) / state.percent;
        return Math.max(0, Math.round((elapsed * remainingFraction) / 1000.0));
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static class ProgressState {
        String stage;
        String message;
        int percent;
        boolean done;
        boolean success;
        long startedAtMs;
        long updatedAtMs;
    }
}
