package vn.group7.content.model;

import jakarta.persistence.*;

@Entity
@Table(name = "level")
public class LevelEntity {
  @Id
  @Column(name = "level_id")
  private java.util.UUID levelId;

  @Column(name = "course_id")
  private java.util.UUID courseId;

  @Column(name = "name", length = 200)
  private String name;

  @Column(name = "name_normalized", columnDefinition = "text")
  private String nameNormalized;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected LevelEntity() {}
}
