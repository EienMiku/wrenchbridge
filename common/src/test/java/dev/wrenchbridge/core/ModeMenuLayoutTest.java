package dev.wrenchbridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModeMenuLayoutTest {
    @Test void rowsFitWithoutOverlappingTheFooterAcrossGuiSizes() {
        for (int width : new int[] {320, 427, 854, 1280}) {
            for (int height = 240; height <= 1080; height += 11) {
                ModeMenuLayout layout = ModeMenuLayout.forScreen(width, height);
                int lastBottom = layout.rowY(layout.rows - 1) + ModeMenuLayout.ROW_HEIGHT;
                assertTrue(layout.left >= 12);
                assertTrue(layout.left + layout.width <= width - 12);
                assertTrue(layout.searchY + 20 <= layout.listY);
                assertTrue(lastBottom + 8 <= layout.footerY);
                assertTrue(lastBottom + ModeMenuLayout.ROW_STEP + 8 > layout.footerY,
                        "Another row would fit; the menu is leaving usable space empty");
                assertTrue(layout.footerY + 20 <= layout.doneY);
                assertEquals(height - 12, layout.doneY + 20);
            }
        }
    }

    @Test void tallScreensShowMoreThanSevenChoices() {
        ModeMenuLayout small = ModeMenuLayout.forScreen(320, 240);
        ModeMenuLayout large = ModeMenuLayout.forScreen(854, 480);
        assertTrue(large.rows > 7);
        assertTrue(large.rows > small.rows);
    }
}
