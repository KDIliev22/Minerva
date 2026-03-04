package com.minerva.dht;

import bt.dht.DHTService;
import com.google.inject.AbstractModule;

/**
 * Guice module that overrides the default DHTService binding to use
 * PersistentMldhtService, which persists the DHT routing table to disk
 * for faster bootstrap on subsequent startups.
 */
public class PersistentDHTModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DHTService.class).to(PersistentMldhtService.class).asEagerSingleton();
    }
}
