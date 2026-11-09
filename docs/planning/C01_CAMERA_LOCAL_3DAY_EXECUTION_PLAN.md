# C01-LOCAL — Kế hoạch triển khai Camera/ảnh minh chứng (3 ngày)

> **Loại tài liệu:** Execution plan + progress log; KHÔNG phải bằng chứng đã hoàn thành chức năng.  
> **Dự án:** BaoCaoSuCo (Android Java/XML, `com.example.baocaosuco`, minSdk 24).  
> **Thời gian mục tiêu:** 09–11/11/2026 (Asia/Ho_Chi_Minh).  
> **Owner triển khai:** Trần Quốc Huy — Backend/Technical Integration, Resident Java.  
> **Reviewer bắt buộc:** một thành viên có quyền, độc lập với tác giả PR; cần xác định trước merge.  
> **Theo dõi:** [Issue #54](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/54) (MVP tracker); [Issue #59](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/59) (UI handoff của Phúc).  
> **Baseline source:** `main@62c616f7ad80a8a19fb5160eb5c05907ef316658` (29/10/2026).  
> **Feature branch:** `feature/c01-camera-local-20261109`. Không commit thẳng `main`.

## 0. Mục đích và quyết định phạm vi

C01 trong [PROJECT_SCOPE](../PROJECT_SCOPE.md) và Issue #54 vốn thuộc **enhancement, ảnh làm sau MVP**. Theo yêu cầu ưu tiên triển khai gấp 3 ngày của Huy, đây là **kế hoạch thực thi giới hạn C01-LOCAL trên nhánh riêng**; không tự chuyển C01 toàn phần sang DONE hoặc thay đổi quyết định chung của nhóm. Phần này chỉ tạo năng lực Android xử lý ảnh cục bộ, **không gửi ảnh cùng sự cố lên Firebase, không cho Manager xem ảnh qua cloud**. Đề nghị nhóm xác nhận thứ tự ưu tiên trước khi merge.

**Đầu ra tối thiểu:** một ảnh/lần báo cáo; gọi Camera hệ thống hoặc Android Photo Picker; tạo URI chụp an toàn; đọc URI đã chọn; kiểm tra ảnh; sao chép/xử lý thành ảnh JPEG trong vùng lưu riêng ứng dụng; trả về đối tượng kết quả (URI nội bộ, thông tin ảnh hoặc lỗi) qua callback/API Java để Phúc nối vào B01. Ảnh có thể xem trước bằng app/test harness khi có UI phù hợp, nhưng không tự thêm XML của Phúc.

**Ngoài phạm vi:** Firebase Storage/Blaze/billing, upload/download, `imageUrl`/Firestore schema, `NewIncidentInput`/`IncidentRepository` contract, nhiều ảnh, quay video, CameraX, Camera custom, chụp nền, OCR/AI, Gallery tự viết, EXIF/GPS enrichment, Manager gallery UI, sửa các luồng B01–B07 khác. Không ghi Base64 ảnh vào Firestore.

**Dừng/mở rộng:** nếu yêu cầu ảnh upload, Manager đọc ảnh từ thiết bị khác hoặc ảnh persist gắn với incident ID, lập thay đổi hợp đồng và phê duyệt Storage/Rules/billing **ở sprint khác**. Không tự triển khai trong 3 ngày này.

## 1. Kiểm tra baseline và phân công

- Đã kiểm tra GitHub remote `main`: chưa có các file Photo/Camera/Gallery/Storage và chưa có khai báo FileProvider trong `AndroidManifest.xml`; `IncidentRepository.Resident.create(NewIncidentInput,...)` chỉ nhận 4 trường text.
- Nhánh `main` có PR #47/#53/#57/#58/#60 đã merge; Java B01–B06 có sẵn, **Activity/XML binding của nghiệp vụ chưa được nghiệm thu**. Issue #59 chưa có XML bàn giao ở lần kiểm tra 05/11.
- Huy triển khai **Java resident/photo/** và Java test liên quan; Phúc sở hữu XML màn hình B01/B02/B04; Hà sở hữu Auth; Hưng sở hữu LOG; không đụng file của người khác.
- Remote GitHub **không cho biết** trạng thái file chưa commit trên máy của thành viên. Trước khi code cục bộ, người thực thi phải kiểm tra `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git diff --stat`, `git worktree list`. Không reset/clean/stash làm mất dữ liệu.

### File ownership / edit gates

| Paths | Trạng thái / quyền sửa |
|---|---|
| `app/src/main/java/com/example/baocaosuco/resident/photo/**` | **ALLOWED** trong tác vụ C01-LOCAL sau khi chốt L1; code Java mới |
| `app/src/test/java/com/example/baocaosuco/resident/photo/**` | **ALLOWED** cho unit test không cần Firebase |
| `app/src/androidTest/java/com/example/baocaosuco/resident/photo/**` | **OPTIONAL**, chỉ thêm nếu có thiết bị/emulator đủ điều kiện chạy |
| `app/src/main/res/xml/incident_photo_paths.xml` | **CONDITIONAL**: FileProvider XML cấu hình mới, cần xác nhận phạm vi cấu hình và đường dẫn tối thiểu |
| `app/src/main/AndroidManifest.xml` | **BLOCKED UNTIL L2 APPROVAL**: phải được integration custodian + owner bị ảnh hưởng/independent reviewer đồng ý trước khi đăng ký FileProvider |
| `app/src/main/java/com/example/baocaosuco/resident/ResidentMainActivity.java` | **DEFER**: chỉ nối sau khi Phúc giao XML/ID thật, bằng PR Java tích hợp riêng |
| `app/src/main/res/layout/**`, `auth/**`, `logic/**`, `manager/**`, `data/**`, `firestore.rules`, `firestore.indexes.json`, Gradle/build/secret | **PROTECTED / NO EDIT** trong C01-LOCAL; nếu thật sự cần thay đổi thì dừng và xin quyết định riêng |

Ưu tiên dùng `androidx.activity.result.contract.ActivityResultContracts.TakePicture` và `PickVisualMedia` (kiểm tra phiên bản dependency có sẵn) thay vì viết Camera SDK. Tạo URI FileProvider dưới thư mục cache riêng, cấp quyền URI tạm đúng phạm vi; tránh xin quyền `CAMERA` hay quyền toàn bộ thư viện nếu chỉ gọi Camera hệ thống/Photo Picker. Không đưa đường dẫn `file://` cho app Camera.

## 2. API nội bộ và quy tắc dữ liệu (đề xuất; cần kiểm tra khi code)

- `IncidentPhotoController.java`: nhận Host Activity/ActivityResultRegistry, đăng ký launcher đúng lifecycle (trước STARTED), phát lệnh `capture()` / `pick()`, nhận kết quả success/cancel/failure; không chứa logic Firebase.
- `IncidentPhotoProcessor.java`: kiểm tra URI đọc được, MIME và nội dung ảnh, giới hạn đầu vào, decode lấy kích thước với sampling phù hợp để tránh OOM, encode JPEG ngoài UI thread.
- `IncidentPhotoStore.java`: tạo ảnh chụp tạm, copy từ content URI sang app-private storage; xóa file tạm/ảnh thay thế thuộc sở hữu module; không giữ quyền truy cập thư viện lâu hơn cần thiết.
- `IncidentPhotoPolicy.java`: một ảnh, chấp nhận JPEG/PNG, đầu vào <=15 MiB, chiều dài tối đa đầu ra 1600px, mục tiêu JPEG <=2 MiB; nếu không đáp ứng được thì báo lỗi thay vì treo máy.
- `PhotoResult`/callback: phân biệt `SUCCESS`, `CANCELLED`, `INVALID_IMAGE`, `TOO_LARGE`, `IO_ERROR` (thiết kế kiểu cụ thể khi bắt đầu code); UI dùng URI nội bộ để hiển thị ảnh và cho phép thay thế/xóa.
- Test ảnh dọc/ngang theo dữ liệu thực tế; metadata định hướng có thể cần xử lý riêng. Không thêm ExifInterface hoặc dependency mới khi chưa được duyệt; nếu còn hạn chế xoay ảnh phải ghi nhận trong PR, không báo PASS sai.
- Không giữ ảnh vô thời hạn khi báo cáo chưa lưu; dọn cache do module tạo và tránh xóa ảnh ngoài phạm vi ứng dụng. Không ghi ảnh/URI riêng tư vào logs công khai.

## 3. Kế hoạch thực thi 09–11/11/2026

| Mốc | Công việc có thể kiểm tra | Đầu ra / gate |
|---|---|---|
| **D1 — 09/11** | D1.1 baseline/allowed paths, mở nhánh; D1.2 chốt API callback và FileProvider gate; D1.3 tạo Camera TakePicture + Photo Picker; D1.4 kiểm tra cancel, permission và URI | **G1:** gọi Camera/Gallery qua Activity Result, có URI hoặc lỗi/cancel rõ ràng; không sửa XML của Phúc |
| **D2 — 10/11** | D2.1 validate MIME/nội dung/kích thước; D2.2 xử lý resize/encode JPEG trên worker; D2.3 lưu cục bộ và cleanup; D2.4 đơn vị test policy và trường hợp lỗi | **G2:** ảnh hợp lệ thành JPEG đọc được; ảnh quá lớn/hỏng/URI lỗi không crash và không leak |
| **D3 — 11/11** | D3.1 chạy unit test + `assembleDebug`; D3.2 manual test emulator và phone nếu có; D3.3 rà soát diff, bảo mật, callback/lifecycle; D3.4 PR, xin review, gửi API handoff cho Phúc | **G3:** ghi SHA, lệnh và kết quả thật, lỗi còn tồn tại, reviewer và PR; chỉ merge sau APPROVE; nếu thiếu XML ghi UI_BINDING_BLOCKED |

**Ưu tiên nếu trễ:** phải giữ Camera + Picker + URI an toàn + validation tối thiểu + build/test. Giảm mục tiêu nén/định hướng hoặc kiểm thử thiết bị không có sẵn, nhưng ghi `PARTIAL`/`BLOCKED` với nguyên nhân; không thay bằng claim DONE. Không phụ thuộc Cloud để chốt C01-LOCAL.

## 4. Ma trận nghiệm thu (tất cả NOT_RUN ban đầu)

| TC | Bước kiểm tra | Kết quả mong đợi | Trạng thái ban đầu |
|---|---|---|---|
| TC01 | Chụp ảnh thành công qua Camera hệ thống | Trả URI nội bộ đọc được, file tồn tại | NOT_RUN |
| TC02 | Hủy Camera | Không crash, không giữ file tạm rác | NOT_RUN |
| TC03 | Chọn ảnh bằng Photo Picker | Đọc URI, xử lý được ảnh thực tế | NOT_RUN |
| TC04 | Hủy Picker | Không phá ảnh đang chọn | NOT_RUN |
| TC05 | Chọn ảnh >15 MiB hoặc không được hỗ trợ | Báo lỗi có mã, không OOM/crash | NOT_RUN |
| TC06 | Ảnh hỏng/URI không đọc được/permission revoked | Báo lỗi rõ ràng, đóng stream đúng | NOT_RUN |
| TC07 | Resize/JPEG/đổi ảnh và xóa ảnh | Giữ đúng 1 ảnh, file cũ do module tạo được dọn | NOT_RUN |
| TC08 | Xoay màn hình, back, Activity destroy | Không cập nhật UI đã hủy, không crash/leak | NOT_RUN |
| TC09 | `./gradlew :app:testDebugUnitTest :app:assembleDebug` (Windows: `.\\gradlew.bat`) | Gradle exit 0, số test và log thật | NOT_RUN |
| TC10 | Tích hợp ảnh vào B01 XML thật | Người dùng thấy preview trên form thật | BLOCKED: XML/IDs chưa bàn giao; ngoài điều kiện DONE riêng của module Java |
| TC11 | Thiết bị vật lý | Chụp/chọn và nhận ảnh trên máy thật | NOT_RUN; có thể BLOCKED nếu không có máy |

**Ghi chú nghiệm thu:** G1/G2/G3 chỉ đóng khi có SHA tương ứng và bằng chứng. `TEST_PASS` code không tự suy ra `MERGED` hay `APP_E2E_DONE`. Nghiệm thu C01-LOCAL không đồng nghĩa C01 cloud đầy đủ.

## 5. Git/Review/Tracking

1. Tạo feature branch từ đúng `main` đã xác nhận, chỉ thay đổi file thuộc C01; từng commit nhỏ theo thứ tự `feat(c01): camera-picker`, `feat(c01): local photo processing`, `test(c01): validation and lifecycle`.
2. Không mở PR tài liệu riêng chỉ để cập nhật status. Tài liệu này được đặt trên **cùng feature branch để dùng khi code**; mở một PR C01 **sau khi có triển khai và bằng chứng kiểm thử**, link Issue #54; không merge tự động.
3. PR phải ghi: scope, Owner/reviewer, base SHA/head SHA, files changed, quyền L2 cho Manifest/FileProvider, lệnh build, emulator/phone evidence, case FAIL/BLOCKED, ảnh chụp/redacted logs, handoff cho Phúc.
4. Không ghi tên người khác là reviewer đã APPROVE nếu chưa có action trên GitHub. Không push thẳng main, không force push, không sửa / xóa công việc teammate.
5. Issue #54 giữ làm tracker duy nhất: bình luận kết quả sau G1/G2/G3; Issue #59 chỉ cập nhật khi có API handoff thực sự, không thay đổi XML.
6. Nếu dự án chưa có file `app/google-services.json` local, build/test có thể BLOCKED: cần ghi đúng blocker; tuyệt đối không commit credentials. C01-LOCAL không thực hiện Firebase network test.

## 6. Nhật ký tiến độ — khởi tạo 09/11/2026

| Mục | Người phụ trách | Trạng thái | Bằng chứng / blocker |
|---|---|---|---|
| INSPECT — xác minh module Camera còn thiếu trên remote `main` | Huy (điều phối) | **DONE (remote inspection)** | Baseline `62c616f`: chưa có photo classes/FileProvider, data contract text-only |
| PLAN — mô tả scope, WBS, gate, test matrix | Huy (điều phối) | **DOCUMENTED / REVIEW_PENDING** | File kế hoạch này trên feature branch, chưa merge main |
| AUTH — chốt sửa Manifest/FileProvider (L2) | Huy + owner/reviewer bị ảnh hưởng | **BLOCKED_APPROVAL** | Chưa ghi nhận phê duyệt cụ thể; xem [G1 handoff](../testing/C01_G1_CAMERA_GALLERY_HANDOFF.md) |
| D1 — code Camera/Picker | Huy | **JAVA_CODE_READY / G1_BLOCKED_CONFIG** | `IncidentPhotoController`, `IncidentPhotoRequestState` + 8 JUnit methods committed; FileProvider/Android build/manual checks still pending |
| D2 — validate, resize, local store | Huy | **TODO** | Build/tests NOT_RUN |
| D3 — CI/manual tests, code review, PR | Huy + independent reviewer | **TODO** | Android/Gradle/device evidence NOT_RUN; review/merge not completed |
| UI — Phúc XML/IDs → Huy Java integration | Phúc → Huy | **BLOCKED_DEPENDENCY** | Issue #59 còn mở, không dùng ID giả |
| C01 full cloud evidence attachment | Group decision | **OUT_OF_SCOPE_THIS_SPRINT** | Chờ riêng hợp đồng Storage/Rules/billing |

### Checkpoint G1 — 09/11/2026 (actual code commits)

- **G1 Java implemented / NOT DEVICE-ACCEPTED**: `IncidentPhotoController.java` (`TakePicture`, `PickVisualMedia`, success/cancel/error), `IncidentPhotoRequestState.java` (single-flight, stale-callback guards), `IncidentPhotoRequestStateTest.java` (8 JUnit methods authored). All additions under Huy's `resident/photo/**` and test module.
- **Handoff:** [C01 G1 Camera/Gallery Java](../testing/C01_G1_CAMERA_GALLERY_HANDOFF.md). No Phúc XML binding, no Firebase/image persistence, no changes in Auth/LOG/Manager/Data/Gradle/Manifest.
- **G1 gate:** `JAVA_CODE_READY / BLOCKED_CONFIG`: Camera full-size uses FileProvider authority `<applicationId>.incidentphoto.fileprovider`. Manifest provider and minimum `res/xml` path require task-specific L2 approval; both remain unwritten. Gallery API code exists but has not been exercised on a device.
- **Verification:** Android Gradle/JUnit/emulator/phone = `NOT_RUN`; do not promote to `G1_PASS` until reviewed FileProvider config, build, and manual smoke. Rotation/process-recreation still to verify in G3.
- **Next:** obtain L2 authorization, land minimal FileProvider configuration separately in this branch, run testDebugUnitTest + assembleDebug and emulator Camera/Picker; then D2 image processing.

### Mẫu ghi tiến độ G1/G2/G3

```text
DATE/TIME + TZ:
TASK ID / GATE:
OWNER / REVIEWER:
BASE_SHA / HEAD_SHA / BRANCH / PR:
ALLOWED / CHANGED PATHS:
BUILD COMMAND / EXIT:
UNIT TESTS / RESULT:
EMULATOR / OS / RESULT:
PHYSICAL DEVICE / OS / RESULT:
WHAT VERIFIED:
FAIL / BLOCKER / OWNER:
NEXT ACTION:
DECISION: PASS | FAIL | BLOCKED | NOT_RUN
```

**Stop condition:** Nếu thiếu phê duyệt L2, chỉ triển khai Java trong `resident/photo/**` và unit tests độc lập; không chỉnh Manifest/Gradle/Shared/XML. Nếu có lỗi chưa khắc phục, giữ status chính xác và không tự nhận đã nghiệm thu.
