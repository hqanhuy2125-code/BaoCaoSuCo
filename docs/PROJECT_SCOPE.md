# Phạm vi dự án

Nhãn: `CONFIRMED` = nhóm đã xác nhận, `PROPOSED` = đề xuất, `PENDING_DECISION` = chờ nhóm quyết định, `OUT_OF_SCOPE` = không làm.

## 1. Các quyết định

| Nội dung | Nhãn |
|---|---|
| Đề tài: báo và xử lý sự cố ký túc xá | CONFIRMED |
| Nhóm 4 người | CONFIRMED |
| Java, giao diện XML | CONFIRMED |
| Phần đính kèm ảnh làm cuối, cả 4 bước còn lại làm bình thường | CONFIRMED |
| Làm xong trong 5 tuần, tuần 6 báo cáo | CONFIRMED (nhóm trưởng) |
| Firebase Authentication + Firestore | PROPOSED |
| 2 vai trò (Cư dân, Quản lý), kỹ thuật viên chỉ là tên | PROPOSED |
| Kỹ thuật viên có đăng nhập riêng | PENDING_DECISION |
| Thông báo bằng Firestore listener (chưa push khi tắt app) | PROPOSED |
| Kiến trúc đơn giản: Activity/Fragment, package theo tính năng, lớp repository | PROPOSED |
| MVVM, Navigation Component | PENDING_DECISION (chỉ dùng nếu cô yêu cầu hoặc nhóm quen) |
| Tên 4 thành viên cho 4 phần việc | PENDING_DECISION |
| Thanh toán, chat, gọi điện, AI phân loại ảnh, đánh giá sao, xuất PDF/Excel | OUT_OF_SCOPE |

## 2. Chức năng MVP (bắt buộc)

- Đăng nhập, đăng ký, phân vai trò Cư dân / Quản lý.
- Cư dân: form báo sự cố (tiêu đề, mô tả, vị trí, mức ưu tiên), danh sách sự cố của mình, xem trạng thái và lịch sử, nút "đã xong" / "chưa xong".
- Quản lý: danh sách tất cả sự cố sắp theo ưu tiên và hạn, lọc theo trạng thái, quá hạn tô đỏ, giao việc (ghi tên người xử lý), đổi trạng thái.
- Lõi: hàm kiểm tra chuyển trạng thái hợp lệ, hàm tính hạn theo mức ưu tiên, ghi lịch sử, thông báo khi trạng thái đổi.

**Làm thêm nếu còn thời gian:** đính kèm ảnh (chọn từ thư viện trước, chụp camera sau), thống kê đếm theo trạng thái, quét QR vị trí, báo sự cố khi mất mạng.

## 3. Luật chuyển trạng thái

| Từ | Sang | Ai bấm |
|---|---|---|
| Mới | Đã giao | Quản lý (phải ghi tên người xử lý) |
| Đã giao | Đang xử lý | Quản lý |
| Đang xử lý | Hoàn tất | Quản lý |
| Hoàn tất | Đã đóng | Cư dân (người báo) |
| Hoàn tất | Đang xử lý | Cư dân (người báo, bấm "chưa xong") |

Mọi chuyển khác bị từ chối.

## 4. Mức ưu tiên và hạn xử lý (ví dụ, nhóm chốt lại)

Khẩn 4 giờ, Thường 3 ngày, Thấp 7 ngày. Hạn = giờ tạo + số giờ theo mức. Chưa xong mà quá hạn thì tô đỏ.

## 5. Dữ liệu (PROPOSED, chốt trong tuần 1)

- **users:** id, tên, email, vai trò.
- **incidents:** id, tiêu đề, mô tả, vị trí, mức ưu tiên, trạng thái, người báo, người xử lý, giờ tạo, hạn xử lý, imageUrl (để trống được).
- **history:** id sự cố, trạng thái cũ, trạng thái mới, ai đổi, lúc nào, ghi chú.

Giờ lưu theo kiểu thời gian của Firestore (Timestamp). Tên trạng thái và vai trò viết thành hằng số dùng chung, không gõ chuỗi tay ở nhiều nơi.

## 6. Phân công (PENDING_DECISION về tên)

| Người | Phần việc | Logic riêng để vấn đáp |
|---|---|---|
| 1 (nhóm trưởng) | Khung app, đăng nhập, phân quyền, quản lý GitHub, tài liệu và demo | Phân quyền theo vai trò |
| 2 | Phía cư dân: form báo, danh sách, chi tiết và timeline, xác nhận, ảnh (cuối) | Kiểm tra dữ liệu nhập, tạo bản ghi |
| 3 | Phía quản lý: danh sách sắp theo ưu tiên và hạn, lọc, giao việc, đổi trạng thái | Sắp xếp, lọc, xác định quá hạn |
| 4 | Lõi trạng thái: hàm kiểm tra chuyển trạng thái, hàm tính hạn, ghi lịch sử, thông báo, kiểm thử | Máy trạng thái, tính hạn |

Người 4 viết hai hàm dùng chung (kiểm tra chuyển trạng thái, tính hạn) từ tuần 1 để người 2 và 3 gọi.

## 7. Mốc thời gian

| Tuần | Việc | Kết quả |
|---|---|---|
| 1 | Làm quen Android Studio và Java, chốt dữ liệu, người 1 dựng khung và đăng nhập | Đăng nhập chạy được |
| 2 | Mỗi người làm màn hình của mình bằng dữ liệu giả, người 4 viết hai hàm lõi | Từng phần chạy riêng |
| 3 | Nối Firestore thật, chạy luồng báo, giao, đang xử lý | Báo và giao chạy bằng dữ liệu thật |
| 4 | Hoàn tất, xác nhận, lịch sử, thông báo | Mốc: luồng chạy trọn. Chưa đạt thì bỏ ảnh |
| 5 | Ghép, test, sửa lỗi, làm ảnh nếu dư thời gian, viết tài liệu, tập demo | Chốt xong |
| 6 | Báo cáo | |

## 8. Tiêu chuẩn nghiệm thu

- Build không lỗi, chạy được trên máy ảo và ít nhất một điện thoại thật.
- Có xử lý các trạng thái: đang tải, lỗi, danh sách trống, nhập thiếu.
- Luồng báo → giao → xử lý → hoàn tất → xác nhận chạy trọn.
- Chuyển trạng thái sai bị từ chối.
- Code đã vào nhánh `main` qua Pull Request.

## 9. Thứ tự bỏ bớt nếu trễ

Ảnh, thông báo, thống kê, lọc theo trạng thái. Không bỏ: đăng nhập, báo sự cố, giao việc, đổi trạng thái hợp lệ, hạn xử lý.
