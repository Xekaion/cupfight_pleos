package io.autohmg.cupfight;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

final class SegmentRanking {
    private static final String KEY_PREFIX = "segment_ranking_v1:";
    private static final String ALIAS = "alias";
    private static final String REMAINING = "remainingTenths";
    private static final int FULL_CUP = 1000;

    private final SharedPreferences preferences;

    SegmentRanking(SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    boolean record(String segment, String model, String driverId, String alias, int remainingTenths) {
        String key = scopeKey(segment, model);
        String driver = requireValue(driverId);
        String displayAlias = requireValue(alias);
        if (remainingTenths < 0 || remainingTenths > FULL_CUP) {
            throw new IllegalArgumentException("Remaining water must be between 0 and 1000 tenths");
        }

        synchronized (preferences) {
            JSONObject scores = readScores(key);
            JSONObject previous = scores.optJSONObject(driver);
            int previousRemaining = previous == null ? -1 : previous.optInt(REMAINING, -1);
            int best = previousRemaining <= FULL_CUP
                    ? Math.max(remainingTenths, previousRemaining) : remainingTenths;
            try {
                JSONObject score = new JSONObject();
                score.put(ALIAS, displayAlias);
                score.put(REMAINING, best);
                scores.put(driver, score);
                return preferences.edit().putString(key, scores.toString()).commit();
            } catch (JSONException exception) {
                throw new IllegalStateException("Could not encode ranking entry", exception);
            }
        }
    }

    List<Entry> standings(String segment, String model) {
        JSONObject scores = readScores(scopeKey(segment, model));
        List<String> drivers = new ArrayList<>();
        for (Iterator<String> keys = scores.keys(); keys.hasNext(); ) {
            String driver = keys.next();
            JSONObject score = scores.optJSONObject(driver);
            if (score != null && !score.optString(ALIAS).isEmpty()) {
                int remaining = score.optInt(REMAINING, -1);
                if (remaining >= 0 && remaining <= FULL_CUP) {
                    drivers.add(driver);
                }
            }
        }
        drivers.sort(Comparator.comparingInt((String driver) -> scores.optJSONObject(driver).optInt(REMAINING))
                .reversed().thenComparing(Comparator.naturalOrder()));

        List<Entry> standings = new ArrayList<>();
        int previousRemaining = -1;
        int rank = 0;
        for (String driver : drivers) {
            JSONObject score = scores.optJSONObject(driver);
            int remaining = score.optInt(REMAINING);
            if (remaining != previousRemaining) {
                rank = standings.size() + 1;
                previousRemaining = remaining;
            }
            standings.add(new Entry(driver, score.optString(ALIAS), remaining, rank));
        }
        return standings;
    }

    private JSONObject readScores(String key) {
        try {
            return new JSONObject(preferences.getString(key, "{}"));
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }

    private static String scopeKey(String segment, String model) {
        String road = requireValue(segment);
        // The road length keeps segment and model pairs distinct even when labels contain punctuation.
        return KEY_PREFIX + road.length() + ':' + road + requireValue(model);
    }

    private static String requireValue(String value) {
        String trimmed = Objects.requireNonNull(value).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Ranking values cannot be blank");
        }
        return trimmed;
    }

    static final class Entry {
        final String driverId;
        final String alias;
        final int remainingTenths;
        final int rank;

        private Entry(String driverId, String alias, int remainingTenths, int rank) {
            this.driverId = driverId;
            this.alias = alias;
            this.remainingTenths = remainingTenths;
            this.rank = rank;
        }
    }
}
