package com.lifelog.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "media.storage")
public class MediaStorageProperties {
    private String endpoint = "http://127.0.0.1:8333";
    private String region = "us-east-1";
    private String bucket = "lifelog-media";
    private String accessKey = "lifelog";
    private String secretKey = "123456";
    private long maxImageBytes = 20L * 1024 * 1024;
    private long maxVideoBytes = 1024L * 1024 * 1024;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public long getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
    public long getMaxVideoBytes() { return maxVideoBytes; }
    public void setMaxVideoBytes(long maxVideoBytes) { this.maxVideoBytes = maxVideoBytes; }
}
