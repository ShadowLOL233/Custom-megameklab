/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMekLab.
 */

package megameklab.util;

/**
 * Compacts long Outer Sphere display names down to record-sheet abbreviations
 * (Improve -> Imp., Advance -> Adv., Superheavy -> Suphvy, Reinforced -> Rein.,
 * Heavy-Duty -> Hvy-Duty, Heavy Duty -> Hvy Duty, Experimental -> Exp.).
 * <p>
 * Used only by the printing layer so MML displays full names while the Mek
 * Record Sheet remains tight enough to fit the same cells the canon abbreviations
 * were designed for. Substitutions only run when the input contains "(OS)" so
 * non-OS equipment names are returned unchanged.
 */
public final class OSAbbreviator {

    private OSAbbreviator() {
    }

    public static String abbreviate(String fullName) {
        if (fullName == null || !fullName.contains("(OS)")) {
            return fullName;
        }
        return fullName
              // composite first so "Experimental Superheavy" collapses cleanly
              .replace("Experimental Superheavy", "Exp. Suphvy")
              .replace("Experimental", "Exp.")
              .replace("Improve", "Imp.")
              .replace("Advance", "Adv.")
              .replace("Superheavy", "Suphvy")
              .replace("Reinforced", "Rein.")
              .replace("Heavy-Duty", "Hvy-Duty")
              .replace("Heavy Duty", "Hvy Duty");
    }
}
