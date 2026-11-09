package vn.group7.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "learning_setup")
public class LearningSetupEntity {
  @Id
  @Column(name = "user_id")
  private java.util.UUID userId;

  @Column(name = "current_course_id")
  private java.util.UUID currentCourseId;

  @Column(name = "current_level_id")
  private java.util.UUID currentLevelId;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected LearningSetupEntity() {}
}
