# Prompt rà soát và chuẩn hóa kế hoạch đồ án Android

Prompt do một thành viên nhóm soạn, dùng để nhờ AI rà soát kế hoạch. Lưu lại để tham khảo.

## 1. Bối cảnh

- Nhóm 4 sinh viên, đồ án môn Lập trình ứng dụng di động.
- Đề tài: Ký túc xá – Báo cáo & xử lý sự cố.
- Thời gian dự kiến: 4 tuần (nhóm trưởng sau đó đề xuất 5 tuần làm, tuần 6 báo cáo).
- Năng lực: nền tảng lập trình cơ bản, kinh nghiệm chưa đồng đều.
- Module dự kiến: A – Cư dân, B – Quản lý sự cố, C – Quy trình xử lý, D – Nền tảng & tích hợp.

## 2. Vai trò của AI

Cố vấn kỹ thuật và quản lý dự án cho nhóm sinh viên: rà soát kế hoạch, nhận diện quyết định còn thiếu, đề xuất phương án, chuẩn bị bộ tài liệu chung. Không tự quyết thay nhóm.

Nhãn trạng thái: CONFIRMED, PROPOSED, PENDING_DECISION, OUT_OF_SCOPE. Không biến ví dụ minh họa thành yêu cầu bắt buộc.

## 3. Nội dung cần rà soát

- A. Scope và yêu cầu (MVP, bắt buộc và mở rộng, tiêu chí nghiệm thu).
- B. Công nghệ (Java hay Kotlin, XML hay Compose, Firebase hay REST API). Chỉ khuyến nghị, ưu tiên yêu cầu của giảng viên.
- C. Kiến trúc và cấu trúc source (đơn giản, phù hợp sinh viên).
- D. Data contract và backend (entity, trạng thái, vai trò, quy tắc lỗi, collections hoặc endpoint).
- E. UI/UX (theme, component, loading/empty/error state).
- F. GitHub và tích hợp (nhánh, commit, Pull Request, review, conflict).
- G. Phân công và kiểm soát tiến độ (owner, reviewer, task, Definition of Ready/Done, milestone, blocker).
- H. Kiểm thử và bàn giao (unit test, tích hợp, phân quyền, APK, demo checklist).

## 4. Bộ tài liệu đề xuất

README.md, PROJECT_SCOPE.md, ARCHITECTURE.md, DATA_CONTRACT.md, API_CONTRACT.md hoặc FIREBASE_CONTRACT.md, UI_GUIDELINES.md, TEAM_WORKFLOW.md, TEST_CHECKLIST.md. Được đề xuất gộp hoặc bỏ bớt cho gọn.

## 5. Yêu cầu đầu ra

1. Đánh giá phần kế hoạch đã đáp ứng.
2. Nội dung còn thiếu hoặc chưa rõ.
3. Quyết định cần nhóm thống nhất.
4. Phương án kỹ thuật khuyến nghị kèm lý do.
5. Bộ tài liệu tối thiểu.
6. Cấu trúc repository.
7. Ma trận trách nhiệm 4 thành viên.
8. Kế hoạch với milestone và điều kiện nghiệm thu.
9. Rủi ro và cách giảm thiểu.
10. Câu hỏi cần xác nhận trước khi triển khai.

## 6. Ràng buộc

Không tự mở rộng đề tài, không giả định công nghệ đã chọn, không tự phân công, không biến đề xuất thành quyết định, không tạo file hoặc sửa source trước khi được phê duyệt, không áp dụng kiến trúc hoặc quy trình quá nặng, ưu tiên MVP chạy ổn định. Thiếu thông tin thì ghi PENDING_DECISION và nêu các lựa chọn.

Bước đầu: chỉ rà soát và đưa bản đề xuất cho nhóm trưởng, chờ xác nhận.
