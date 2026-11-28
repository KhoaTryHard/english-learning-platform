# Event contract — Huy

AssessmentCompleted theo DD-10/API v0.1. Huy đã đưa schema và example approved từ
Drive W3 vào thư mục này, giữ nguyên bytes và nhãn lịch sử trong file.

Schema canonical: `AssessmentCompleted_v1.schema.json`; example:
`AssessmentCompleted_v1.example.json`. Service standalone mang bản copy để build
độc lập; chạy `python scripts/check-huy-contracts.py` tại root để kiểm hash đồng nhất.

Assessment kiểm event đầu ra trong test; Progress validate schema và business
guards lúc consume. Producer/consumer DB tests có evidence riêng; Kafka E2E chưa
chạy. Thay contract phải được review/version, không tự sửa payload/schema.
