package kr.java.upload_exception.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // org.springframework.beans.factory.annotation.Value;
    @Value("${file.upload-dir}") // @Value 필드 주입
    private String uploadDir; // 경로 찾을때 사용

    @Value("${file.storage.type}")
    private String storageType;

    /**
     * 정적 리소스 핸들러 설정
     *
     * URL 요청과 실제 파일 위치를 연결
     * 예: /images/abc.jpg 요청 → uploads/abc.jpg 파일 반환
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (storageType.equals("local")) {
            // uploadDir의 절대 경로 계산
            String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
            System.out.println("WebConfig - absolutePath = " + absolutePath);

            registry.addResourceHandler("/images/**")  // URL 패턴
                    .addResourceLocations("file:" + absolutePath + "/")  // 실제 디렉토리
                    .setCachePeriod(3600);  // 캐시 유지 시간 (초) - 브라우저가 1시간 동안 이미지 재요청 안 함
        }
    }
}
