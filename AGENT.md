# AGENT.md – Quy tắc cho AI hỗ trợ dự án

File này dành cho AI (Gemini trong Android Studio hoặc công cụ khác). Đọc hết trước khi trả lời hoặc viết code.

## Bối cảnh

Đồ án môn Lập trình ứng dụng di động, nhóm 4 sinh viên, đề tài "Ký túc xá – Báo cáo & xử lý sự cố". Các thành viên mới học lập trình Android, từng làm web. Thời gian làm khoảng 5 tuần, tuần 6 báo cáo.

## Ràng buộc kỹ thuật (đã chốt)

- Ngôn ngữ: **Java**. Không chuyển sang Kotlin, không viết file `.kt`.
- Giao diện: **XML Layout**. Không dùng Jetpack Compose.
- Min SDK 24. Package gốc `com.example.baocaosuco`.
- Không dùng Coroutines hoặc StateFlow (của Kotlin). Dữ liệu bất đồng bộ dùng callback/listener của Firebase.
- Backend dự kiến: Firebase Authentication và Cloud Firestore (đang chờ nhóm xác nhận cuối).

## Phạm vi

- Hai vai trò: Cư dân và Quản lý. Kỹ thuật viên chỉ là tên quản lý ghi khi giao việc (chưa chốt có đăng nhập riêng).
- Trạng thái sự cố: Mới → Đã giao → Đang xử lý → Hoàn tất → Đã đóng. Cư dân bấm "chưa xong" thì quay về Đang xử lý. Không được nhảy cóc.
- Phần đính kèm ảnh làm **sau cùng**, trường `imageUrl` cho phép để trống.
- Thông báo dùng Firestore listener. Chưa làm push khi tắt app.
- Không tự thêm tính năng ngoài `docs/PROJECT_SCOPE.md` (thanh toán, chat, gọi điện, AI phân loại ảnh, đánh giá sao, xuất PDF/Excel).

## Cách làm việc

1. **Chưa được phê duyệt thì không tạo file, không sửa code, không refactor.** Chỉ phân tích và đề xuất.
2. Gắn nhãn rõ: `CONFIRMED` (nhóm đã xác nhận), `PROPOSED` (AI đề xuất), `PENDING_DECISION` (chờ nhóm quyết định), `OUT_OF_SCOPE` (không làm). Không biến đề xuất thành quyết định.
3. Không biến ví dụ minh họa (số giờ hạn xử lý, tên trạng thái) thành yêu cầu bắt buộc.
4. Mỗi lần chỉ làm một việc nhỏ. Không viết cả app một lượt.
5. Mỗi người chỉ sửa package của phần mình. File dùng chung (`AndroidManifest.xml`, `build.gradle.kts`, thư mục `common`, `data`) chỉ nhóm trưởng sửa hoặc phải báo trước.

## Phong cách code

- Đơn giản, dễ đọc, phù hợp sinh viên. Ưu tiên chạy đúng hơn tối ưu.
- Chú thích bằng tiếng Việt ở các đoạn logic chính (kiểm tra trạng thái, tính hạn).
- Kèm giải thích ngắn "đoạn này làm gì, vì sao" vì từng người phải giải thích được phần mình khi vấn đáp.
- Tên lớp, biến bằng tiếng Anh, viết theo chuẩn Java (`CamelCase` cho lớp, `camelCase` cho biến).
- Xử lý đủ: đang tải, lỗi, danh sách trống, nhập thiếu.

## Không được làm

- Không commit `local.properties`, keystore hoặc khóa riêng.
- Không đổi package name, `applicationId` khi chưa hỏi nhóm trưởng.
- Không thêm thư viện mới khi chưa hỏi.
