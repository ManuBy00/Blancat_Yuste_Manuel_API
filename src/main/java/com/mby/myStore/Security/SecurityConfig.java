package com.mby.myStore.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. ACTIVAR CORS con la configuración definida abajo
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. DESACTIVAR CSRF (No es necesario para APIs con JWT)
                .csrf(csrf -> csrf.disable())

                // 3. CONFIGURAR PERMISOS DE RUTAS
                .authorizeHttpRequests(auth -> auth
                        // Endpoints de autenticación y Swagger son públicos
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Ejemplo de ruta protegida solo para ADMIN
                        // .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Todo lo demás requiere estar autenticado
                        .anyRequest().authenticated()
                )

                // 4. POLÍTICA DE SESIÓN: Sin estado (Stateless)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 5. FILTRO JWT: Se ejecuta antes del filtro de usuario/password
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // --- CONFIGURACIÓN DE CORS ---
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Orígenes permitidos
        configuration.setAllowedOrigins(List.of(
                "http://localhost:4200",   // Angular
                "http://10.0.2.2:8080" //  emulador android

        ));

        // Permitimos los métodos HTTP estándar
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Permitimos todas las cabeceras (necesario para enviar el Token en Authorization)
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));

        // Permitimos el envío de credenciales
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}