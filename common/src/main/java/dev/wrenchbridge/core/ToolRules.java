package dev.wrenchbridge.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure, Java 8-compatible rules. Tags discover tools; stack state decides intent. */
public final class ToolRules {
    public static final Set<String> WRENCH_TAGS = set("c:tools/wrench", "c:wrenches",
            "forge:tools/wrench", "forge:wrenches", "fabric:wrenches");
    private static final Set<String> KNOWN = set("anvilcraft:anvil_hammer", "anvilcraft:royal_anvil_hammer",
            "anvilcraft:ember_anvil_hammer", "anvilcraft:frost_anvil_hammer", "anvilcraft:transcendence_anvil_hammer",
            "mekanism:configurator", "rftoolsbase:smartwrench", "rftoolsbase:smartwrench_select",
            "immersiveengineering:hammer", "prettypipes:wrench",
            "thermalfoundation:wrench", "thermal:wrench", "enderio:item_yeta_wrench", "enderio:yeta_wrench",
            "pneumaticcraft:pneumatic_wrench", "actuallyadditions:laser_wrench",
            "actuallyadditions:item_laser_wrench", "ic2:wrench", "ic2:electric_wrench");
    private static final Set<String> NEVER = set("industrialforegoing:infinity_hammer",
            "fluxnetworks:flux_configurator", "fluxnetworks:admin_configurator", "thermal:detonator");

    private ToolRules() {}

    public static boolean isCandidate(String id, Collection<String> tags, boolean dismantleAbility) {
        if (id == null || NEVER.contains(id)) return false;
        if (id.equals("wrenchbridge:universal_wrench") || dismantleAbility || KNOWN.contains(id)) return true;
        for (String tag : tags) {
            if (WRENCH_TAGS.contains(tag) || tag.equals("anvilcraft:tools/anvil_hammer")
                    || tag.equals("immersiveengineering:tools/hammers")) return true;
        }
        // A narrow lexical fallback: do not interpret every hammer or configurator as a wrench.
        String path = id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        return path.equals("wrench") || path.endsWith("_wrench") || path.startsWith("wrench_")
                || path.equals("spanner") || path.endsWith("_spanner") || path.equals("crescent_hammer");
    }

    public static boolean allowsSharedDismantle(boolean dismantleAbility, boolean otherWrenchAbility, String mode) {
        if (dismantleAbility) return true;
        if (otherWrenchAbility) return false;
        if (mode == null || mode.isEmpty()) return true;
        String name = mode.toUpperCase(Locale.ROOT);
        if (name.equals("WRENCH") || name.equals("DEFAULT") || name.equals("NORMAL")
                || name.equals("MODE_WRENCH") || name.equals("ALL")) return true;
        return !(name.contains("CONFIG") || name.contains("SELECT") || name.contains("OFFSET")
                || name.contains("COPY") || name.contains("PASTE") || name.contains("EMPTY")
                || name.equals("ROTATE") || name.contains("BIND"));
    }

    private static Set<String> set(String... values) { return new HashSet<String>(Arrays.asList(values)); }
}
