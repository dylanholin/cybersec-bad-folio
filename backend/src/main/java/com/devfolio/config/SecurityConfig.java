package com.devfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 🔴 A05-03 : CSRF désactivé sans justification
            .csrf(csrf -> csrf.disable())

            // 🔴 A01-05 : CORS ouvert à tous
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of("*"));
                config.setAllowedMethods(List.of("*"));
                config.setAllowedHeaders(List.of("*"));
                return config;
            }))

            .authorizeHttpRequests(auth -> auth
                // 🔴 A01-03 : endpoint admin accessible sans vérification de rôle
                .requestMatchers("/api/admin/**").permitAll()
                // 🔴 A05-01 : actuator sans protection
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                // 🔴 A01-01 : toutes les autres routes également ouvertes
                .anyRequest().permitAll()
            )

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 🔴 A02-01 : MD5 au lieu de BCrypt
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    byte[] hash = md.digest(raw.toString().getBytes());
                    return HexFormat.of().formatHex(hash);
                } catch (Exception e) {
                    return raw.toString();
                }
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return encode(raw).equals(encoded);
            }
        };
    }
}
