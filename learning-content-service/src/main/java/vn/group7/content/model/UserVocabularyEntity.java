package vn.group7.content.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_vocabulary")
public class UserVocabularyEntity {
  @Id
  @Column(name = "user_vocabulary_id")
  private java.util.UUID userVocabularyId;

  @Column(name = "user_id")
  private java.util.UUID userId;

  @Column(name = "vocabulary_id")
  private java.util.UUID vocabularyId;

  @Column(name = "added_at")
  private java.time.Instant addedAt;

  protected UserVocabularyEntity() {}
}
