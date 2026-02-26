package com.minerva;

import bt.net.portmapping.PortMapper;
import bt.net.portmapping.impl.UpnpPortMapper;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class PortMapperProducer {
    @Produces
    @PortMapping
    public Set<PortMapper> producePortMappers() {
        Set<PortMapper> portMappers = new HashSet<>();
        portMappers.add(new UpnpPortMapper());  // your UPnP implementation
        return portMappers;
    }
}