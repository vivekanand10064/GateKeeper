
package in.vs.gatekeeper.management.service;

import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.vs.gatekeeper.management.dto.requestDto.HeaderRuleRequest;
import in.vs.gatekeeper.management.dto.requestDto.RouteRequest;
import in.vs.gatekeeper.management.dto.requestDto.RouteStatusRequest;
import in.vs.gatekeeper.management.dto.responseDto.AuditRecordResponse;
import in.vs.gatekeeper.management.dto.responseDto.RouteResponse;
import in.vs.gatekeeper.management.dto.responseDto.RouteVersionResponse;
import in.vs.gatekeeper.management.entity.ApiRoute;
import in.vs.gatekeeper.management.entity.ControlPlaneAuditRecord;
import in.vs.gatekeeper.management.entity.Organization;
import in.vs.gatekeeper.management.entity.OrganizationMonthlyUsage;
import in.vs.gatekeeper.management.entity.OrganizationStatus;
import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;
import in.vs.gatekeeper.management.entity.RouteConfigurationVersion;
import in.vs.gatekeeper.management.entity.RouteHeaderRule;
import in.vs.gatekeeper.management.event.GatewayConfigurationEventService;
import in.vs.gatekeeper.management.exception.ApiException;
import in.vs.gatekeeper.management.repository.ApiRouteRepository;
import in.vs.gatekeeper.management.repository.ControlPlaneAuditRecordRepository;
import in.vs.gatekeeper.management.repository.OrganizationMonthlyUsageRepository;
import in.vs.gatekeeper.management.repository.OrganizationUsagePlanRepository;
import in.vs.gatekeeper.management.repository.RouteConfigurationVersionRepository;
import tools.jackson.databind.ObjectMapper;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RouteService {

    private final ApiRouteRepository apiRouteRepository;
    private final RouteConfigurationVersionRepository versionRepository;
    private final ControlPlaneAuditRecordRepository auditRecordRepository;
    private final TenantContext tenantContext;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final GatewayConfigurationEventService configurationEventService;
    private final OrganizationUsagePlanRepository usagePlanRepository;
    private final OrganizationMonthlyUsageRepository monthlyUsageRepository;
    @Value("${gatekeeper.routes.allow-private-targets:false}")
    private boolean allowPrivateTargets;

    public RouteService(
            ApiRouteRepository apiRouteRepository,
            RouteConfigurationVersionRepository versionRepository,
            ControlPlaneAuditRecordRepository auditRecordRepository,
            TenantContext tenantContext,
            AuditService auditService,
            ObjectMapper objectMapper,
            GatewayConfigurationEventService configurationEventService,OrganizationUsagePlanRepository usagePlanRepository,
            OrganizationMonthlyUsageRepository monthlyUsageRepository) {
    	this.usagePlanRepository = usagePlanRepository;
    	this.monthlyUsageRepository = monthlyUsageRepository;
        this.apiRouteRepository = apiRouteRepository;
        this.versionRepository = versionRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.tenantContext = tenantContext;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.configurationEventService = configurationEventService;
    }

    @Transactional
    public RouteResponse create(RouteRequest request) {

        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();

        validate(request, organization);

        if (apiRouteRepository.existsByOrganizationIdAndRouteId(
                organization.getId(), request.getRouteId())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Route ID already exists for this organization"
            );
        }

        ApiRoute route = new ApiRoute();

        route.setOrganization(organization);
        route.setConfigurationVersion(1L);
        route.setCreatedAt(LocalDateTime.now());

        apply(route, request);

        apiRouteRepository.save(route);

        saveVersion(route, "CREATED");

        auditService.record(
                organization,
                "CREATE_ROUTE",
                "API_ROUTE",
                route.getRouteId(),
                startedAt,
                "Route configuration version 1 created"
        );

        configurationEventService.publish(
                "CREATED",
                organization.getId(),
                route.getRouteId(),
                route.getConfigurationVersion()
        );

        return RouteResponse.from(route);
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAllForTenant() {

        Organization organization = tenantContext.getOrganization();

        return toResponses(
                apiRouteRepository.findAllByOrganizationId(
                        organization.getId()
                )
        );
    }

    @Transactional(readOnly = true)
    public RouteResponse findById(Long id) {

        Organization organization = tenantContext.getOrganization();

        return RouteResponse.from(
                getOwnedRoute(id, organization.getId())
        );
    }

    @Transactional
    public RouteResponse update(Long id, RouteRequest request) {

        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();

        ApiRoute route = getOwnedRoute(id, organization.getId());

        if (!route.getRouteId().equals(request.getRouteId())) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "routeId cannot be changed"
            );
        }

        validate(request, organization);

        route.setConfigurationVersion(
                route.getConfigurationVersion() + 1
        );

        apply(route, request);

        saveVersion(route, "UPDATED");

        auditService.record(
                organization,
                "UPDATE_ROUTE",
                "API_ROUTE",
                route.getRouteId(),
                startedAt,
                "Route configuration version "
                        + route.getConfigurationVersion()
                        + " created"
        );

        configurationEventService.publish(
                "UPDATED",
                organization.getId(),
                route.getRouteId(),
                route.getConfigurationVersion()
        );

        return RouteResponse.from(route);
    }

    @Transactional
    public RouteResponse changeStatus(
            Long id,
            RouteStatusRequest request) {

        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();

        ApiRoute route = getOwnedRoute(id, organization.getId());

        route.setActive(request.getActive());

        route.setConfigurationVersion(
                route.getConfigurationVersion() + 1
        );

        route.setUpdatedAt(LocalDateTime.now());

        String changeType =
                request.getActive() ? "ACTIVATED" : "DEACTIVATED";

        String action =
                request.getActive() ? "ACTIVATE_ROUTE" : "DEACTIVATE_ROUTE";

        saveVersion(route, changeType);

        auditService.record(
                organization,
                action,
                "API_ROUTE",
                route.getRouteId(),
                startedAt,
                "Route configuration version "
                        + route.getConfigurationVersion()
                        + " created"
        );

        configurationEventService.publish(
                changeType,
                organization.getId(),
                route.getRouteId(),
                route.getConfigurationVersion()
        );

        return RouteResponse.from(route);
    }

    @Transactional
    public void delete(Long id) {

        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();

        ApiRoute route = getOwnedRoute(id, organization.getId());

        route.setConfigurationVersion(
                route.getConfigurationVersion() + 1
        );

        route.setUpdatedAt(LocalDateTime.now());

        saveVersion(route, "DELETED");

        auditService.record(
                organization,
                "DELETE_ROUTE",
                "API_ROUTE",
                route.getRouteId(),
                startedAt,
                "Final configuration saved as version "
                        + route.getConfigurationVersion()
        );

        configurationEventService.publish(
                "DELETED",
                organization.getId(),
                route.getRouteId(),
                route.getConfigurationVersion()
        );

        apiRouteRepository.delete(route);
    }

    @Transactional(readOnly = true)
    public List<RouteVersionResponse> getVersions(String routeId) {

        Organization organization = tenantContext.getOrganization();

        List<RouteVersionResponse> response = new ArrayList<>();

        List<RouteConfigurationVersion> versions =
                versionRepository
                        .findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(
                                organization.getId(),
                                routeId
                        );

        for (RouteConfigurationVersion version : versions) {
            response.add(RouteVersionResponse.from(version));
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAllActiveForGateway() {

        List<ApiRoute> routes =
                apiRouteRepository.findAllByActiveTrueAndOrganizationStatus(
                        OrganizationStatus.ACTIVE
                );

        List<RouteResponse> responses = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDate billingMonth = YearMonth.from(now).atDay(1);

        for (ApiRoute route : routes) {

            Long organizationId = route.getOrganization().getId();

            OrganizationUsagePlan usagePlan =
                    usagePlanRepository.findByOrganizationId(organizationId)
                            .orElse(null);

            OrganizationMonthlyUsage monthlyUsage =
                    monthlyUsageRepository
                            .findByOrganizationIdAndBillingMonth(
                                    organizationId,
                                    billingMonth
                            )
                            .orElse(null);

            responses.add(
                    RouteResponse.from(route, usagePlan, monthlyUsage)
            );
        }

        return responses;
    }

    private ApiRoute getOwnedRoute(Long id, Long organizationId) {

        return apiRouteRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "Route not found"
                        )
                );
    }

    private void apply(
            ApiRoute route,
            RouteRequest request) {

        route.setRouteId(request.getRouteId());
        route.setName(request.getName());
        route.setPathPattern(request.getPathPattern());
        route.setTargetBaseUrl(request.getTargetBaseUrl());
        route.setTargetPathPrefix(request.getTargetPathPrefix());
        route.setRequestsPerMinute(request.getRequestsPerMinute());
        route.setResponseTimeoutMs(request.getResponseTimeoutMs());
        route.setIdempotencyEnabled(request.isIdempotencyEnabled());
        route.setActive(request.isActive());
        route.setUpdatedAt(LocalDateTime.now());

        route.setAllowedMethods(
                normalizeMethods(request.getAllowedMethods())
        );

        route.getHeaderRules().clear();

        for (HeaderRuleRequest ruleRequest : request.getHeaderRules()) {

            RouteHeaderRule rule = new RouteHeaderRule();

            rule.setRoute(route);

            rule.setDirection(
                    ruleRequest.getDirection().toUpperCase(Locale.ROOT)
            );

            rule.setAction(
                    ruleRequest.getAction().toUpperCase(Locale.ROOT)
            );

            rule.setHeaderName(ruleRequest.getHeaderName());
            rule.setHeaderValue(ruleRequest.getHeaderValue());

            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());

            route.getHeaderRules().add(rule);
        }
    }

    private void validate(
            RouteRequest request,
            Organization organization) {

        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Route request is required"
            );
        }

        if (request.getPathPattern() == null
                || request.getPathPattern().isBlank()) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "pathPattern is required"
            );
        }

        String tenantPrefix = "/" + organization.getSlug() + "/";

        if (!request.getPathPattern().startsWith(tenantPrefix)) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "pathPattern must start with the tenant prefix "
                            + tenantPrefix
            );
        }

        if (request.getTargetPathPrefix() == null
                || !request.getTargetPathPrefix().startsWith("/")) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "targetPathPrefix must start with /"
            );
        }

        validateTargetBaseUrl(request.getTargetBaseUrl());

        if (request.getAllowedMethods() == null
                || request.getAllowedMethods().isEmpty()) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "At least one allowed HTTP method is required"
            );
        }

        normalizeMethods(request.getAllowedMethods());

        if (request.getHeaderRules() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "headerRules cannot be null"
            );
        }

        for (HeaderRuleRequest rule : request.getHeaderRules()) {

            if (rule == null
                    || rule.getDirection() == null
                    || rule.getAction() == null
                    || rule.getHeaderName() == null
                    || rule.getHeaderName().isBlank()) {

                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid header rule"
                );
            }

            String direction =
                    rule.getDirection().toUpperCase(Locale.ROOT);

            String action =
                    rule.getAction().toUpperCase(Locale.ROOT);

            if (!"REQUEST".equals(direction)
                    && !"RESPONSE".equals(direction)) {

                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Header direction must be REQUEST or RESPONSE"
                );
            }

            if (!"ADD".equals(action)
                    && !"REMOVE".equals(action)) {

                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Header action must be ADD or REMOVE"
                );
            }

            if ("ADD".equals(action)
                    && (rule.getHeaderValue() == null
                    || rule.getHeaderValue().isBlank())) {

                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ADD header rules require headerValue"
                );
            }
        }
    }

    private void validateTargetBaseUrl(String targetBaseUrl) {

        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "targetBaseUrl is required"
            );
        }

        try {

            if (!targetBaseUrl.equals(targetBaseUrl.trim())
                    || targetBaseUrl.chars().anyMatch(Character::isISOControl)) {

                throw new IllegalArgumentException(
                        "URL contains whitespace or control characters"
                );
            }

            URI uri = new URI(targetBaseUrl);

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (!uri.isAbsolute()
                    || scheme == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))
                    || host == null
                    || host.isBlank()
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getPort() == 0
                    || uri.getPort() > 65535) {

                throw new IllegalArgumentException(
                        "Invalid backend URL structure"
                );
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);

            if (normalizedHost.startsWith("[")
                    && normalizedHost.endsWith("]")) {

                normalizedHost = normalizedHost.substring(
                        1,
                        normalizedHost.length() - 1
                );
            }

            if (normalizedHost.contains("%")) {
                throw new IllegalArgumentException(
                        "IPv6 zone identifiers are not allowed"
                );
            }

            if (!normalizedHost.contains(":")) {
                normalizedHost = IDN.toASCII(normalizedHost);
            }

            normalizedHost = normalizedHost.toLowerCase(Locale.ROOT);

            if (normalizedHost.endsWith(".")) {
                normalizedHost = normalizedHost.substring(
                        0,
                        normalizedHost.length() - 1
                );
            }

            if (isMetadataHostname(normalizedHost)) {
                throw new IllegalArgumentException(
                        "Cloud metadata destinations are not allowed"
                );
            }

            InetAddress[] addresses =
                    InetAddress.getAllByName(normalizedHost);

            if (addresses.length == 0) {
                throw new IllegalArgumentException(
                        "Backend hostname did not resolve"
                );
            }

            for (InetAddress address : addresses) {

                if (isAlwaysRestrictedAddress(address)) {
                    throw new IllegalArgumentException(
                            "Backend resolves to a restricted network address"
                    );
                }

                if (!allowPrivateTargets
                        && isNonPublicAddress(address)) {

                    throw new IllegalArgumentException(
                            "Private or non-public backend addresses are not allowed"
                    );
                }
            }

        } catch (URISyntaxException
                 | UnknownHostException
                 | IllegalArgumentException exception) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid or restricted targetBaseUrl: "
                            + exception.getMessage()
            );
        }
    }

    private boolean isMetadataHostname(String host) {

        return host.equals("metadata.google.internal")
                || host.equals("metadata.google")
                || host.equals("metadata.azure.internal")
                || host.equals("instance-data")
                || host.equals("metadata");
    }

    private boolean isAlwaysRestrictedAddress(InetAddress address) {

        if (address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {

            return true;
        }

        byte[] bytes = address.getAddress();

        if (bytes.length == 4) {

            return (bytes[0] & 0xFF) == 169
                    && (bytes[1] & 0xFF) == 254
                    && (bytes[2] & 0xFF) == 169
                    && (bytes[3] & 0xFF) == 254;
        }

        if (address instanceof Inet6Address) {

            boolean ipv6Metadata =
                    (bytes[0] & 0xFF) == 0xFD
                            && (bytes[1] & 0xFF) == 0x00
                            && bytes[2] == 0xEC
                            && bytes[3] == 0x02;

            if (ipv6Metadata) {
                return true;
            }

            boolean ipv4Mapped =
                    bytes[0] == 0
                            && bytes[1] == 0
                            && bytes[2] == 0
                            && bytes[3] == 0
                            && bytes[4] == 0
                            && bytes[5] == 0
                            && bytes[6] == 0
                            && bytes[7] == 0
                            && bytes[8] == 0
                            && bytes[9] == 0
                            && (bytes[10] & 0xFF) == 0xFF
                            && (bytes[11] & 0xFF) == 0xFF;

            if (ipv4Mapped) {
                try {
                    InetAddress mappedAddress = InetAddress.getByAddress(
                            new byte[]{
                                    bytes[12], bytes[13], bytes[14], bytes[15]
                            }
                    );

                    return isAlwaysRestrictedAddress(mappedAddress)
                            || (!allowPrivateTargets
                            && isNonPublicAddress(mappedAddress));

                } catch (UnknownHostException exception) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isNonPublicAddress(InetAddress address) {

        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {

            return true;
        }

        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {

            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            int third = bytes[2] & 0xFF;

            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 0)
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113)
                    || first >= 224;
        }

        if (address instanceof Inet6Address) {

            int first = bytes[0] & 0xFF;

            return (first & 0xFE) == 0xFC
                    || address.isSiteLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isAnyLocalAddress();
        }

        return true;
    }

    private Set<String> normalizeMethods(Set<String> methods) {

        if (methods == null || methods.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "At least one allowed HTTP method is required"
            );
        }

        Set<String> normalized = new HashSet<>();

        for (String method : methods) {

            if (method == null || method.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "HTTP method cannot be empty"
                );
            }

            String value = method.toUpperCase(Locale.ROOT);

            try {
                HttpMethod.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported HTTP method: " + method
                );
            }

            normalized.add(value);
        }

        return normalized;
    }

    private void saveVersion(
            ApiRoute route,
            String changeType) {

        RouteConfigurationVersion version =
                new RouteConfigurationVersion();

        version.setOrganization(route.getOrganization());
        version.setRouteId(route.getRouteId());

        version.setVersionNumber(
                route.getConfigurationVersion()
        );

        version.setChangeType(changeType);

        version.setConfigurationSnapshot(
                objectMapper.writeValueAsString(
                        RouteResponse.from(route)
                )
        );

        version.setCreatedAt(LocalDateTime.now());

        versionRepository.save(version);
    }

    private List<RouteResponse> toResponses(
            List<ApiRoute> routes) {

        List<RouteResponse> response = new ArrayList<>();

        for (ApiRoute route : routes) {
            response.add(RouteResponse.from(route));
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<AuditRecordResponse> getAuditRecords() {

        Organization organization =
                tenantContext.getOrganization();

        List<AuditRecordResponse> response = new ArrayList<>();

        List<ControlPlaneAuditRecord> records =
                auditRecordRepository
                        .findAllByOrganizationIdOrderByCreatedAtDesc(
                                organization.getId()
                        );

        for (ControlPlaneAuditRecord record : records) {
            response.add(
                    AuditRecordResponse.from(record)
            );
        }

        return response;
    }
}
