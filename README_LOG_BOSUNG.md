# Bổ sung LOG — đề xuất chờ review (29/10/2026)

Nguồn: `BaoCaoSuCo-main (1)(1).zip` người dùng cung cấp.

- Chỉ **thêm** `app/src/test/java/com/example/baocaosuco/logic/IncidentLogicBoundaryTest.java`.
- Không sửa source production, Firebase, Gradle, Manager, Resident, AGENT.md hoặc hợp đồng.
- 200 tổ hợp trạng thái/hành động/vai trò/owner/assignee được kiểm tra bởi một bài test, cùng các test biên.
- Cần review và phê duyệt trước khi đưa vào nhánh; không tự merge.
- JUnit/Gradle chưa chạy ở môi trường Android; trạng thái NOT_RUN.
- Quyền actor/owner phải được xác minh tại backend tin cậy; các test này không thay thế kiểm thử Security Rules.

Cách tích hợp: chép đúng file Java vào đường dẫn tương ứng trên nhánh riêng, sau khi nhóm chấp thuận. Chạy `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` trên Windows.
