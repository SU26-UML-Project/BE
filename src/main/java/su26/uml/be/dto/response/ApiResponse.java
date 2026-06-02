package su26.uml.be.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    int code;
    String message;
    T result;

    public static <T> ApiResponse<T> success(String message, T result) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setResult(result);
        return response;
    }

    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(9999);
        response.setMessage(message);
        return response;
    }

}
