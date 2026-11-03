package vn.group7.content.model;

import jakarta.persistence.*;

@Entity
@Table(name = "course_module")
public class CourseModuleEntity {
  @Id
  @Column(name = "module_id")
  private java.util.UUID moduleId;

  @Column(name = "course_id")
  private java.util.UUID courseId;

  @Column(name = "title", length = 200)
  private String title;

  @Column(name = "title_normalized", columnDefinition = "text")
  private String titleNormalized;

  @Column(name = "order_no")
  private Integer orderNo;

  @Column(name = "published")
  private Boolean published;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected CourseModuleEntity() {}
}
