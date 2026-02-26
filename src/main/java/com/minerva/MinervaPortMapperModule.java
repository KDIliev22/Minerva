package com.minerva;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import bt.net.portmapping.PortMapper;
import bt.net.portmapping.impl.UpnpPortMapper;

public class MinervaPortMapperModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<PortMapper> binder = Multibinder.newSetBinder(binder(), PortMapper.class, PortMapping.class);
        binder.addBinding().to(UpnpPortMapper.class);
    }
}