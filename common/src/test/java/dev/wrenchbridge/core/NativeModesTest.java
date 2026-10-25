package dev.wrenchbridge.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class NativeModesTest {
    public enum Mode { CONFIGURE, WRENCH }
    public static final class Stack { Mode mode = Mode.CONFIGURE; }
    public static final class Player { }
    public static final class Tool {
        int useCalls;
        public Mode getMode(Stack stack) { return stack.mode; }
        public void setMode(Stack stack, Player player, Mode mode) { stack.mode = mode; }
        public void use(Stack stack) { useCalls++; throw new AssertionError("Must not probe gameplay"); }
    }
    public static final class ErasedTool {
        public Object getMode(Stack stack) { return stack.mode; }
        public void setMode(Stack stack, Player player, Object mode) { stack.mode = (Mode) mode; }
    }
    public static final class LegacyTool {
        public Mode getState(Stack stack) { return stack.mode; }
        public void setState(Stack stack, Mode mode) { stack.mode = mode; }
    }
    public static final class FailingGetterTool {
        public Mode getMode(Stack stack) { throw new IllegalStateException("getter unavailable"); }
        public Mode getState(Stack stack) { return stack.mode; }
        public void setState(Stack stack, Mode mode) { stack.mode = mode; }
    }
    public static final class AmbiguousTool {
        int setterCalls;
        public Mode getMode(Stack stack) { return stack.mode; }
        public void setMode(Object a, Object b) { setterCalls++; }
    }
    @Test void cachedMetadataStillReadsEachStackAndRecoversFromGetterFailure() throws Exception {
        FailingGetterTool tool = new FailingGetterTool();
        Stack first = new Stack(), second = new Stack();
        assertEquals("CONFIGURE", NativeModes.current(tool, first));
        assertTrue(NativeModes.select(tool, first, new Player(), "WRENCH"));
        assertEquals("WRENCH", NativeModes.current(tool, first));
        assertEquals("CONFIGURE", NativeModes.current(tool, second));
        first.mode = Mode.CONFIGURE;
        assertEquals("CONFIGURE", NativeModes.current(tool, first));
    }
    @Test void negativeAndAmbiguousStructuresDoNotInvokeGameplayOrSetters() throws Exception {
        AmbiguousTool tool = new AmbiguousTool();
        for (int i = 0; i < 3; i++) {
            assertTrue(NativeModes.choices(tool, new Stack(), new Player()).isEmpty());
            assertFalse(NativeModes.select(tool, new Stack(), new Player(), "WRENCH"));
            assertEquals("", NativeModes.current(new Object(), new Stack()));
        }
        assertEquals(0, tool.setterCalls);
    }
    @Test void supportsGenericRadialModesAndLegacyStateSetters() throws Exception {
        Stack stack = new Stack(); Player player = new Player();
        assertTrue(NativeModes.select(new ErasedTool(), stack, player, "WRENCH"));
        assertEquals(Mode.WRENCH, stack.mode);
        assertTrue(NativeModes.select(new LegacyTool(), stack, player, "CONFIGURE"));
        assertEquals(Mode.CONFIGURE, stack.mode);
    }
    @Test void enumeratesAndSetsOnlyDeclaredModesWithoutTryingUseActions() throws Exception {
        Tool tool = new Tool(); Stack stack = new Stack(); Player player = new Player();
        assertEquals(Arrays.asList("CONFIGURE", "WRENCH"), NativeModes.choices(tool, stack, player));
        assertTrue(NativeModes.select(tool, stack, player, "WRENCH"));
        assertEquals("WRENCH", NativeModes.current(tool, stack));
        assertFalse(NativeModes.select(tool, stack, player, "INVALID"));
        assertEquals(0, tool.useCalls);
    }
}
