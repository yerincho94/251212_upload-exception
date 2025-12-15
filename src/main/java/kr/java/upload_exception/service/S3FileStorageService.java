package kr.java.upload_exception.service;

import kr.java.upload_exception.exception.FileStorageException;
import kr.java.upload_exception.exception.InvalidFileTypeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * AWS S3 저장 구현체
 *
 * 사용하려면:
 * 1. build.gradle에 AWS SDK 의존성 추가 (spring-cloud-aws-starter-s3)
 * 2. application-dev.yaml에 aws.s3.* 설정
 * 3. file.storage.type=s3 설정
 */
@Service
@Slf4j // 무난하게 로그를 출력을 위한 연결!
@ConditionalOnProperty(
        name = "file.storage.type",
//        havingValue = "local",
        havingValue = "s3" )
//        matchIfMissing = true) // dev에 있는 정보
@RequiredArgsConstructor // 생성자 주입
public class S3FileStorageService implements FileStorageService {

    // SDK가 알아서 구성해서 주입해줌
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    // 허용할 이미지 MIME 타입 목록
    // MIME 타입: 파일의 종류를 나타내는 표준 형식 (예: image/jpeg = JPEG 이미지)
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg",   // JPG, JPEG 이미지
            "image/png",    // PNG 이미지
            "image/gif",    // GIF 이미지
            "image/webp"    // WebP 이미지
    );

    @Override
    public String store(MultipartFile file) {
        // 파일 검증
        validateFile(file);

        // 파일명에서 확장자 추출
        String extension = extractExtension(file.getOriginalFilename());
        // S3 키: 폴더구조/UUID.확장자
        String key = UUID.randomUUID() + extension;

        try {
            // S3에 업로드할 요청 생성
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            // 파일 업로드
            s3Client.putObject(request,
                    // request를 어떻게 처리할 것인가?
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return key;
        } catch (IOException | S3Exception e) {
            throw new FileStorageException("S3 업로드 실패: " + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        if (!StringUtils.hasText(key)) return;
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            // 로그만 남기고 진행
            log.error("S3 삭제 실패: {}", e.getMessage());
        }
    }

    @Override
    public String getUrl(String key) {
        // 로컬 저장소의 경우 /images/ 경로로 접근
        return "/images/" + key;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileStorageException("빈 파일입니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException("허용되지 않는 파일 형식입니다.");
        }
    }

    /**
     * 파일명에서 확장자 추출
     * 예: "photo.jpg" → ".jpg"
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
