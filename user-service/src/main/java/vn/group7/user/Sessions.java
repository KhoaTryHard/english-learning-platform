package vn.group7.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 900)
public class Sessions {
  @Bean
  DefaultCookieSerializer cookie(@Value("${platform.secure-cookies}") boolean secure) {
    var c = new DefaultCookieSerializer();
    c.setCookieName("csrf_session");
    c.setCookiePath("/api/v1");
    c.setUseHttpOnlyCookie(true);
    c.setUseSecureCookie(secure);
    c.setSameSite("Lax");
    c.setUseBase64Encoding(false);
    return c;
  }
}
