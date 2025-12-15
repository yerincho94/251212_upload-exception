package kr.java.upload_exception.service;

import kr.java.upload_exception.exception.FileStorageException;
import kr.java.upload_exception.exception.InvalidFileTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 로컬 디스크 저장 구현체
 *
 * @Primary: 같은 인터페이스의 여러 구현체 중 기본으로 사용할 것 지정
 * @ConditionalOnProperty: 특정 설정값이 있을 때만 Bean 등록
 */
@Service
@Slf4j // 무난하게 로그를 출력을 위한 연결!
@ConditionalOnProperty(
        name = "file.storage.type",
        havingValue = "local",
        matchIfMissing = true) // dev에 있는 정보
public class LocalFileStorageService implements FileStorageService {

    // 파일이 저장될 실제 디렉토리 경로
    // import java.nio.file.Path;
    private final Path uploadPath;

    // 허용할 이미지 MIME 타입 목록
    // MIME 타입: 파일의 종류를 나타내는 표준 형식 (예: image/jpeg = JPEG 이미지)
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg",   // JPG, JPEG 이미지
            "image/png",    // PNG 이미지
            "image/gif",    // GIF 이미지
            "image/webp"    // WebP 이미지
    );

    // 생성자: application.yaml의 file.upload-dir 값을 주입받음
    // import org.springframework.beans.factory.annotation.Value;
    public LocalFileStorageService(@Value("${file.upload-dir}") String uploadDir) {
        // 문자열 경로를 Path 객체로 변환
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        // 업로드 디렉토리가 없으면 생성
        try {
            Files.createDirectories(this.uploadPath); // 생성시도
        } catch (IOException e) {
            throw new FileStorageException("업로드 디렉토리를 생성할 수 없습니다.");
        }
    }

    /**
     * 파일을 서버에 저장하고 저장된 파일명을 반환
     * MultipartFile -> 저장된 파일명
     *
     * @param file 업로드된 파일 (MultipartFile: Spring이 업로드 파일을 다루는 인터페이스)
     * @return 저장된 파일명 (UUID + 원본 확장자)
     */
    public String store(MultipartFile file) {
        // 빈 파일 체크
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new FileStorageException("빈 파일은 업로드할 수 없습니다.");
        }

        // 파일 타입 검증
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException(
                    "허용되지 않는 파일 형식입니다. (허용: JPG, PNG, GIF, WebP)"
            );
        }

        // 원본 파일명에서 확장자 추출
        String originalFilename = file.getOriginalFilename();
//        String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        // substring으로 확장자 추출을하게 되면, 나중에 문제가 생길 수 있음으로, 아래처럼 따로 메서드화 시킨다.
        String extension = extractExtension(originalFilename);
        // image.jpg -> image[.jpg] -> .jpg

        // 새로운 파일명 생성: UUID를 사용해 중복 방지
        // UUID: 전 세계에서 고유한 식별자를 생성하는 표준 방식
        String storedFilename = UUID.randomUUID() + extension;

        // 파일명에 경로 조작 문자(..)가 있는지 검사 - 보안 목적
        // Path Traversal 보안공격: "../../../etc/passwd" 같은 경로로 시스템 파일 접근 시도
        if (storedFilename.contains("..")) {
            throw new FileStorageException("파일명에 허용되지 않는 문자가 포함되어 있습니다.");
        }

        try {
            // 최종 저장 경로 생성
            Path targetPath = this.uploadPath.resolve(storedFilename);

            // 파일 저장 (transferTo: 업로드된 파일을 지정 경로로 이동)
            file.transferTo(targetPath);

            return storedFilename; // 비로서 저장 가능한 파일명으로 생성!
        } catch (IOException e) {
            throw new FileStorageException("파일 저장 중 오류가 발생했습니다: " + e.getMessage());
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

    /**
     * 저장된 파일 삭제
     */
    public void delete(String filename) {
        // 빈 파일 체크
        if (!StringUtils.hasText(filename) || filename.isBlank()) {
            return;
        }

        try {
            Path filePath = this.uploadPath.resolve(filename);
            Files.deleteIfExists(filePath); // 만약있으면 삭제

        } catch (IOException e) {
            // 삭제 실패는 로그만 남기고 진행 (파일이 이미 없을 수도 있음)
//            System.err.println("파일 삭제 실패: " + filename);
            log.error(e.getMessage());
            log.error("파일 삭제 실패: {}", filename); // @Slf4j 활용
        }
    }

    @Override
    public String getUrl(String key) {
        // 로컬 저장소의 경우 /images/ 경로로 접근
        return "/images/" + key;
    }

}
