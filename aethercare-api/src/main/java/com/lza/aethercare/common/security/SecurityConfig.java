package com.lza.aethercare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 主設定：拆兩個 SecurityFilterChain：
 * <ol>
 *   <li>{@code managementSecurityFilterChain}（@Order(1)）：跑在獨立 management
 *       port（預設 9001），HTTP Basic auth + actuator role 限制；
 *       /actuator/health 與 /actuator/info 公開（給 k8s probe 用）。</li>
 *   <li>{@code apiSecurityFilterChain}（@Order(2)）：主 API 8080，
 *       JWT bearer token + USER role；CSRF disable + stateless。</li>
 * </ol>
 *
 * <p>分離 port + 不同認證機制 = 物理隔離，production 可用 NetworkPolicy
 * 限制 9001 只給內網 / monitoring 服務。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private static final String DEV_DEFAULT_ACTUATOR_PASSWORD = "actuator-dev-pass";
    private static final String DEV_DEFAULT_JWT_SECRET =
            "Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tcGxlYXNlLW9yLXlvdS1nZXQtaGFja2VkLTIwMjY=";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Value("${aethercare.security.cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsCsv;

    @Value("${aethercare.security.actuator.username:actuator}")
    private String actuatorUsername;

    @Value("${aethercare.security.actuator.password:" + DEV_DEFAULT_ACTUATOR_PASSWORD + "}")
    private String actuatorPassword;

    @Value("${aethercare.security.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.profiles.active:local}")
    private String activeProfiles;

    /**
     * production profile 啟動時驗證敏感設定都已被 env 覆蓋（actuator password + JWT secret）；
     * 若仍為 dev 預設值則 fail-fast 拒絕啟動，避免被部署到 prod 後成為攻擊面。
     */
    @PostConstruct
    public void validateSecrets() {
        boolean isProd = activeProfiles != null && activeProfiles.contains("prod");
        if (isProd) {
            if (DEV_DEFAULT_ACTUATOR_PASSWORD.equals(actuatorPassword)) {
                throw new IllegalStateException(
                        "production profile 啟動但 actuator password 仍為 dev 預設；"
                                + "請設 AETHERCARE_ACTUATOR_PASSWORD env");
            }
            if (DEV_DEFAULT_JWT_SECRET.equals(jwtSecret)) {
                throw new IllegalStateException(
                        "production profile 啟動但 JWT secret 仍為 dev 預設；"
                                + "請設 AETHERCARE_JWT_SECRET env");
            }
        } else {
            if (DEV_DEFAULT_ACTUATOR_PASSWORD.equals(actuatorPassword)) {
                log.warn("⚠️ actuator dev 預設密碼，production 必須以 AETHERCARE_ACTUATOR_PASSWORD 覆蓋");
            }
            if (DEV_DEFAULT_JWT_SECRET.equals(jwtSecret)) {
                log.warn("⚠️ JWT 用 dev 預設 secret，production 必須以 AETHERCARE_JWT_SECRET 覆蓋");
            }
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * Management chain：跑在獨立 port，basic auth。
     * health / info 公開給 k8s probe；其他 actuator 需 ACTUATOR role。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        // dev 預設值警告已移到 validateSecrets() 集中處理
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        .anyRequest().hasRole("ACTUATOR"))
                .httpBasic(Customizer.withDefaults())
                .userDetailsService(actuatorUserDetailsService());
        return http.build();
    }

    /**
     * API chain：主 API 8080，JWT bearer token。
     * /actuator/** 不再列入，因為已搬到 management port 由另一個 chain 處理。
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/logout").hasRole("USER")
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/ping").permitAll()
                        .requestMatchers("/api/v1/care-**", "/api/v1/care-**/**", "/api/v1/workflows/**",
                                "/api/v1/elders/**", "/api/v1/sla/**").hasRole("USER")
                        .requestMatchers("/api/v1/insurance/**").hasRole("INSURANCE")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenAccessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Actuator chain 專用 InMemoryUserDetailsManager，與 AppUser DB 無關。 */
    private InMemoryUserDetailsManager actuatorUserDetailsService() {
        UserDetails actuator = User.builder()
                .username(actuatorUsername)
                .password(passwordEncoder().encode(actuatorPassword))
                .roles("ACTUATOR")
                .build();
        return new InMemoryUserDetailsManager(actuator);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private org.springframework.security.web.AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> writeProblem(
                response, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED, authException.getMessage());
    }

    private AccessDeniedHandler forbiddenAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeProblem(
                response, HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN, accessDeniedException.getMessage());
    }

    private void writeProblem(HttpServletResponse response, int status, ErrorCode code, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(code.getMessage());
        problem.setDetail(detail);
        problem.setProperty("code", code.name());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
