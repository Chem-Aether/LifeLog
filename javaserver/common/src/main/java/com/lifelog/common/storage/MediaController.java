package com.lifelog.common.storage;

import com.lifelog.common.api.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MediaController {
    private final MediaStorageService storage;

    public MediaController(MediaStorageService storage) {
        this.storage = storage;
    }

    @PostMapping("/api/media")
    public Result upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(defaultValue = "shared") String category,
                         @RequestParam(name = "media_type", defaultValue = "image") String mediaType) {
        return Result.success("媒体上传成功", storage.upload(file, category, mediaType));
    }

    @DeleteMapping("/api/media")
    public Result delete(@RequestParam String url) {
        storage.delete(url);
        return Result.success("媒体已删除");
    }
}
