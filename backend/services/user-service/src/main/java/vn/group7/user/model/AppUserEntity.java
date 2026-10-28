package vn.group7.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUserEntity {
  @Id
  @Column(name = "user_id")
  private java.util.UUID userId;

  @Column(name = "email_normalized", length = 254)
  private String emailNormalized;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "role", length = 16)
  private String role;

  @Column(name = "status", length = 16)
  private String status;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected AppUserEntity() {}
}
