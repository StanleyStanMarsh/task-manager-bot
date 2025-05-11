package ru.spbstu.hsai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;
import ru.spbstu.hsai.usermanagement.service.UserService;
import reactor.core.publisher.Mono;

@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

    private final UserServiceInterface userService;
    private final String superAdminId;

    public SecurityConfig(UserServiceInterface userService,
                          @Value("${superadmin.telegramId}") String superAdminId) {
        this.userService = userService;
        this.superAdminId = superAdminId;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/healtcheck").permitAll()
                        .pathMatchers("/users/{telegramId}/demote").hasAuthority("SUPER_ADMIN")
                        .pathMatchers("/users/{telegramId}/promote", "/self_demote", "/users").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .anyExchange().permitAll()
                )
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Access to API\"");
                            exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                            return exchange.getResponse().writeWith(
                                    Mono.just(exchange.getResponse().bufferFactory().wrap("Access Denied".getBytes()))
                            );
                        })
                )
                .build();
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        return username -> {
            Long telegramId = Long.valueOf(username);
            return userService.findByTelegramId(telegramId)
                    .switchIfEmpty(Mono.empty())
                    .map(domainUser -> {
                        String role = domainUser.getRole();
                        if (superAdminId.equals(username)) {
                            role = "SUPER_ADMIN";
                        }
                        return User.withUsername(username)
                                .password(domainUser.getPassword())
                                .authorities(role)
                                .build();
                    });
        };
    }
}