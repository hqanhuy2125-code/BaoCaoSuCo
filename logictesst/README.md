# LOG Java - bản sửa sau rà soát

- Java thuần trong `app/src/main/java/com/example/baocaosuco/logic`.
- Kiểm tra history draft đúng cặp `(expectedStatus, action, nextStatus)`; ngăn giả quyết định sai đích. Không thể xác thực role/owner từ `TransitionDecision` đơn lẻ: Repository/backend vẫn phải xác minh lại.
- `IncidentTransitionPlan` chứa eventId do nguồn tin cậy cấp, expectedStatus và history draft. `IncidentCommitPort` chỉ là interface, KHÔNG ghi Firebase; backend cần transaction với so sánh expectedStatus, eventId idempotent và ghi history cùng trạng thái.
- `integration_examples` chứa adapter thực hiện đúng chữ ký interface trong các feature ZIP của Huy. KHÔNG chép vào module LOG hoặc `main` nếu feature chưa merge. Resident adapter chỉ kiểm tra state, **không xác minh owner**; commit gateway phải xác minh auth.uid == reportOwner.
- `IncidentStatusCodec` chỉ chấp nhận canonical code tiếng Anh. Nhãn mock tiếng Việt không phải canonical; nhóm cần duyệt mapping trước khi tích hợp.
- COMPLETED active/terminal để tính quá hạn chưa chốt, không tự tạo StatusClassifier mặc định.
- SLA được truyền vào; không hardcode thời gian. Thông báo/push ngoài phạm vi bản này.
- Chưa kiểm tra Gradle/Android build và Firebase E2E. Các class Java và adapter đã được biên dịch riêng với javac trên snapshot feature.

## Cần làm khi tích hợp
1. Nhóm chốt CON-02, canonical status, SLA, và quy tắc COMPLETED.
2. Repository implement transaction an toàn: xác thực vai trò/chủ báo cáo, so sánh expectedStatus, chống duplicate eventId, ghi status+history atomically; chỉ báo thành công sau commit.
3. Đưa adapter vào nhánh tích hợp và chạy `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
4. Test emulator Firebase Security Rules và E2E trước khi merge.
