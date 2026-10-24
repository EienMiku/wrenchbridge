package dev.wrenchbridge.core;

/** Geometry in Minecraft GUI pixels. The standard scaled viewport is at least 320 x 240. */
public final class ModeMenuLayout {
    public static final int ROW_HEIGHT = 20;
    public static final int ROW_STEP = 24;
    public final int left, width, searchY, listY, rows, footerY, doneY;

    private ModeMenuLayout(int screenWidth, int screenHeight) {
        width = Math.min(360, Math.max(0, screenWidth - 24));
        left = (screenWidth - width) / 2;
        searchY = screenHeight < 220 ? 28 : 48;
        listY = searchY + 28;
        doneY = screenHeight - 32;
        footerY = doneY - 26;
        rows = Math.max(1, (footerY - 8 - listY + ROW_STEP - ROW_HEIGHT) / ROW_STEP);
    }

    public static ModeMenuLayout forScreen(int width, int height) {
        return new ModeMenuLayout(width, height);
    }

    public int rowY(int row) { return listY + row * ROW_STEP; }
}
