package vn.group7.core;

import org.springframework.context.annotation.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class Passwords {
  @Bean
  BCryptPasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService unusedFormLogin() {
    return name -> {
      throw new UsernameNotFoundException("Form login disabled");
    };
  }
}
