# BaoCaoSuCo – Ký túc xá: Báo cáo & xử lý sự cố

Đồ án môn Lập trình ứng dụng di động (Android Studio, Java). Nhóm 4 sinh viên.

## Ứng dụng làm gì

Cư dân ký túc xá báo sự cố (hỏng điện, nước, thiết bị) qua app thay vì nhắn Zalo. Quản lý xem danh sách theo mức ưu tiên và hạn xử lý, giao việc và cập nhật tiến độ. Cư dân theo dõi sự cố của mình tới khi xong và xác nhận.

Quy trình: Cư dân báo → Quản lý giao việc → Đang xử lý → Hoàn tất → Cư dân xác nhận.

## Trạng thái dự án

Đang ở giai đoạn thống nhất kế hoạch. Chi tiết xem [docs/PROJECT_SCOPE.md](docs/PROJECT_SCOPE.md).

## Công nghệ

| Hạng mục | Lựa chọn | Trạng thái |
|---|---|---|
| Ngôn ngữ | Java&Kotlin | CONFIRMED |
| Giao diện | XML Layout | CONFIRMED |
| Backend | Firebase Authentication + Cloud Firestore | PROPOSED |
| Min SDK | API 24 | CONFIRMED |

## Chạy project

1. Clone repo về máy, mở thư mục bằng Android Studio.
2. Đợi Gradle sync xong.
3. Nếu dùng Firebase: thêm file `google-services.json` vào thư mục `app/` (lấy từ nhóm trưởng).
4. Chọn máy ảo hoặc điện thoại thật, bấm Run.

## Cấu trúc tài liệu

| File | Nội dung |
|---|---|
| `README.md` | Giới thiệu và cách chạy (file này) |
| `AGENT.md` | Quy tắc cho AI hỗ trợ viết code |
| `docs/PROJECT_SCOPE.md` | Phạm vi, quy trình, dữ liệu, phân công, mốc thời gian |
| `docs/TEAM_WORKFLOW.md` | Quy ước GitHub và làm việc nhóm |
| `docs/planning/` | Hai file Word kế hoạch ban đầu của nhóm |
| `docs/ai-review/` | Prompt rà soát và kết quả AI đã đối chiếu với quyết định nhóm |

## Thành viên

| Người | Phần việc | Tên |
|---|---|---|
| 1 (nhóm trưởng) | Khung app, đăng nhập, ghép code | Hà Quang Huy |
| 2 | Phía cư dân | Trần Quốc Huy |
| 3 | Phía quản lý | Trần Huỳnh Hồng Phúc |
| 4 | Lõi trạng thái và kiểm thử | Nguyễn Đức Hưng |
