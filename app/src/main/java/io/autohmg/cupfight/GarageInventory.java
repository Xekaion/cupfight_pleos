package io.autohmg.cupfight;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

final class GarageInventory {
    private static final String OWNED = "cosmetics_owned";
    private static final String EQUIPPED = "cosmetics_equipped";
    private static final String DROPS = "drops";

    private final SharedPreferences preferences;
    private final EnumSet<Cosmetic> owned = EnumSet.noneOf(Cosmetic.class);
    private final EnumMap<Cosmetic.Slot, Cosmetic> equipped = new EnumMap<>(Cosmetic.Slot.class);
    private int drops;

    GarageInventory(SharedPreferences preferences) {
        this.preferences = preferences;
        drops = Math.max(0, preferences.getInt(DROPS, 0));
        if (!preferences.contains(OWNED)) {
            restoreLegacy(Cosmetic.NEON_WING, "wingOwned", "wingEquipped");
            restoreLegacy(Cosmetic.CRYSTAL_CUP, "crystalOwned", "crystalEquipped");
            return;
        }
        Set<String> ownedIds = preferences.getStringSet(OWNED, Collections.emptySet());
        Set<String> equippedIds = preferences.getStringSet(EQUIPPED, Collections.emptySet());
        for (Cosmetic item : Cosmetic.values()) {
            if (ownedIds.contains(item.name())) {
                owned.add(item);
                if (equippedIds.contains(item.name())) {
                    equipped.put(item.slot, item);
                }
            }
        }
    }

    private void restoreLegacy(Cosmetic item, String ownedKey, String equippedKey) {
        if (preferences.getBoolean(ownedKey, false)) {
            owned.add(item);
            if (preferences.getBoolean(equippedKey, false)) {
                equipped.put(item.slot, item);
            }
        }
    }

    int drops() {
        return drops;
    }

    int ownedCount() {
        return owned.size();
    }

    boolean owns(Cosmetic item) {
        return owned.contains(item);
    }

    boolean isEquipped(Cosmetic item) {
        return equipped.get(item.slot) == item;
    }

    Cosmetic equippedIn(Cosmetic.Slot slot) {
        return equipped.get(slot);
    }

    Set<Cosmetic> equipped() {
        EnumSet<Cosmetic> copy = EnumSet.noneOf(Cosmetic.class);
        copy.addAll(equipped.values());
        return copy;
    }

    Set<Cosmetic> withPreview(Cosmetic item) {
        Set<Cosmetic> copy = equipped();
        copy.remove(equipped.get(item.slot));
        copy.add(item);
        return copy;
    }

    boolean buy(Cosmetic item) {
        if (owned.contains(item) || drops < item.price) {
            return false;
        }
        drops -= item.price;
        owned.add(item);
        save();
        return true;
    }

    boolean toggle(Cosmetic item) {
        if (!owned.contains(item)) {
            return false;
        }
        if (isEquipped(item)) {
            equipped.remove(item.slot);
        } else {
            equipped.put(item.slot, item);
        }
        save();
        return true;
    }

    void award(int amount) {
        drops = (int) Math.min(Integer.MAX_VALUE, (long) drops + amount);
        save();
    }

    private void save() {
        Set<String> ownedIds = new HashSet<>();
        Set<String> equippedIds = new HashSet<>();
        for (Cosmetic item : owned) {
            ownedIds.add(item.name());
        }
        for (Cosmetic item : equipped.values()) {
            equippedIds.add(item.name());
        }
        preferences.edit().putInt(DROPS, drops)
                .putStringSet(OWNED, ownedIds)
                .putStringSet(EQUIPPED, equippedIds)
                .apply();
    }
}
