
package in.vs.gatekeeper.management.config;

import java.util.Base64;
import java.util.Collections;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // Safe for the current stateless Bearer-token API design.
            // Revisit if authentication moves to cookies.
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/", "/index.html", "/styles.css", "/app.js",
                    "/favicon.ico", "/health", "/api/auth/login"
                ).permitAll()

                .requestMatchers("/api/platform/**")
                    .hasRole("PLATFORM_ADMIN")

                // Usage and billing are view-only for tenants: GET is allowed,
                // any other HTTP method is rejected before the generic rule.
                .requestMatchers(HttpMethod.GET,
                    "/api/tenant/usage/**", "/api/tenant/billing/**")
                    .hasRole("TENANT_ADMIN")

                .requestMatchers(
                    "/api/tenant/usage/**", "/api/tenant/billing/**")
                    .denyAll()

                .requestMatchers("/api/tenant/**")
                    .hasRole("TENANT_ADMIN")

                .requestMatchers("/api/gateway/**")
                    .hasRole("GATEWAY_SERVICE")

                .anyRequest().authenticated()
            )

            .oauth2ResourceServer(resourceServer ->
                resourceServer.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${security.jwt.secret}") String secret) {

        SecretKey key = jwtSecretKey(secret);

        return NimbusJwtEncoder
            .withSecretKey(key)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${security.jwt.secret}") String secret) {

        SecretKey key = jwtSecretKey(secret);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        OAuth2TokenValidator<Jwt> validator =
            JwtValidators.createDefault();

        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(validator)
        );

        return decoder;
    }

    private SecretKey jwtSecretKey(String secret) {

        final byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "JWT secret must be a valid Base64-encoded value",
                exception
            );
        }

        if (decoded.length < 32) {
            throw new IllegalStateException(
                "JWT secret must contain at least 32 decoded bytes"
            );
        }

        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    private Converter<Jwt, AbstractAuthenticationToken>
            jwtAuthenticationConverter() {

        return jwt -> {
            String role = jwt.getClaimAsString("role");

            if (role == null || role.isBlank()) {
                return new JwtAuthenticationToken(
                    jwt,
                    Collections.emptyList(),
                    jwt.getSubject()
                );
            }

            return new JwtAuthenticationToken(
                jwt,
                Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + role)
                ),
                jwt.getSubject()
            );
        };
    }
}
