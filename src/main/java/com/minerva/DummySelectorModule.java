package com.minerva;

import bt.module.PeerConnectionSelector;
import com.google.inject.AbstractModule;
import java.io.IOException;
import java.nio.channels.Selector;

public class DummySelectorModule extends AbstractModule {
    @Override
    protected void configure() {
        try {
            // Provide a real Selector (open one). This will be used by LSD.
            // If you prefer a no‑op mock, you could create a custom implementation,
            // but a real Selector is fine – LSD will just never receive data on it.
            bind(Selector.class).annotatedWith(PeerConnectionSelector.class)
                .toInstance(Selector.open());
        } catch (IOException e) {
            throw new RuntimeException("Failed to open dummy Selector", e);
        }
    }
}