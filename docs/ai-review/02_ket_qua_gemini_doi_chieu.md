# Kết quả rà soát của Gemini, đã đối chiếu với quyết định nhóm

Đây là bản tóm lược output của Gemini (Android Studio), kèm nhận xét của nhóm trưởng. **Không phải bản gốc nguyên văn.** Phần nào mâu thuẫn quyết định nhóm thì ghi rõ để không làm theo nhầm.

## 1. Gemini đề xuất và nhóm xử lý thế nào

| Gemini đề xuất | Quyết định của nhóm | Nhãn |
|---|---|---|
| Chuyển project sang Kotlin | Giữ Java vì cô dạy Java | Bác bỏ, Java là CONFIRMED |
| Coroutines và StateFlow | Của Kotlin, không dùng. Dùng callback/listener của Firebase | Bác bỏ |
| XML Layout + ViewBinding + Material | Giữ XML. ViewBinding và Material dùng được | PROPOSED |
| Firebase (Auth, Firestore, Storage) | Dùng Auth và Firestore. Storage chỉ khi làm ảnh | PROPOSED |
| 3 vai trò (cư dân, BQL, kỹ thuật viên) | Dùng 2 vai trò, kỹ thuật viên chỉ là tên | PROPOSED, đăng nhập riêng cho kỹ thuật viên là PENDING_DECISION |
| Ảnh tối đa 3 tấm, làm tuần 2 | Ảnh làm cuối cùng, tùy chọn | Bác bỏ, nhóm đã chốt làm cuối |
| Push FCM là bắt buộc | Dùng Firestore listener, push khi tắt app làm thêm | Bác bỏ khỏi MVP |
| Profile, đánh giá sao, xuất PDF/Excel | Bỏ | OUT_OF_SCOPE |
| MVVM, Single Activity, Navigation Component | Quá nặng với người mới. Dùng Activity/Fragment đơn giản | PENDING_DECISION |
| Lịch 4 tuần, mốc tháng 3/2025 | Dùng lịch 5 tuần + tuần 6 báo cáo của nhóm | Bác bỏ |
| 3 file tài liệu: SCOPE_AND_CONTRACT, TEAM_WORKFLOW_AND_GIT, README | Nhóm dùng README, AGENT.md, PROJECT_SCOPE, TEAM_WORKFLOW | Đã áp dụng, gọn hơn |

## 2. Phần dùng được từ Gemini

- Chỉ nhóm trưởng gộp Pull Request vào `main`. Mỗi người chỉ sửa package của mình.
- Rủi ro người 3 phải chờ người 2: nhập sẵn dữ liệu mẫu lên Firestore để làm trước.
- Tiêu chuẩn nghiệm thu: đủ trạng thái đang tải, lỗi, trống; test máy ảo và máy thật.
- Dùng component có sẵn của Material để đỡ mất thời gian làm giao diện.
- Chụp ảnh và chọn ảnh dùng cơ chế có sẵn của Android, không cần thư viện ngoài phức tạp.

## 3. Cấu trúc package đề xuất (điều chỉnh cho Java, PROPOSED)

Bản Gemini dùng file `.kt` và nhiều tầng, nhóm đơn giản lại:

```text
com.example.baocaosuco/
├── MainActivity.java            # Người 1, điểm vào và điều hướng theo vai trò
├── auth/                        # Người 1, đăng nhập, đăng ký
├── resident/                    # Người 2, form báo, danh sách và chi tiết của cư dân
├── manager/                     # Người 3, danh sách quản lý, giao việc, đổi trạng thái
├── logic/                       # Người 4, hàm kiểm tra chuyển trạng thái, tính hạn
├── data/
│   ├── model/                   # User, Incident, HistoryItem
│   └── repository/              # Lớp gọi Firestore, dùng chung
└── common/                      # Hằng số (trạng thái, vai trò, mức ưu tiên), tiện ích
```

Quy tắc phụ thuộc: `resident` và `manager` gọi `logic` và `data`, không gọi nhau. `data` và `common` chỉ nhóm trưởng sửa.

## 4. Rủi ro (từ Gemini, đã chỉnh)

| Rủi ro | Giảm thiểu |
|---|---|
| Xung đột Git do sửa chung file | Mỗi người một package, nhánh riêng, nhóm trưởng gộp PR |
| Người 3 chờ người 2 | Dữ liệu mẫu trên Firestore, làm màn hình bằng dữ liệu giả trước |
| Lỗi quyền camera và thư viện ảnh | Ảnh làm cuối, chọn từ thư viện trước, chụp camera sau |
| Trễ hạn vì ôm giao diện phức tạp | Dùng component có sẵn, làm đúng luồng trước rồi mới trau chuốt |
| Nhóm chưa quen Android | Tuần 1 làm app nhỏ cho quen, đọc Logcat khi lỗi |

## 5. Câu hỏi còn chờ nhóm

1. Xác nhận Firebase (Auth + Firestore) làm backend.
2. Kỹ thuật viên có đăng nhập riêng hay chỉ là tên quản lý ghi.
3. Có dùng MVVM hay không (hỏi cô có yêu cầu không).
4. Gán tên 4 thành viên vào 4 phần việc.
5. Cô chấm điểm cá nhân dựa vào vấn đáp hay lịch sử GitHub.
