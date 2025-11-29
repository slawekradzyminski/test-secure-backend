package com.awesome.testing.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import jakarta.servlet.DispatcherType;

import java.io.IOException;
import java.util.List;

import static com.awesome.testing.utils.ErrorResponseDefinition.sendErrorResponse;

@SuppressWarnings("unused")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Order(2)
public class WebSecurityConfig {

    private static final List<String> ALLOWED_ENDPOINTS = List.of(
            "/users/signin",
            "/users/signup",
            "/users/refresh",
            "/h2-console/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/actuator/**",
            "/ws-traffic/**"
    );

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Disable CSRF as we use JWT
        http.csrf(AbstractHttpConfigurer::disable);

        // Enable CORS
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // No session will be created or used by Spring Security
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Define authorization rules
        http.authorizeHttpRequests(auth -> {
            // First, allow async dispatch
            auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll();
            
            // Then allow public endpoints
            PathPatternRequestMatcher.Builder matcherBuilder = PathPatternRequestMatcher.withDefaults();
            ALLOWED_ENDPOINTS.forEach(endpoint -> auth.requestMatchers(matcherBuilder.matcher(endpoint)).permitAll());
            
            // Finally, require authentication for all other endpoints
            auth.anyRequest().authenticated();
        });

        // Handle authentication and authorization errors
        http.exceptionHandling(ex -> ex
                .accessDeniedHandler(this::handleAccessDenied)
                .authenticationEntryPoint(this::handleUnauthorized)
        );

        // Apply JWT security filter
        http.addFilterBefore(new JwtTokenFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void handleAccessDenied(HttpServletRequest request,
                                    HttpServletResponse response,
                                    AccessDeniedException ex) throws IOException {
        sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void handleUnauthorized(HttpServletRequest request,
                                    HttpServletResponse response,
                                    AuthenticationException ex) throws IOException {
        sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:8081",
                "http://127.0.0.1:8081",
                "http://host.docker.internal:8081"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With", 
            "Accept", 
            "Cache-Control",
            "Last-Event-ID",
            "x-requested-with"
        ));
        configuration.setExposedHeaders(List.of(
            "Content-Type",
            "X-Requested-With",
            "Cache-Control",
            "Last-Event-ID"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
