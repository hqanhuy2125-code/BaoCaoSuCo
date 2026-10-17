# Quy ước làm việc nhóm

## GitHub

- Nhánh chính: `main`. Không push thẳng lên `main`.
- Mỗi người làm nhánh riêng, đặt tên: `feature/<tên-ngắn>-<việc>`, ví dụ `feature/an-form-bao-su-co`. Sửa lỗi dùng `fix/<mô-tả>`.
- Xong việc tạo Pull Request vào `main`. Nhóm trưởng xem rồi mới gộp.
- Commit bằng tài khoản GitHub của chính mình để lịch sử thể hiện đúng ai làm gì.
- Lời nhắn commit ngắn, bắt đầu bằng loại việc: `feat:` thêm chức năng, `fix:` sửa lỗi, `docs:` tài liệu, `refactor:` sắp xếp lại code. Ví dụ `feat: them form bao su co`.

## Trước khi tạo Pull Request

1. Kéo `main` mới nhất về nhánh của mình, giải quyết xung đột nếu có.
2. Build và chạy thử trên máy ảo, không lỗi.
3. Chỉ sửa file thuộc phần mình. File dùng chung (`AndroidManifest.xml`, `build.gradle.kts`, `common`, `data`) thì báo nhóm trưởng.
4. Ghi vào PR: làm gì, cách thử, có dùng AI không.

## Xung đột code

Xung đột thường do hai người sửa cùng một file. Cách tránh: mỗi người một package. Nếu vẫn xung đột thì người tạo PR sau tự gộp, hỏi người kia nếu không chắc.

## Họp và báo tiến độ

- Họp ngắn một lần mỗi tuần, mỗi người nói: đã xong gì, đang kẹt gì, tuần tới làm gì.
- Kẹt quá 2 ngày thì báo ngay trong nhóm Zalo.
- Đổi phạm vi (thêm hoặc bỏ chức năng) phải thống nhất cả nhóm và ghi lại trong `docs/PROJECT_SCOPE.md`.

## Dùng AI

- Dùng AI để viết nhanh được, nhưng phải đọc hiểu code và giải thích được phần mình làm khi vấn đáp.
- Mỗi câu hỏi cho AI ghi rõ: dùng Java, giao diện XML, Firebase.
- Ghi vào tài liệu phần nào có AI hỗ trợ.
- Gặp lỗi thì đọc Logcat, copy dòng lỗi để tìm hoặc hỏi.
