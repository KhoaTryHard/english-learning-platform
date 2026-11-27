package vn.group7.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "learner_profile")
public class LearnerProfileEntity {
  @Id
  @Column(name = "profile_id")
  private java.util.UUID profileId;

  @Column(name = "user_id")
  private java.util.UUID userId;

  @Column(name = "display_name", length = 100)
  private String displayName;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected LearnerProfileEntity() {}
}
