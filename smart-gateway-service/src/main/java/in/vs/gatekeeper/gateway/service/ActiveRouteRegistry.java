package in.vs.gatekeeper.gateway.service;

import org.springframework.stereotype.Component;

import in.vs.gatekeeper.gateway.dto.GatewayRoute;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe snapshot of the active routes currently applied by this gateway. */
@Component
public class ActiveRouteRegistry {

    private final AtomicReference<List<GatewayRoute>> routes =
            new AtomicReference<>(List.of());

    public void replace(List<GatewayRoute> newRoutes) {
        List<GatewayRoute> snapshot = newRoutes == null ? List.of() : newRoutes.stream()
                .filter(GatewayRoute::isActive)
                .sorted(Comparator.comparingInt(
                        (GatewayRoute route) -> route.getPathPattern() == null
                                ? 0 : route.getPathPattern().length()).reversed())
                .toList();
        routes.set(snapshot);
    }

    public List<GatewayRoute> getRoutes() {
        return routes.get();
    }
}
