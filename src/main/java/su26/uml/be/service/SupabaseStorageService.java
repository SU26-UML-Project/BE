package su26.uml.be.service;

import org.springframework.web.multipart.MultipartFile;

public interface SupabaseStorageService {
    String uploadAvatar(MultipartFile file, String userId);
    String uploadDocument(MultipartFile file, String userId);

    String uploadAvatarFromUrl(String imageUrl, String userId);

    String getSignedUrl(String path, int expiresInSeconds);
    void deleteFile(String bucket, String path);
}
