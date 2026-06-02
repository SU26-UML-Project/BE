    package su26.uml.be.exception;

    import lombok.AllArgsConstructor;
    import lombok.NoArgsConstructor;

    @AllArgsConstructor
    @NoArgsConstructor
    public class AppException extends RuntimeException {

        private ErrorCode errorCode;

        public ErrorCode getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(ErrorCode errorCode) {
            this.errorCode = errorCode;
        }
    }