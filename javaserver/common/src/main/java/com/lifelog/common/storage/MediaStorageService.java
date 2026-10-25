package com.lifelog.common.storage;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "media.storage", name = "endpoint")
public class MediaStorageService {
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp", "gif", "image/gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mov", "webm", "3gp", "avi");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
    private final MediaStorageProperties properties;
    private final S3Client s3;
    private volatile boolean bucketPrepared;

    public MediaStorageService(MediaStorageProperties properties) {
        this.properties = properties;
        this.s3 = S3Client.builder()
                .endpointOverride(java.net.URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.getAccessKey(), properties.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    public Map<String, Object> upload(MultipartFile file, String category, String mediaType) {
        if (!Set.of("travel", "diary", "bill", "shared").contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "媒体分类无效");
        }
        if (!Set.of("image", "video").contains(mediaType) || file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "媒体文件无效");
        }
        long limit = "image".equals(mediaType) ? properties.getMaxImageBytes() : properties.getMaxVideoBytes();
        if (file.getSize() > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "媒体文件超过大小限制");

        String extension = extension(file.getOriginalFilename());
        String contentType = contentType(file, mediaType, extension);
        String key = category + "/" + YearMonth.now(ZoneOffset.UTC).format(MONTH) + "/" + UUID.randomUUID() + "." + extension;
        try {
            ensureBucket();
            s3.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket()).key(key).contentType(contentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "写入 SeaweedFS 失败", exception);
        }

        return Map.of(
                "url", objectPath(key),
                "content_type", contentType,
                "media_type", mediaType,
                "size_bytes", file.getSize());
    }

    public void delete(String url) {
        String key = keyFromUrl(url);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
        } catch (S3Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "从 SeaweedFS 删除失败", exception);
        }
    }

    public String objectPath(String key) {
        return "/" + properties.getBucket() + "/" + key;
    }

    public String keyFromUrl(String url) {
        String prefix = "/" + properties.getBucket() + "/";
        if (!StringUtils.hasText(url) || !url.startsWith(prefix) || url.contains("://") || url.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "媒体 URL 不属于当前对象存储");
        }
        String key = url.substring(prefix.length());
        if (key.isBlank() || key.contains("..") || key.startsWith("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "媒体 URL 无效");
        }
        return key;
    }

    private synchronized void ensureBucket() {
        if (bucketPrepared) return;
        try {
            s3.headBucket(builder -> builder.bucket(properties.getBucket()));
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404 && exception.statusCode() != 301) throw exception;
            s3.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
        }
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"PublicReadObjects\",\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + properties.getBucket() + "/*\"]}]}";
        s3.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(properties.getBucket()).policy(policy).build());
        s3.putBucketCors(PutBucketCorsRequest.builder().bucket(properties.getBucket())
                .corsConfiguration(CORSConfiguration.builder().corsRules(CORSRule.builder()
                        .allowedOrigins("*").allowedMethods("GET", "HEAD").allowedHeaders("*")
                        .exposeHeaders("Content-Length", "Content-Type", "ETag").maxAgeSeconds(3600).build())
                        .build()).build());
        bucketPrepared = true;
    }

    private static String extension(String filename) {
        if (filename == null || !filename.contains(".")) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件扩展名无效");
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.containsKey(extension) && !VIDEO_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的媒体格式");
        }
        return extension;
    }

    private static String contentType(MultipartFile file, String mediaType, String extension) {
        if ("image".equals(mediaType)) {
            String type = IMAGE_TYPES.get(extension);
            if (type == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "图片扩展名无效");
            try {
                byte[] signature = file.getBytes();
                boolean valid = switch (type) {
                    case "image/jpeg" -> signature.length >= 3 && (signature[0] & 0xff) == 0xff && (signature[1] & 0xff) == 0xd8 && (signature[2] & 0xff) == 0xff;
                    case "image/png" -> signature.length >= 8 && signature[0] == (byte) 0x89 && signature[1] == 0x50 && signature[2] == 0x4e && signature[3] == 0x47;
                    case "image/gif" -> signature.length >= 6 && new String(signature, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).startsWith("GIF8");
                    case "image/webp" -> signature.length >= 12 && new String(signature, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(signature, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
                    default -> false;
                };
                if (!valid) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "图片内容与扩展名不匹配");
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取媒体文件失败", exception);
            }
            return type;
        }
        if (Set.of("mp4", "m4v", "mov").contains(extension)) {
            try {
                byte[] header;
                try (var input = file.getInputStream()) { header = input.readNBytes(12); }
                if (header.length < 12 || !"ftyp".equals(new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "视频内容无效");
                }
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取视频文件失败", exception);
            }
        }
        return switch (extension) {
            case "mp4", "m4v" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "3gp" -> "video/3gpp";
            case "avi" -> "video/x-msvideo";
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的视频格式");
        };
    }
}
