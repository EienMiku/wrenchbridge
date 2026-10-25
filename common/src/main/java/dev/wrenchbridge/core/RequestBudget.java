package dev.wrenchbridge.core;

/** A small burst of UI actions, refilled by server ticks; no wall clock or gameplay state cache. */
public final class RequestBudget {
    private final int capacity, cost;
    private int balance, last;

    public RequestBudget(int burst, int ticksPerAction, int tick) {
        if (burst < 1 || ticksPerAction < 1) throw new IllegalArgumentException();
        capacity = burst * ticksPerAction;
        cost = ticksPerAction;
        balance = capacity;
        last = tick;
    }

    public boolean acquire(int tick) {
        int elapsed = tick - last;
        if (elapsed > 0) { balance = (int) Math.min(capacity, (long) balance + elapsed); last = tick; }
        if (balance < cost) return false;
        balance -= cost;
        return true;
    }
}
