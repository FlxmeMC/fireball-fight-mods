package com.weave.lunarmods.internal.lunar;

import java.util.LinkedList;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class DiscoveryTest {
    public interface Option {
        Object get();
        void reset();
    }

    public static class RegistrarBase {
        public final LinkedList<Option> rows = new LinkedList<Option>();
        public RegistrarBase add(Option... options) { return this; }
    }
    public static class Registrar extends RegistrarBase { }
    public static class Editor { }
    public interface Feature {
        void hook(Editor editor);
        void hook(Registrar registrar);
        void enabled(boolean enabled);
    }
    public interface AmbiguousFeature {
        void first(Registrar registrar);
        void second(Registrar registrar);
    }

    @Test public void distinguishesSettingsFromLunarsEditorHookUsingInheritedRegistrarApi() throws Exception {
        assertEquals(Feature.class.getMethod("hook", Registrar.class), Discovery.findOptionsHook(Feature.class));
    }

    @Test(expected = IllegalStateException.class)
    public void refusesToGuessWhenTwoRealSettingsHooksExist() {
        Discovery.findOptionsHook(AmbiguousFeature.class);
    }
}
