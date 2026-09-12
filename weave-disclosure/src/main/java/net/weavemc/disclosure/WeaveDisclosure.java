package net.weavemc.disclosure;

import net.weavemc.api.ModInitializer;

import java.lang.instrument.Instrumentation;

public final class WeaveDisclosure implements ModInitializer {
    @Override
    public void preInit(Instrumentation instrumentation) {
        requireActive("preInit");
    }

    @Override
    public void init() {
        requireActive("init");
    }

    private static void requireActive(String phase) {
        if (!Boolean.getBoolean("weave.active")) {
            throw new IllegalStateException("Weave attachment state is missing");
        }
        System.out.println("[Weave Disclosure] " + phase + " acknowledged session "
                + System.getProperty("weave.session.id", "unknown"));
    }
}
