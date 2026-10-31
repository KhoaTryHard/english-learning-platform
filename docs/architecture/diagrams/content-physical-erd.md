# Content physical ERD — artifact review #20

Nguồn: migration Content tại Khoa `bf183461e5647ad83c7ec16dc6c7d1953379b10e`.
Hình PNG cùng thư mục được tạo từ cùng quan hệ để bổ sung Word W3-03 local.
Đây là đối chiếu source; chờ Khoa/Huy review, không phải xác nhận baseline mới.

```mermaid
erDiagram
    COURSE ||--o{ LEVEL : contains
    COURSE ||--o{ COURSE_MODULE : contains
    COURSE_MODULE ||--o{ LESSON : contains
    LESSON ||--o{ VOCABULARY : contains
    LESSON ||--o{ LEARNING_CONTENT : contains
    VOCABULARY ||--o{ USER_VOCABULARY : saved_by
    COURSE {
        uuid course_id PK
        boolean published
    }
    LEVEL {
        uuid level_id PK
        uuid course_id FK
    }
    COURSE_MODULE {
        uuid module_id PK
        uuid course_id FK
        int order_no
        boolean published
    }
    LESSON {
        uuid lesson_id PK
        uuid module_id FK
        int order_no
        boolean published
    }
    VOCABULARY {
        uuid vocabulary_id PK
        uuid lesson_id FK
        text word_normalized
        text meaning_normalized
    }
    LEARNING_CONTENT {
        uuid content_id PK
        uuid lesson_id FK
        string type
        text text
        string resource_url
        boolean published
    }
    USER_VOCABULARY {
        uuid user_vocabulary_id PK
        uuid user_id "external ref, no cross-service FK"
        uuid vocabulary_id FK
    }
```

Mỗi child có đúng một parent local; parent có thể chưa có child, nên cardinality
là 1 → 0..*. UNIQUE Vocabulary: (lesson_id,word_normalized,meaning_normalized).
UNIQUE saved relation: (user_id,vocabulary_id). GRAMMAR/READING bắt buộc text;
LISTENING bắt buộc resource_url; availability là DTO derive. Không có Level→Module
FK hoặc FK User xuyên database. Đây là subset fields phục vụ diagram; SQL migration
là nguồn đầy đủ về columns/checks/index/grants.
