package vn.group7.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "revoked_token")
public class RevokedTokenEntity {
  @Id
  @Column(name = "jti")
  private java.util.UUID jti;

  @Column(name = "expires_at")
  private java.time.Instant expiresAt;

  protected RevokedTokenEntity() {}
}
