# BÁO CÁO NGHIỆM THU — Task B07

## 1. Thông tin chung

| Mục | Giá trị |
|---|---|
| Tên task | B07 — Nối UI của Phúc với Java coordinators và cầu nối chụp ảnh |
| Nhánh | `feature/b07-ui-java-wiring-20261124` |
| Commit HEAD | `7a9c0754602c074f00a2171f7022844a1f3ea3ac` |
| Nhánh cơ sở so sánh | `integration/be-camera-into-develop-20261124` |
| Ngày giờ | 2026-11-26 (review chỉ đọc) |
| Người thực hiện | Claude Code (review tự động), cần người xác nhận |

Các commit của nhánh: `7a9c075 fix(b07): call fragment error handlers instead of recursing`, `f2b33d8 fix(b07): declare detail activities in manifest`, `055ba31 Task B07: Wire Phuc UI with Java coordinators and photo bridge`.

Cây làm việc: chỉ có 7 file chưa theo dõi (`step1_*.png`, `step1_*.xml`) có từ trước. Không có thay đổi nào chưa commit trên file đã theo dõi. Báo cáo này là file mới duy nhất do review tạo ra và chưa `git add`.

## 2. Phạm vi

**B07 làm:**
- Đổi `ResidentMainActivity` và `ManagerMainActivity` sang layout UI của Phúc (`ui_resident_main`, `ui_manager_main`) và nạp fragment vào `fragment_container`.
- Thêm `ResidentListFragment`, `ResidentDetailActivity`, `IncidentAdapter`, `ManagerListFragment`, `ManagerDetailActivity`. Các màn này nối với `ResidentIncidentCoordinator` và `ManagerIncidentCoordinator`.
- Thêm `CreateIncidentFragment`: form tạo sự cố, nối `IncidentPhotoUiBridge` và `IncidentPhotoViewBinder` cho chụp ảnh, chọn thư viện và xóa ảnh.
- Khai báo các Activity chi tiết trong `AndroidManifest.xml`.

**B07 KHÔNG làm (hoặc review này không xác minh):**
- Không sửa layout, drawable, values (đã kiểm tra, xem mục 3).
- Không thêm dependency, không đổi build script, schema hay Firebase rules.
- Không thêm quyền `CAMERA`.
- Chưa kiểm chứng đăng nhập Firebase thật trên emulator hay thiết bị.

## 3. Kết quả kiểm tra tự động

| # | Mục | Kết quả | Bằng chứng |
|---|---|---|---|
| 1 | Không sửa `res/layout`, `res/drawable*`, `res/values` | PASS | `git diff --name-status integration/be-camera-into-develop-20261124..HEAD -- app/src/main/res` không trả về dòng nào |
| 2 | Role gate của `ResidentMainActivity` và `ManagerMainActivity` còn nguyên | PASS | Khối `getCurrentUser(...)` → `redirectToMain()` giữ nguyên. Hàm `logout()` và `redirectToMain()` không nằm trong diff |
| 3 | Đăng xuất còn nguyên | PASS (đọc code) | Nút `logout_button` vẫn gọi `logout()`, nay có null-check. Chưa chạy thực tế |
| 4 | Manifest khai báo đủ Activity mới | PASS | `AndroidManifest.xml` có `.resident.ResidentDetailActivity` và `.manager.ManagerDetailActivity` |
| 5 | FileProvider khớp `incident_photo_paths.xml` | PASS | Authority `${applicationId}.incidentphoto.fileprovider`, meta-data `@xml/incident_photo_paths`, file khai báo `cache-path incident_camera/` |
| 6 | Không có quyền CAMERA | PASS | Manifest chỉ có `INTERNET` |
| 7 | `findViewById` null trong Main Activity | PASS | Các id `welcome_text`, `tv_header_resident_name`, `tv_header_resident_email`, `logout_button`, `fragment_container` có trong cả hai layout `ui_*_main.xml`. `nav_item_home` và `btn_camera_fab` nằm trong `bottom_navigation_bar.xml`, mã có null-check |
| 8 | Dùng view sau `onDestroyView` | PASS (đọc code, rủi ro thấp) | Các fragment đóng coordinator trong `onDestroyView`. `ManagerListFragment` có null-check view. `CreateIncidentFragment.onSaved/onError` dùng `requireContext()` nhưng coordinator đã `close()` |
| 9 | `IncidentPhotoController` được release | PASS | `CreateIncidentFragment.java:123` gọi `photoBridge.close()`. `IncidentPhotoUiBridge.close()` gọi `controller.close()`, trong đó `unregister()` cả hai launcher |
| 10 | Đăng ký `ActivityResultLauncher` đúng thời điểm | CẢNH BÁO | `CreateIncidentFragment.java:74` tạo bridge trong `onCreateView`. Javadoc của bridge yêu cầu tạo trong `Activity.onCreate()` trước STARTED. Khi bấm FAB lúc Activity đang RESUMED, launcher đăng ký muộn. Chưa tái hiện crash, nhưng có nguy cơ mất kết quả camera khi xoay màn hình hoặc process bị hủy giữa lúc chụp. Cần test thủ công |
| 11 | **Lỗi đệ quy vô hạn (crash)** | **Đã sửa ở 7a9c075 - build + unit test pass, CHƯA test trên thiết bị** | Lỗi gốc tại `CreateIncidentFragment.java:85-90`: `Listener` ẩn danh gọi `onPickerError(source, error)` và `onProcessingError(error)`. Trong lớp ẩn danh, tên này trỏ về chính phương thức đó, không trỏ về phương thức của Fragment (dòng 152, 157). Kết quả là đệ quy vô hạn và `StackOverflowError` khi có lỗi chọn hoặc xử lý ảnh. Đã sửa ở commit `7a9c075` thành `CreateIncidentFragment.this.onPickerError(...)` và `CreateIncidentFragment.this.onProcessingError(...)`. `testDebugUnitTest` và `assembleDebug` đều pass sau khi sửa. **Unit test không phủ nhánh lỗi ảnh**, nên bản sửa mới chỉ được chứng minh là biên dịch được, chưa chạy thử trên thiết bị |
| 12 | Ghi Firestore khi mở màn (ngoài nút gửi) | PASS | Các màn danh sách và chi tiết chỉ gọi `loadList()` hoặc tải chi tiết (đọc). `CreateIncidentFragment` chỉ gọi `coordinator.submit(...)` trong `submitIncident()` ở `setOnClickListener` của `btnSubmit`. Kết luận dựa trên đọc code, không chạy với Firebase thật |
| 13 | Log token hoặc mật khẩu | PASS | `grep` tìm `Log.` trong các fragment và activity mới không có kết quả |
| 14 | `.\gradlew.bat testDebugUnitTest` | PASS | 26 suite, 262 test, 0 failure, 0 error, 0 skipped. Chạy lại ngày 2026-11-26 trên HEAD `7a9c075` với `--rerun-tasks`, 39/39 task được thực thi (không còn UP-TO-DATE), `BUILD SUCCESSFUL` trong 1m 19s. Số liệu lấy từ `app/build/test-results/testDebugUnitTest/*.xml` |
| 15 | `.\gradlew.bat assembleDebug` | PASS | `BUILD SUCCESSFUL`, `app-debug.apk` 11.932.717 byte, ghi lúc 2026-11-26 15:58. Biên dịch lại thật cùng lần chạy ở dòng 14 (`--rerun-tasks`) |
| 16 | Không đọc hoặc in `google-services.json` | PASS | Review không mở file này |
| 17 | Test UI trên thiết bị hoặc emulator (Espresso, đăng nhập thật) | NOT_RUN | Review chỉ đọc, không có thiết bị và không đăng nhập Firebase thật |

Ghi chú môi trường: lần chạy Gradle đầu tiên thất bại vì daemon cũ dùng JDK khác (`jlink.exe` không tồn tại ở đường dẫn của extension IDE). Sau `gradlew --stop` với `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, cả hai lệnh đều thành công. Đây là vấn đề môi trường, không phải lỗi mã nguồn.

## 4. Danh sách file đã thay đổi

Nguồn: `git diff --name-status integration/be-camera-into-develop-20261124..HEAD`. Tổng cộng 9 file, +901/−44.

| Trạng thái | File |
|---|---|
| M | `app/src/main/AndroidManifest.xml` |
| A | `app/src/main/java/com/example/baocaosuco/manager/ManagerDetailActivity.java` |
| A | `app/src/main/java/com/example/baocaosuco/manager/ManagerListFragment.java` |
| M | `app/src/main/java/com/example/baocaosuco/manager/ManagerMainActivity.java` |
| A | `app/src/main/java/com/example/baocaosuco/resident/CreateIncidentFragment.java` |
| A | `app/src/main/java/com/example/baocaosuco/resident/IncidentAdapter.java` |
| A | `app/src/main/java/com/example/baocaosuco/resident/ResidentDetailActivity.java` |
| A | `app/src/main/java/com/example/baocaosuco/resident/ResidentListFragment.java` |
| M | `app/src/main/java/com/example/baocaosuco/resident/ResidentMainActivity.java` |

**Các dòng bị đổi trong hai Main Activity (so với bản gốc):**
- Đổi layout: `resident_main` → `ui_resident_main`, `manager_main` → `ui_manager_main`.
- Đổi id view: `*_main_greeting` → `welcome_text`. Thêm `tvName` và `tvEmail`. Nút đăng xuất đổi từ `Button` sang `ImageButton` `logout_button`.
- Đổi lời chào: dùng `R.string.resident_welcome` và `R.string.manager_welcome` thay cho `*_main_greeting`.
- Thêm `switchToHome()`, `switchToCreateIncident()` (cư dân) và `loadFragment()`. Thêm gắn sự kiện `nav_item_home` và `btn_camera_fab`.
- Xóa các comment Javadoc và comment giải thích. Không đổi logic phân quyền.

## 5. Rủi ro và vấn đề đã biết

1. **[Đã sửa ở 7a9c075 - build + unit test pass, CHƯA test trên thiết bị] Đệ quy vô hạn** ở `CreateIncidentFragment.java:85-90` (mục 3 dòng 11). Lỗi gốc gây crash khi chọn ảnh lỗi, ví dụ camera không khả dụng hoặc ảnh không hợp lệ. Unit test không phủ nhánh lỗi ảnh, nên cần test thủ công các tình huống lỗi ảnh để xác nhận.
2. **[Trung bình] Đăng ký launcher trong `onCreateView`** (mục 3 dòng 10), trái với hướng dẫn trong Javadoc của bridge. Cần test xoay màn hình hoặc kill process khi đang chụp.
3. **[Thấp] `setResidentVerified(true)` cố định** ở `CreateIncidentFragment.java:97`. Nút ảnh được bật mà không chờ kết quả role gate. Role gate vẫn do `ResidentMainActivity` thực hiện, nhưng không có kiểm tra riêng ở tầng fragment.
4. **[Thấp] `ResidentDetailActivity` và `ManagerDetailActivity` không có role gate riêng.** Các Activity này khai báo `exported=false`. Kiểm soát quyền thật phải nằm ở Firestore rules phía server, không được dựa vào kiểm tra role ở client.
5. **[Thấp] Chuỗi hard-code** trong `CreateIncidentFragment` ("📷 Chụp Ảnh Sự Cố", Toast, v.v.) và nút ảnh tạo động trong mã. Ảnh hưởng đến i18n và nhất quán với XML của Phúc.
6. **Chưa chạy được đăng nhập Firebase thật trên emulator** trong lần review này. Luồng đăng nhập, tải danh sách và chi tiết với dữ liệu thật chưa được kiểm chứng.
7. Firebase Auth + Firestore vẫn ở trạng thái `PROPOSED` theo `AGENTS.md` cho đến khi nhóm ghi nhận phê duyệt.
8. Đã chạy lại với --rerun-tasks trên 7a9c075, 39/39 task thực thi, không còn UP-TO-DATE (xem mục 3 dòng 14-15).
9. Môi trường dev có daemon Gradle dùng JDK sai. Cần `gradlew --stop` và đặt `JAVA_HOME` đúng.
10. File `step1_*.png` và `step1_*.xml` chưa theo dõi nằm ở thư mục gốc repo. Không thuộc B07, không nên đưa vào commit.

## 6. Checklist KIỂM THỬ THỦ CÔNG

Người test điền kết quả (PASS/FAIL/BLOCKED) và ghi chú. **Tuyệt đối KHÔNG bấm "Gửi sự cố" khi test** để tránh ghi dữ liệu vào Firestore.

- [ ] NOT_RUN — Đăng nhập tài khoản Cư dân
- [ ] NOT_RUN — Xem danh sách sự cố của Cư dân
- [ ] NOT_RUN — Mở chi tiết một sự cố (Cư dân)
- [ ] NOT_RUN — Mở màn tạo sự cố (bấm nút camera FAB)
- [ ] NOT_RUN — Chụp ảnh (cấp quyền nếu hệ thống hỏi, xác nhận ảnh xem trước hiện ra)
- [ ] NOT_RUN — Chọn ảnh từ thư viện
- [ ] NOT_RUN — Xóa ảnh đã chọn
- [ ] NOT_RUN — Thử tình huống lỗi ảnh (hủy chụp, ảnh không hợp lệ). Mục tiêu: xác nhận lỗi đệ quy ở mục 5.1 không gây crash
- [ ] NOT_RUN — Xoay màn hình khi đã có ảnh xem trước và khi đang chụp
- [ ] NOT_RUN — Đăng xuất (Cư dân)
- [ ] NOT_RUN — Đăng nhập tài khoản Quản lý
- [ ] NOT_RUN — Xem danh sách sự cố của Quản lý (kiểm tra các chip lọc trạng thái)
- [ ] NOT_RUN — Mở chi tiết sự cố của Quản lý
- [ ] NOT_RUN — Đăng xuất (Quản lý)
- [ ] NOT_RUN — Xác nhận không có dữ liệu mới nào xuất hiện trên Firestore sau toàn bộ phiên test

## 7. Kết luận

**Chưa đủ điều kiện merge.**

Lý do:
- Lỗi đệ quy vô hạn trong `CreateIncidentFragment.java:85-90` đã được sửa ở `7a9c075` (build + unit test pass), nhưng CHƯA test trên thiết bị. Unit test không phủ nhánh lỗi ảnh, và tính năng chụp ảnh là trọng tâm của B07.
- Rủi ro đăng ký launcher muộn (mục 5.2) chưa được kiểm chứng.
- Toàn bộ kiểm thử thủ công và đăng nhập Firebase thật là NOT_RUN.

Điểm tốt: không sửa layout, drawable hay values. Role gate và đăng xuất giữ nguyên. Manifest, FileProvider và quyền khớp yêu cầu. 262 unit test đạt và `assembleDebug` thành công.

Đây chỉ là đánh giá của review tự động. Theo `AGENTS.md`, **quyết định merge thuộc người duyệt**, và AI chỉ đề xuất.

## 8. Chữ ký

| Vai trò | Họ tên | Quyết định (Duyệt / Không duyệt) | Ngày | Chữ ký |
|---|---|---|---|---|
| Người duyệt | | | | |
| Người test thủ công | | | | |
