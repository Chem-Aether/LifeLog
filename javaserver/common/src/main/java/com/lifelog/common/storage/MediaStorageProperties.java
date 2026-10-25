package com.lifelog.common.storage;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "media.storage", name = "endpoint")
@ConfigurationProperties(prefix = "media.storage")
@Data
public class MediaStorageProperties {
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private long maxImageBytes;
    private long maxVideoBytes;
}
