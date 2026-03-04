package bt.net.portmapping.impl;

import bt.net.portmapping.PortMapper;
import bt.net.portmapping.PortMapProtocol;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UpnpPortMapper implements PortMapper {
    private static final Logger logger = LoggerFactory.getLogger(UpnpPortMapper.class);
    private final UpnpService upnpService;

    public UpnpPortMapper() {
        this.upnpService = new UpnpServiceImpl();
        logger.debug("UpnpPortMapper created – service starting asynchronously");
    }

    /**
     * Resolve the actual LAN IP if the caller passed a wildcard address (0.0.0.0).
     * Routers reject UPnP AddPortMapping with 0.0.0.0 as NewInternalClient.
     */
    private String resolveLanAddress(String localAddr) {
        if (localAddr == null || "0.0.0.0".equals(localAddr) || "::".equals(localAddr)) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(new InetSocketAddress("8.8.8.8", 80));
                String resolved = socket.getLocalAddress().getHostAddress();
                logger.info("Resolved wildcard address {} to LAN IP {}", localAddr, resolved);
                return resolved;
            } catch (Exception e) {
                try {
                    String fallback = InetAddress.getLocalHost().getHostAddress();
                    logger.info("Resolved wildcard address {} to fallback IP {}", localAddr, fallback);
                    return fallback;
                } catch (Exception ex) {
                    logger.warn("Could not resolve LAN address, keeping {}", localAddr);
                    return localAddr;
                }
            }
        }
        return localAddr;
    }

    @Override
    public void mapPort(int port, String localAddr, PortMapProtocol protocol, String description) {
        String proto = protocol == null ? "TCP" : protocol.name();
        localAddr = resolveLanAddress(localAddr);
        logger.info("Attempting to map {} port {} to {} using UPnP", proto, port, localAddr);

        // Give UPnP some time to initialize
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        if (upnpService.getControlPoint() == null) {
            logger.warn("UPnP control point not available – skipping mapping");
            return;
        }

        upnpService.getControlPoint().search();

        // Wait for device discovery
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        for (Device<?,?,?> device : upnpService.getRegistry().getDevices(new UDADeviceType("InternetGatewayDevice"))) {
            Service wanIpConn = device.findService(new UDAServiceType("WANIPConnection"));
            if (wanIpConn == null) {
                wanIpConn = device.findService(new UDAServiceType("WANPPPConnection"));
            }
            if (wanIpConn != null) {
                ActionInvocation<?> addPortMapping = new ActionInvocation<>(wanIpConn.getAction("AddPortMapping"));
                addPortMapping.setInput("NewRemoteHost", "");
                addPortMapping.setInput("NewExternalPort", port);
                addPortMapping.setInput("NewProtocol", proto);
                addPortMapping.setInput("NewInternalPort", port);
                addPortMapping.setInput("NewInternalClient", localAddr);
                addPortMapping.setInput("NewEnabled", true);
                addPortMapping.setInput("NewPortMappingDescription", description != null ? description : "Minerva BT");
                addPortMapping.setInput("NewLeaseDuration", 0);

                CountDownLatch latch = new CountDownLatch(1);
                upnpService.getControlPoint().execute(new org.jupnp.controlpoint.ActionCallback(addPortMapping) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        logger.info("UPnP {} port {} mapped successfully", proto, port);
                        latch.countDown();
                    }
                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                        logger.warn("UPnP {} port {} mapping failed: {}", proto, port, defaultMsg);
                        latch.countDown();
                    }
                });

                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return; // mapped or failed, we're done
            }
        }
        logger.warn("No UPnP IGD with WANIPConnection found – port {} not mapped", port);
    }
}