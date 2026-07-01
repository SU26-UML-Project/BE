package su26.uml.be.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một method (thường ở tầng service) là một thao tác quản trị cần ghi nhật ký.
 *
 * <p>Hạ tầng audit là GENERIC: chỉ cần thêm annotation này lên một action ĐÃ CÓ endpoint thật,
 * {@code AuditAspect} sẽ tự động ghi một dòng vào bảng {@code audit_log} sau khi method chạy
 * thành công — không phải sửa bảng/API audit.</p>
 *
 * <ul>
 *   <li>{@link #action()} — tên hành động cố định (vd. {@code "AI_CONFIG_UPDATE"}). Service có thể
 *       ghi đè động qua {@code AuditContext.setAction(...)} cho các trường hợp toggle
 *       (vd. khóa/mở → {@code USER_LOCK}/{@code USER_UNLOCK}).</li>
 *   <li>{@link #targetType()} — loại đối tượng bị tác động (vd. {@code "USER"}, {@code "AI_CONFIG"}).</li>
 *   <li>{@link #targetId()} — SpEL trên tham số method để lấy id đối tượng (vd. {@code "#userId"}).
 *       Bỏ trống nếu không có, hoặc service tự set qua {@code AuditContext.setTargetId(...)}.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** Tên hành động mặc định lưu vào cột {@code action}. */
    String action();

    /** Loại đối tượng bị tác động, lưu vào cột {@code target_type}. */
    String targetType();

    /** Biểu thức SpEL trên tham số method để lấy {@code target_id} (vd. {@code "#userId"}). */
    String targetId() default "";
}
