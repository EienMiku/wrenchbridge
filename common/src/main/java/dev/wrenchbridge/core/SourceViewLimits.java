package dev.wrenchbridge.core;

/** Bounds for read-only, player-owned tool snapshots used by client tooltips and slot bars. */
public final class SourceViewLimits {
  public static final int MAX_SOURCES = 8;
  public static final int MAX_ID_BYTES = 128;
  public static final int MAX_STACK_BYTES = 64 * 1024;
  public static final int REFRESH_TICKS = 10;
  public static final int EXPIRE_TICKS = 40;
  public static final int MAX_CACHED = 64;

  private SourceViewLimits() {}
}
