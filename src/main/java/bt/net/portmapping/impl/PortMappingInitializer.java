
package bt.net.portmapping.impl;
import bt.net.portmapping.PortMapper;
import com.minerva.PortMapping;
import javax.inject.Inject;
import java.util.Set;

public class PortMappingInitializer {
    private final Set<PortMapper> portMappers;
    private final Object runtimeLifecycleBinder;
    private final Object config;

    @Inject
    public PortMappingInitializer(@PortMapping Set<PortMapper> portMappers) {
        this.portMappers = portMappers;
        this.runtimeLifecycleBinder = null;
        this.config = null;
        // Initialization logic here
    }
}
