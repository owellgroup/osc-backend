package com.osc.store.controller;

import com.osc.store.model.dto.UploadResponse;
import com.osc.store.service.FileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
public class FileController {

    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }

    /** Public: product and banner images are served through the API so the bucket can stay private. */
    @GetMapping("/api/files/images/{name}")
    public ResponseEntity<InputStreamResource> image(@PathVariable String name) {
        FileStorageService.FileData data = storage.openImage(name);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(data.contentType()))
                .contentLength(data.size())
                .body(new InputStreamResource(data.stream()));
    }

    @PostMapping("/api/admin/uploads/image")
    public UploadResponse uploadImage(@RequestParam("file") MultipartFile file) {
        FileStorageService.StoredFile stored = storage.storeImage(file);
        return new UploadResponse(stored.path(), stored.name(), stored.size());
    }
}