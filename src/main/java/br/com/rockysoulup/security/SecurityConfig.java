package br.com.rockysoulup.security;

import br.com.rockysoulup.dao.UsuarioDAO;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.cors.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecretKey jwtKey(SecurityProperties p) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(p.jwtSecret()); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT_SECRET deve ser Base64 válido."); }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET precisa de pelo menos 32 bytes aleatórios.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, SecurityProperties p, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null &&
                    jwt.getAudience().contains(p.audience()) && jwt.getSubject() != null &&
                    jwt.getSubject().matches("[1-9][0-9]{0,18}") && jwt.hasClaim("ver") &&
                    !jwt.getIssuedAt().isAfter(clock.instant()) && jwt.getExpiresAt().isAfter(jwt.getIssuedAt()) &&
                    Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() <= p.tokenSeconds();
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(p.issuer()), claims));
        return decoder;
    }
    @Bean CorsConfigurationSource corsConfigurationSource(SecurityProperties p) {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : p.allowedOrigins()) {
            java.net.URI uri = java.net.URI.create(origin);
            if (origin.contains("*") || uri.getHost() == null || !(uri.getScheme().equals("https") || uri.getScheme().equals("http")) ||
                    uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
                    (uri.getPath() != null && !uri.getPath().isEmpty())) throw new IllegalStateException("Origem CORS inválida.");
        }
        config.setAllowedOrigins(p.allowedOrigins());
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",config); return source;
    }
    @Bean org.springframework.web.filter.CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        var filter = new org.springframework.web.filter.CorsFilter(corsConfigurationSource);
        filter.setCorsProcessor(new org.springframework.web.cors.DefaultCorsProcessor() {
            @Override protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) throws java.io.IOException {
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                response.getBody().write("{\"status\":403,\"mensagem\":\"Preflight CORS não autorizado.\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                response.flush();
            }
        });
        return filter;
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.CorsFilter> corsRegistration(org.springframework.web.filter.CorsFilter corsFilter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(corsFilter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean org.springframework.security.oauth2.server.resource.web.BearerTokenResolver bearerTokens() {
        var resolver = new org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver();
        resolver.setAllowFormEncodedBodyParameter(false);
        resolver.setAllowUriQueryParameter(false);
        return resolver;
    }
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain security(HttpSecurity http, UsuarioDAO users, SecurityJson errors,
            org.springframework.security.oauth2.server.resource.web.BearerTokenResolver bearerTokens) throws Exception {
        http.csrf(csrf -> csrf.disable()) // Exclusively Authorization: Bearer; no cookie/session authentication.
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
            .authorizeHttpRequests(routes -> routes
                .requestMatchers(HttpMethod.GET,"/health").permitAll()
                .requestMatchers(HttpMethod.GET,"/roletas","/verificacoes/{id}/roleta").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.POST,"/verificacoes/{id}/roleta").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.POST,"/auth/login","/auth/cadastro").permitAll()
                .requestMatchers(HttpMethod.GET,"/auth/sessao").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.POST,"/auth/logout").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.POST,"/verificacoes","/verificacoes/desafio").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.GET,"/verificacoes","/verificacoes/{id}","/verificacoes/{id}/evidencia").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/verificacoes/{id}/evidencia").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.POST,"/admin/verificacoes/{id}/revisao","/admin/verificacoes/{id}/reprocessar","/admin/ia/suspensao").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/admin/ia/consumo").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/usuario").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/usuario/{id}").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.PUT,"/usuario/{id}").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.PUT,"/usuario/{id}/saldo").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/usuario/{id}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/acao","/acao/{id}","/carta","/carta/{id}","/selo","/selo/{id}","/recompensa","/recompensa/{id}","/historico","/historico/{id}","/usuario-carta","/usuario-carta/{usuarioId}/{cartaId}","/usuario-fragmento","/usuario-fragmento/{usuarioId}/{raridade}").hasAnyRole("USER","ADMIN")
                .requestMatchers(HttpMethod.POST,"/acao","/carta","/selo","/recompensa","/historico","/usuario-carta","/usuario-fragmento").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/acao/{id}","/carta/{id}","/selo/{id}","/recompensa/{id}","/historico/{id}","/usuario-carta/{usuarioId}/{cartaId}","/usuario-fragmento/{usuarioId}/{raridade}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/acao/{id}","/carta/{id}","/selo/{id}","/recompensa/{id}","/historico/{id}","/usuario-carta/{usuarioId}/{cartaId}","/usuario-fragmento/{usuarioId}/{raridade}").hasRole("ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(resource -> resource.bearerTokenResolver(bearerTokens).authenticationEntryPoint(errors).accessDeniedHandler(errors)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    try {
                        Account account = users.identity(Long.parseLong(token.getSubject())).orElseThrow(() -> new IllegalArgumentException());
                        Object version = token.getClaims().get("ver");
                        if (!account.active() || !(version instanceof Number number) || number.longValue() != account.tokenVersion() ||
                                !(account.role().equals("USER") || account.role().equals("ADMIN"))) throw new IllegalArgumentException();
                        return new JwtAuthenticationToken(token,List.of(new SimpleGrantedAuthority("ROLE_" + account.role())),token.getSubject());
                    } catch (RuntimeException e) {
                        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
                    }
                })));
        return http.build();
    }
}
