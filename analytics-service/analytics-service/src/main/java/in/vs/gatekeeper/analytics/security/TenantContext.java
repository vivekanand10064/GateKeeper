
package in.vs.gatekeeper.analytics.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    public Long getCurrentOrganizationId() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException(
                    "Authenticated tenant context is unavailable"
            );
        }

        Object organizationId = jwt.getClaim("organizationId");

        if (organizationId instanceof Number number) {
            return number.longValue();
        }

        if (organizationId instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new AccessDeniedException(
                        "Invalid organization ID in authentication token"
                );
            }
        }

        throw new AccessDeniedException(
                "Organization ID is missing from authentication token"
        );
    }
}
