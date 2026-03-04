package com.minerva.dht;

import bt.dht.DHTConfig;
import bt.dht.DHTHandshakeHandler;
import bt.dht.DHTService;
import bt.dht.DHTPeerSourceFactory;
import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import bt.protocol.handler.PortMessageHandler;
import com.google.inject.AbstractModule;

/**
 * Replaces the standard DHTModule entirely, binding PersistentMldhtService
 * instead of the default MldhtService for DHT routing table persistence.
 */
public class PersistentDHTModule extends AbstractModule {
    private final DHTConfig config;

    public PersistentDHTModule(DHTConfig config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        bind(DHTConfig.class).toInstance(config);

        ServiceModule.extend(binder()).addPeerSourceFactory(DHTPeerSourceFactory.class);
        ProtocolModule.extend(binder()).addHandshakeHandler(DHTHandshakeHandler.class);
        ProtocolModule.extend(binder()).addMessageHandler(PortMessageHandler.PORT_ID, PortMessageHandler.class);

        // Use our persistent implementation instead of the default MldhtService
        bind(DHTService.class).to(PersistentMldhtService.class).asEagerSingleton();
    }
}
