package vn.group7.content.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vocabulary")
public class VocabularyEntity {
  @Id
  @Column(name = "vocabulary_id")
  private java.util.UUID vocabularyId;

  @Column(name = "lesson_id")
  private java.util.UUID lessonId;

  @Column(name = "word", length = 128)
  private String word;

  @Column(name = "meaning", length = 500)
  private String meaning;

  @Column(name = "example", length = 1000)
  private String example;

  @Column(name = "phonetic", length = 128)
  private String phonetic;

  @Column(name = "word_normalized", columnDefinition = "text")
  private String wordNormalized;

  @Column(name = "meaning_normalized", columnDefinition = "text")
  private String meaningNormalized;

  @Column(name = "published")
  private Boolean published;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  protected VocabularyEntity() {}
}
