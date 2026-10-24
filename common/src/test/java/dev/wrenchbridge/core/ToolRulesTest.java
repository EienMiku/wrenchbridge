package dev.wrenchbridge.core;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolRulesTest {
    @Test void discoversUnknownWrenchesWithoutPromotingWeapons() {
        assertTrue(ToolRules.isCandidate("newtech:copper_wrench", Collections.emptySet(), false));
        assertTrue(ToolRules.isCandidate("newtech:odd_tool", Collections.singleton("c:tools/wrench"), false));
        assertTrue(ToolRules.isCandidate("newtech:odd_tool", Collections.emptySet(), true));
        assertFalse(ToolRules.isCandidate("newtech:war_hammer", Collections.emptySet(), false));
        assertFalse(ToolRules.isCandidate("industrialforegoing:infinity_hammer", Collections.emptySet(), false));
        assertFalse(ToolRules.isCandidate("fluxnetworks:flux_configurator", Collections.emptySet(), false));
    }
    @Test void readsIntentPerStackInsteadOfTreatingTagsAsAMode() {
        assertTrue(ToolRules.allowsSharedDismantle(true, false, "WRENCH"));
        assertFalse(ToolRules.allowsSharedDismantle(false, true, "CONFIGURATE_ITEMS"));
        assertFalse(ToolRules.allowsSharedDismantle(false, false, "MODE_SELECT"));
        assertFalse(ToolRules.allowsSharedDismantle(false, false, "OFFSET_SIDE"));
        assertFalse(ToolRules.allowsSharedDismantle(false, false, "CONFIG_ASPECT"));
        assertTrue(ToolRules.allowsSharedDismantle(false, false, "DEFAULT"));
        assertTrue(ToolRules.allowsSharedDismantle(false, false, ""));
    }
}
