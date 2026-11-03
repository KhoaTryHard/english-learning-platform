package vn.group7.core;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type =
        org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class Security {
  @Bean
  SecurityFilterChain filterChain(HttpSecurity h) throws Exception {
    return h.csrf(c -> c.disable())
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .build();
  }
}
