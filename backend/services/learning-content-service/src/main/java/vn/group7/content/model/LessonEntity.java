package vn.group7.content.model;

import jakarta.persistence.*;

@Entity
@Table(name = "lesson")
public class LessonEntity {
  @Id
  @Column(name = "lesson_id")
  private java.util.UUID lessonId;

  @Column(name = "module_id")
  private java.util.UUID moduleId;

  @Column(name = "title", length = 200)
  private String title;

  @Column(name = "title_normalized", columnDefinition = "text")
  private String titleNormalized;

  @Column(name = "content", columnDefinition = "text")
  private String content;

  @Column(name = "order_no")
  private Integer orderNo;

  @Column(name = "published")
  private Boolean published;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected LessonEntity() {}
}
