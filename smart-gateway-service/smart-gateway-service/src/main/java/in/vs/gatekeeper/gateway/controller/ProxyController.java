package in.vs.gatekeeper.gateway.controller;

import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletRequest;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.web.client.ResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpHeaders;

import org.springframework.http.HttpMethod;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import org.springframework.stereotype.Controller;

import org.springframework.util.AntPathMatcher;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestMethod;

import org.springframework.web.client.RestClient;

import in.vs.gatekeeper.gateway.dto.GatewayAccessEvent;
import in.vs.gatekeeper.gateway.dto.GatewayRoute;
import in.vs.gatekeeper.gateway.dto.HeaderRule;
import in.vs.gatekeeper.gateway.exception.BackendDestinationException;
import in.vs.gatekeeper.gateway.service.ActiveRouteRegistry;
import in.vs.gatekeeper.gateway.service.GatewayAccessEventPublisher;
import in.vs.gatekeeper.gateway.service.MonthlyQuotaService;
import in.vs.gatekeeper.gateway.service.RateLimitService;

import java.io.IOException;

import java.net.IDN;

import java.net.Inet4Address;

import java.net.Inet6Address;

import java.net.InetAddress;

import java.net.URI;

import java.net.UnknownHostException;

import org.apache.hc.client5.http.DnsResolver;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

import java.time.Duration;

import java.util.Enumeration;

import java.util.HashSet;

import java.util.Locale;

import java.util.Set;

@Controller

public class ProxyController {

    private static final Logger log =

            LoggerFactory.getLogger(ProxyController.class);

    private static final AntPathMatcher PATH_MATCHER =

            new AntPathMatcher();

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(

            "connection",

            "keep-alive",

            "proxy-authenticate",

            "proxy-authorization",

            "te",

            "trailer",

            "transfer-encoding",

            "upgrade",

            "host",

            "content-length"

    );

    private final ActiveRouteRegistry routeRegistry;
    private final RateLimitService rateLimitService;
    private final GatewayAccessEventPublisher accessEventPublisher;
    private final MonthlyQuotaService monthlyQuotaService;
    @Value("${gatekeeper.routes.allow-private-targets:false}")

    private boolean allowPrivateTargets;

    @Value("${gatekeeper.proxy.max-request-size:10485760}")

    private int maxRequestSize;

    @Value("${gatekeeper.proxy.max-response-size:10485760}")

    private int maxResponseSize;

    public ProxyController(
            ActiveRouteRegistry routeRegistry,
            RateLimitService rateLimitService,
            GatewayAccessEventPublisher accessEventPublisher,
            MonthlyQuotaService monthlyQuotaService) {

        this.routeRegistry = routeRegistry;
        this.rateLimitService = rateLimitService;
        this.accessEventPublisher = accessEventPublisher;
        this.monthlyQuotaService = monthlyQuotaService;
    }

    @RequestMapping(value = "/**", method = {

            RequestMethod.GET,

            RequestMethod.POST,

            RequestMethod.PUT,

            RequestMethod.PATCH,

            RequestMethod.DELETE,

            RequestMethod.HEAD,

            RequestMethod.OPTIONS

    })

    public ResponseEntity<byte[]> proxy(HttpServletRequest incoming) {
    	long startTime = System.currentTimeMillis();
        String incomingPath = incoming.getRequestURI()

                .substring(incoming.getContextPath().length());

        String methodName = incoming.getMethod()

                .toUpperCase(Locale.ROOT);

        GatewayRoute matchedRoute = null;

        boolean pathMatched = false;

        for (GatewayRoute route : routeRegistry.getRoutes()) {

            if (route.getPathPattern() != null

                    && PATH_MATCHER.match(

                    route.getPathPattern(),

                    incomingPath)) {

                pathMatched = true;

                if (route.getAllowedMethods() != null

                        && route.getAllowedMethods().contains(methodName)) {

                    matchedRoute = route;

                    break;

                }

            }

        }

        if (matchedRoute == null) {

            if (pathMatched) {
                publishAccessEvent(
                        null, incoming,
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        startTime
                );

                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                        .build();
            }

            publishAccessEvent(
                    null, incoming,
                    HttpStatus.NOT_FOUND.value(),
                    startTime
            );

            return ResponseEntity.notFound().build();
        }

        final GatewayRoute route = matchedRoute;
        if (!rateLimitService.isAllowed(
                route.getOrganizationId(),
                route.getRouteId(),
                route.getRequestsPerMinute())) {

            log.warn(
                    "Rate limit exceeded for organization {} and route {}",
                    route.getOrganizationId(),
                    route.getRouteId()
            );
            publishAccessEvent(
                    route,
                    incoming,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    startTime
            );
            return ResponseEntity.status(
                    HttpStatus.TOO_MANY_REQUESTS
            ).build();
        }
     // Monthly organization quota enforcement
        if (!monthlyQuotaService.isAllowed(route)) {
            log.warn(
                    "Monthly quota exceeded for organization {} and route {}",
                    route.getOrganizationId(),
                    route.getRouteId()
            );

            publishAccessEvent(
                    route,
                    incoming,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    startTime,
                    false
            );

            return ResponseEntity.status(
                    HttpStatus.TOO_MANY_REQUESTS
            ).build();
        }
        try {

            String remainingPath =

                    PATH_MATCHER.extractPathWithinPattern(

                            route.getPathPattern(),

                            incomingPath

                    );

            String targetPath = joinPaths(

                    route.getTargetPathPrefix(),

                    remainingPath

            );

            URI targetUri = buildTargetUri(

                    route.getTargetBaseUrl(),

                    targetPath,

                    incoming.getQueryString()

            );

            InetAddress[] validatedAddresses =

                    validateBackendDestination(targetUri);

            HttpHeaders outboundHeaders =

                    copyRequestHeaders(incoming);

            applyHeaderRules(

                    outboundHeaders,

                    route,

                    "REQUEST"

            );

            int timeoutMs = route.getResponseTimeoutMs() == null

                    ? 5000

                    : Math.max(100, route.getResponseTimeoutMs());

            if (maxRequestSize <= 0 || maxResponseSize <= 0) {

                log.error("Gateway request and response size limits must be positive");

                return ResponseEntity.status(

                        HttpStatus.INTERNAL_SERVER_ERROR

                ).build();

            }

            long contentLength = incoming.getContentLengthLong();

            if (contentLength > maxRequestSize) {

                return ResponseEntity.status(

                        HttpStatus.PAYLOAD_TOO_LARGE

                ).build();

            }

            String validatedHost = targetUri.getHost();

            DnsResolver dnsResolver = new DnsResolver() {

                @Override

                public InetAddress[] resolve(String hostname)

                        throws UnknownHostException {

                    if (!hostname.equalsIgnoreCase(validatedHost)) {

                        throw new UnknownHostException(

                                "Unvalidated backend hostname: " + hostname

                        );

                    }

                    return validatedAddresses.clone();

                }

                @Override

                public String resolveCanonicalHostname(String hostname)

                        throws UnknownHostException {

                    resolve(hostname);

                    return hostname;

                }

            };

            var connectionManager =

                    PoolingHttpClientConnectionManagerBuilder.create()

                            .setDnsResolver(dnsResolver)

                            .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()

                    .setConnectionManager(connectionManager)

                    .disableRedirectHandling()

                    .build()) {

                HttpComponentsClientHttpRequestFactory requestFactory =

                        new HttpComponentsClientHttpRequestFactory(httpClient);

                requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

                RestClient client = RestClient.builder()

                        .requestFactory(requestFactory)

                        .build();

                byte[] requestBody = incoming.getInputStream()

                        .readNBytes(maxRequestSize + 1);

                if (requestBody.length > maxRequestSize) {

                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)

                            .build();

                }

                HttpMethod method = HttpMethod.valueOf(methodName);

                RestClient.RequestBodySpec requestSpec = client.method(method)

                        .uri(targetUri)

                        .headers(headers -> headers.addAll(outboundHeaders));

                if (!HttpMethod.GET.equals(method)

                        && !HttpMethod.HEAD.equals(method)) {

                    requestSpec.body(requestBody);

                }

                return requestSpec.exchange((request, response) -> {

                    HttpHeaders responseHeaders =

                            copyEndToEndHeaders(response.getHeaders());

                    applyHeaderRules(

                            responseHeaders,

                            route,

                            "RESPONSE"

                    );

                    byte[] responseBody = response.getBody()

                            .readNBytes(maxResponseSize + 1);

                    if (responseBody.length > maxResponseSize) {

                        log.warn(

                                "Backend response exceeded configured limit for route {}",

                                route.getRouteId()

                        );

                        return ResponseEntity.status(

                                HttpStatus.BAD_GATEWAY

                        ).build();

                    }

                    if (HttpMethod.HEAD.equals(method)

                            || response.getStatusCode().is3xxRedirection()

                            || response.getStatusCode().value() == 204

                            || response.getStatusCode().value() == 304) {

                        responseBody = new byte[0];

                    }
                    publishAccessEvent(
                            route,
                            incoming,
                            response.getStatusCode().value(),
                            startTime
                    );
                    return ResponseEntity

                            .status(response.getStatusCode())

                            .headers(responseHeaders)

                            .body(responseBody);

                });

            }

        } catch (ResourceAccessException exception) {

            boolean timeout = isTimeoutException(exception);

            HttpStatus status = timeout
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.BAD_GATEWAY;

            log.error(
                    "Backend request failed for route {}",
                    route.getRouteId(),
                    exception
            );

            publishAccessEvent(
                    route,
                    incoming,
                    status.value(),
                    startTime
            );

            return ResponseEntity.status(status).build();

        } catch (IOException exception) {
            log.error(
                    "Request body processing failed for route {}",
                    route.getRouteId(),
                    exception
            );

            publishAccessEvent(
                    route,
                    incoming,
                    HttpStatus.BAD_REQUEST.value(),
                    startTime
            );

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private HttpHeaders copyEndToEndHeaders(

        HttpHeaders source) {

    HttpHeaders headers = new HttpHeaders();

    Set<String> excludedHeaders = getConnectionHeaders(

            source.getOrEmpty(HttpHeaders.CONNECTION)

    );

    source.forEach((name, values) -> {

        if (isEndToEndHeader(name, excludedHeaders)) {

            headers.put(name, values);

        }

    });

    return headers;

}

private HttpHeaders copyRequestHeaders(

        HttpServletRequest request) {

    HttpHeaders headers = new HttpHeaders();

    Set<String> excludedHeaders = new HashSet<>();

    Enumeration<String> connectionValues =

            request.getHeaders("Connection");

    while (connectionValues.hasMoreElements()) {

        String value = connectionValues.nextElement();

        for (String token : value.split(",")) {

            String header = token.trim()

                    .toLowerCase(Locale.ROOT);

            if (!header.isEmpty()) {

                excludedHeaders.add(header);

            }

        }

    }

    Enumeration<String> names = request.getHeaderNames();

    if (names == null) {

        return headers;

    }

    while (names.hasMoreElements()) {

        String name = names.nextElement();

        if (isEndToEndHeader(name, excludedHeaders)) {

            Enumeration<String> values =

                    request.getHeaders(name);

            while (values.hasMoreElements()) {

                headers.add(name, values.nextElement());

            }

        }

    }

    return headers;

}

    private Set<String> getConnectionHeaders(

            Iterable<String> connectionValues) {

        Set<String> excluded = new HashSet<>();

        for (String value : connectionValues) {

            if (value == null) {

                continue;

            }

            for (String token : value.split(",")) {

                String header = token.trim()

                        .toLowerCase(Locale.ROOT);

                if (!header.isEmpty()) {

                    excluded.add(header);

                }

            }

        }

        return excluded;

    }

    private boolean isEndToEndHeader(

            String name,

            Set<String> excludedHeaders) {

        String normalized = name.toLowerCase(Locale.ROOT);

        return !HOP_BY_HOP_HEADERS.contains(normalized)

                && !excludedHeaders.contains(normalized);

    }

    private void applyHeaderRules(

            HttpHeaders headers,

            GatewayRoute route,

            String direction) {

        if (route.getHeaderRules() == null) {

            return;

        }

        for (HeaderRule rule : route.getHeaderRules()) {

            if (rule.getDirection() == null

                    || !direction.equalsIgnoreCase(rule.getDirection())

                    || rule.getHeaderName() == null

                    || rule.getHeaderName().isBlank()) {

                continue;

            }

            if ("REMOVE".equalsIgnoreCase(rule.getAction())) {

                headers.remove(rule.getHeaderName());

            } else if ("ADD".equalsIgnoreCase(rule.getAction())

                    && rule.getHeaderValue() != null) {

                headers.set(

                        rule.getHeaderName(),

                        rule.getHeaderValue()

                );

            }

        }

    }

    private String joinPaths(

            String prefix,

            String remainingPath) {

        String left = prefix == null || prefix.isBlank()

                ? "/"

                : prefix;

        String right = remainingPath == null

                ? ""

                : remainingPath;

        String joined =

                (left.endsWith("/") ? left : left + "/")

                        + (right.startsWith("/")

                        ? right.substring(1)

                        : right);

        return joined.replaceAll("/{2,}", "/");

    }

    private URI buildTargetUri(

            String baseUrl,

            String targetPath,

            String query) {

        if (baseUrl == null || baseUrl.isBlank()) {

            throw new IllegalArgumentException(

                    "Route targetBaseUrl is missing"

            );

        }

        if (targetPath == null

                || targetPath.isBlank()

                || !targetPath.startsWith("/")

                || targetPath.contains("\\\\")

                || targetPath.contains("#")

                || targetPath.contains("\r")

                || targetPath.contains("\n")) {

            throw new IllegalArgumentException(

                    "Invalid backend target path"

            );

        }

        for (String segment : targetPath.split("/")) {

            if (".".equals(segment) || "..".equals(segment)) {

                throw new IllegalArgumentException(

                        "Path traversal is not allowed"

                );

            }

        }

        URI baseUri = URI.create(baseUrl);

        if (!baseUri.isAbsolute()

                || baseUri.getHost() == null

                || baseUri.getRawUserInfo() != null

                || baseUri.getRawQuery() != null

                || baseUri.getRawFragment() != null

                || baseUri.getPath().contains("\\\\")) {

            throw new IllegalArgumentException(

                    "Invalid backend base URL"

            );

        }

        String base = baseUrl.endsWith("/")

                ? baseUrl.substring(0, baseUrl.length() - 1)

                : baseUrl;

        String uri = base + targetPath

                + (query == null || query.isBlank()

                ? ""

                : "?" + query);

        URI targetUri = URI.create(uri);

        if (targetUri.getRawFragment() != null) {

            throw new IllegalArgumentException(

                    "Backend URL fragments are not allowed"

            );

        }

        return targetUri;

    }

private InetAddress[] validateBackendDestination(URI targetUri) {

    String scheme = targetUri.getScheme();

    if (scheme == null

            || (!"http".equalsIgnoreCase(scheme)

            && !"https".equalsIgnoreCase(scheme))) {

        throw new IllegalArgumentException(

                "Only HTTP and HTTPS backend destinations are allowed"

        );

    }

    String host = targetUri.getHost();

    if (host == null || host.isBlank()) {

        throw new IllegalArgumentException(

                "Backend hostname is missing"

        );

    }

    String normalizedHost = host.toLowerCase(Locale.ROOT);

    if (normalizedHost.contains("%")) {

        throw new BackendDestinationException("IPv6 zone identifiers are not allowed");

    }

    if (!normalizedHost.contains(":")) {

        normalizedHost = IDN.toASCII(normalizedHost);

    }

    if (normalizedHost.endsWith(".")) {

        normalizedHost = normalizedHost.substring(

                0,

                normalizedHost.length() - 1

        );

    }

    if (normalizedHost.equals("metadata")

            || normalizedHost.equals("instance-data")

            || normalizedHost.equals("metadata.google")

            || normalizedHost.equals("metadata.google.internal")

            || normalizedHost.equals("metadata.azure.internal")) {

        throw new BackendDestinationException("Cloud metadata destinations are not allowed");

    }

    try {

        InetAddress[] addresses =

                InetAddress.getAllByName(normalizedHost);

        if (addresses.length == 0) {

            throw new IllegalArgumentException(

                    "Backend hostname did not resolve"

            );

        }

        for (InetAddress address : addresses) {

            if (isRestrictedAddress(address)) {

                throw new BackendDestinationException("Backend resolves to a restricted network address");

            }

            if (!allowPrivateTargets

                    && isNonPublicAddress(address)) {

                throw new BackendDestinationException("Private backend addresses are not allowed");

            }

        }

        return addresses;

    } catch (IOException exception) {

        throw new IllegalArgumentException(

                "Unable to resolve backend hostname",

                exception

        );

    }

}

    private boolean isRestrictedAddress(InetAddress address) {

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

                    InetAddress mapped = InetAddress.getByAddress(

                            new byte[]{

                                    bytes[12],

                                    bytes[13],

                                    bytes[14],

                                    bytes[15]

                            }

                    );

                    return isRestrictedAddress(mapped)

                            || (!allowPrivateTargets

                            && isNonPublicAddress(mapped));

                } catch (IOException exception) {

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

                    || address.isLoopbackAddress()

                    || address.isSiteLocalAddress()

                    || address.isAnyLocalAddress();

        }

        return true;

    }
    private boolean isTimeoutException(Throwable exception) {

        Throwable current = exception;

        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
    private void publishAccessEvent(
            GatewayRoute route,
            HttpServletRequest request,
            int status,
            long startTime) {

        // Existing events remain billable by default.
        publishAccessEvent(route, request, status, startTime, true);
    }

    private void publishAccessEvent(
            GatewayRoute route,
            HttpServletRequest request,
            int status,
            long startTime,
            boolean billable) {

        try {
            GatewayAccessEvent event = new GatewayAccessEvent(
                    java.util.UUID.randomUUID().toString(),
                    route != null ? route.getOrganizationId() : null,
                    route != null ? route.getRouteId() : null,
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    System.currentTimeMillis() - startTime,
                    LocalDateTime.now(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    billable
            );

            accessEventPublisher.publish(event);

        } catch (Exception exception) {
            log.error("Unable to create gateway access event", exception);
        }
    }
}
