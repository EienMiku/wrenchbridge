package dev.wrenchbridge.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RequestBudgetTest {
    @Test void acceptsRapidSelectionsButBoundsFloodsAndRefillsGradually() {
        RequestBudget budget = new RequestBudget(8, 2, 100);
        for (int i = 0; i < 8; i++) assertTrue(budget.acquire(100));
        for (int i = 0; i < 1000; i++) assertFalse(budget.acquire(100));
        assertFalse(budget.acquire(101));
        assertTrue(budget.acquire(102));
        assertFalse(budget.acquire(102));
        for (int i = 0; i < 8; i++) assertTrue(budget.acquire(1000));
        assertFalse(budget.acquire(1000));
    }
    @Test void serverTickIntegerWrapDoesNotDisableTheLimit() {
        RequestBudget budget = new RequestBudget(1, 2, Integer.MAX_VALUE);
        assertTrue(budget.acquire(Integer.MAX_VALUE));
        assertFalse(budget.acquire(Integer.MIN_VALUE));
        assertTrue(budget.acquire(Integer.MIN_VALUE + 1));
    }
}
