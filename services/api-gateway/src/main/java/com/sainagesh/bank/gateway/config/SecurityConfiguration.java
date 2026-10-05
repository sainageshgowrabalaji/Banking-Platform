package com.sainagesh.bank.gateway.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may come in.
 *
 * <p>A client signs in with Keycloak and gets a signed token (a JWT). It sends the token with every
 * request. The gateway checks the signature against Keycloak's public keys, so it never calls Keycloak
 * for a request and never sees a password. The token also says which roles the user has.
 *
 * <ul>
 *   <li>Health and info endpoints are open
 *   <li>Endpoints that move money directly in the ledger need the operator role. A customer can never
 *       post a journal entry, however valid their token is
 *   <li>Everything else needs any valid token
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    /**
     * One bean decides, from one typed setting. With two beans that each wait for an exact text, a
     * value such as "yes" would match neither, and Spring Boot's own default rules would take over
     * without the operator checks.
     */
    @Bean
    SecurityFilterChain security(HttpSecurity http, GatewayProperties properties) throws Exception {
        return properties.security().enabled() ? tokensRequired(http, properties) : open(http);
    }

    private SecurityFilterChain tokensRequired(HttpSecurity http, GatewayProperties properties) throws Exception {
        String operator = properties.security().operatorRole();
        http
                // No cookies and no sessions, so there is nothing for a forged cross-site request to ride on.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v1/info", "/*/v1/info", "/fallback/**")
                        .permitAll()
                        // The gateway's own metrics, and the metrics of every service behind it.
                        .requestMatchers("/actuator/**", "/*/actuator/**")
                        .hasRole(operator)
                        .requestMatchers("/ledger/v1/journal-entries/**", "/ledger/v1/holds/**")
                        .hasRole(operator)
                        .requestMatchers(HttpMethod.POST, "/ledger/v1/accounts/*/freeze", "/ledger/v1/accounts/*/unfreeze")
                        .hasRole(operator)
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoles()))
                        .authenticationEntryPoint((request, response, e) ->
                                problem(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid access token is required"))
                        .accessDeniedHandler((request, response, e) ->
                                problem(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Your account is not allowed to do this")));
        return http.build();
    }

    /** For a laptop with no Keycloak running. Never use it anywhere real. */
    private SecurityFilterChain open(HttpSecurity http) throws Exception {
        log.warn("Token checks are OFF (bank.gateway.security.enabled=false). Every request is let through.");
        http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Keycloak lists a user's roles in the token under {@code realm_access.roles}. Spring Security
     * expects them as authorities named {@code ROLE_...}. This turns one into the other.
     */
    static JwtAuthenticationConverter keycloakRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfiguration::rolesOf);
        return converter;
    }

    private static Collection<GrantedAuthority> rolesOf(Jwt token) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = token.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        return authorities;
    }

    /** Auth errors in the same problem details format as every other error. */
    private static void problem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.getWriter()
                .write("{\"type\":\"https://errors.bank.example/" + code + "\",\"title\":\"" + status.getReasonPhrase()
                        + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}
