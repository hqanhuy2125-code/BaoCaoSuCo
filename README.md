# BaoCaoSuCo – Ký túc xá: Báo cáo & xử lý sự cố

Đồ án môn Lập trình ứng dụng di động (Android Studio, Java). Nhóm 4 sinh viên.

## Ứng dụng làm gì

Cư dân ký túc xá báo sự cố (hỏng điện, nước, thiết bị) qua app thay vì nhắn Zalo. Quản lý xem danh sách theo mức ưu tiên và hạn xử lý, giao việc và cập nhật tiến độ. Cư dân theo dõi sự cố của mình tới khi xong và xác nhận.

Quy trình: Cư dân báo → Quản lý giao việc → Đang xử lý → Hoàn tất → Cư dân xác nhận.

## Trạng thái dự án

**Code snapshot trên `main` (kiểm tra 24/10/2026, HEAD `bca86b786804068b60646e30aa3e32a8206d8a89`):**

- Đã merge PR #3/#4: giao diện Login/Register, AuthRepository (mock/Firebase), điều hướng theo vai trò, hồ sơ người dùng Firestore `users/{uid}` và khung màn hình Cư dân/Quản lý (chào + đăng xuất).
- `AuthRepositories.USE_FIREBASE = true` trong source hiện tại. File `firestore.rules` chỉ cấp quyền cho `users`; collection nghiệp vụ khác đang bị từ chối theo mặc định.
- **Chưa có trên `main`:** nghiệp vụ tạo/xem/xử lý sự cố, timeline/history và bộ điều khiển trạng thái. Không có bằng chứng E2E hoặc GitHub Actions run tại checkpoint này.
- Đã có code Firebase **không đồng nghĩa** `DEC-004` được nhóm duyệt chính thức hay quy tắc Firestore đã được deploy. Các quyết định, scope và giới hạn vẫn tra cứu tại [docs/PROJECT_SCOPE.md](docs/PROJECT_SCOPE.md) và [docs/governance/DECISION_LOG.md](docs/governance/DECISION_LOG.md).

Xem checkpoint chi tiết: [docs/governance/PROJECT_STATE.md](docs/governance/PROJECT_STATE.md).

## Công nghệ

| Hạng mục | Lựa chọn | Trạng thái |
|---|---|---|
| Ngôn ngữ | Java | CONFIRMED |
| Giao diện | XML Layout | CONFIRMED |
| Backend | Firebase Authentication + Cloud Firestore | PROPOSED |
| Min SDK | API 24 | CONFIRMED |

## Chạy project

1. Clone repo về máy, mở thư mục bằng Android Studio.
2. Đợi Gradle sync xong.
3. Code hiện chọn Firebase mặc định: cần cấu hình `google-services.json` hợp lệ trong `app/` theo hướng dẫn của nhóm trưởng; tệp này không được theo dõi trong Git.
4. Chọn máy ảo hoặc điện thoại thật, bấm Run.

## Cấu trúc tài liệu

| File | Nội dung |
|---|---|
| `README.md` | Giới thiệu và cách chạy (file này) |
| `AGENT.md` | Quy tắc cho AI hỗ trợ viết code |
| `docs/PROJECT_SCOPE.md` | Phạm vi, quy trình, dữ liệu, phân công, mốc thời gian |
| `docs/TEAM_WORKFLOW.md` | Quy ước GitHub và làm việc nhóm |
| `docs/START_HERE.md` | Bản đồ tài liệu/nguồn quyết định |
| `docs/governance/PROJECT_STATE.md` | Snapshot triển khai và các blocker |
| `docs/governance/DECISION_LOG.md` | Trạng thái quyết định chính thức của nhóm |
| `docs/planning/` | Hai file Word kế hoạch ban đầu của nhóm |
| `docs/ai-review/` | Prompt rà soát và kết quả AI đã đối chiếu với quyết định nhóm |

## Thành viên

| Người | Phần việc | Tên |
|---|---|---|
| 1 (nhóm trưởng) | Khung app, đăng nhập, ghép code | Hà Quang Huy |
| 2 | Phía cư dân | Trần Quốc Huy |
| 3 | Phía quản lý | Trần Huỳnh Hồng Phúc |
| 4 | Lõi trạng thái và kiểm thử | Nguyễn Đức Hưng |
