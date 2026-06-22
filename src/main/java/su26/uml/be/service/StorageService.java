package su26.uml.be.service;

import org.springframework.web.multipart.MultipartFile;
import su26.uml.be.dto.response.FileUploadResponse;
import su26.uml.be.dto.response.SignedUrlResponse;

public interface StorageService {
    FileUploadResponse uploadAvatar(MultipartFile file, String email);
    FileUploadResponse uploadDocument(MultipartFile file, String email);

    String uploadAvatarFromUrl(String imageUrl, String userId);

    SignedUrlResponse getSignedUrl(String path, int expiresInSeconds);
    void deleteFile(String bucket, String path);
}
