package vn.group7.content.model;

import jakarta.persistence.*;

@Entity
@Table(name = "learning_content")
public class LearningContentEntity {
  @Id
  @Column(name = "content_id")
  private java.util.UUID contentId;

  @Column(name = "lesson_id")
  private java.util.UUID lessonId;

  @Column(name = "type", length = 16)
  private String type;

  @Column(name = "title", length = 200)
  private String title;

  @Column(name = "text", columnDefinition = "text")
  private String text;

  @Column(name = "resource_url", length = 2048)
  private String resourceUrl;

  @Column(name = "published")
  private Boolean published;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected LearningContentEntity() {}
}
