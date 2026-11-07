package com.osc.store.service;

import com.osc.store.config.ApiException;
import com.osc.store.config.AppProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores images and customer documents in the MinIO (S3) bucket. */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    public static final String IMAGE_URL_PREFIX = "/api/files/images/";
    private static final String IMAGE_FOLDER = "images/";
    private static final String DOCUMENT_FOLDER = "documents/";
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp",
            "image/avif", "avif");

    private static final List<String> DOCUMENT_EXTENSIONS = List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "csv", "txt", "ppt", "pptx", "zip", "png", "jpg", "jpeg");

    private final MinioClient minio;
    private final AppProperties props;
    private final String bucket;
    private volatile boolean bucketReady;

    public FileStorageService(MinioClient minio, AppProperties props) {
        this.minio = minio;
        this.props = props;
        this.bucket = props.getMinio().getBucket();
    }

    @PostConstruct
    public void syncBucket() {
        AppProperties.Minio cfg = props.getMinio();
        if ((cfg.getPublicUrl() == null || cfg.getPublicUrl().isBlank()) && cfg.getEndpoint() != null) {
            String endpoint = cfg.getEndpoint().replaceAll("/+$", "");
            cfg.setPublicUrl(endpoint + "/" + bucket);
        }
        try {
            ensureBucket();
            log.info("MinIO bucket '{}' is ready at {}", bucket, cfg.getPublicUrl());
        } catch (Exception ex) {
            log.warn("MinIO bucket '{}' could not be created yet: {}", bucket, ex.getMessage());
        }
    }

    public record StoredFile(String path, String name, long size) {
    }

    public record FileData(InputStream stream, String contentType, long size) {
    }

    public StoredFile storeImage(MultipartFile file) {
        requireFile(file);
        String ext = IMAGE_TYPES.get(String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT));
        if (ext == null) {
            throw ApiException.badRequest("Please upload a JPG, PNG, GIF, WebP or AVIF image");
        }
        String name = UUID.randomUUID() + "." + ext;
        put(IMAGE_FOLDER + name, file, file.getContentType());
        return new StoredFile(IMAGE_URL_PREFIX + name, file.getOriginalFilename(), file.getSize());
    }

    /** Stores an optional customer document (BOM, RFQ, tender document, ...). Returns the object key. */
    public StoredFile storeDocument(MultipartFile file) {
        requireFile(file);
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!DOCUMENT_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("\"" + original + "\" is not a supported file type. Allowed: "
                    + String.join(", ", DOCUMENT_EXTENSIONS));
        }
        String key = DOCUMENT_FOLDER + UUID.randomUUID() + "." + ext;
        String type = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        put(key, file, type);
        return new StoredFile(key, original, file.getSize());
    }

    public FileData openImage(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            throw ApiException.notFound("File");
        }
        return open(IMAGE_FOLDER + name);
    }

    public FileData openDocument(String key) {
        if (!key.startsWith(DOCUMENT_FOLDER)) {
            throw ApiException.notFound("File");
        }
        return open(key);
    }

    /** Best effort removal of a previously uploaded image, given the path stored in the database. */
    public void deleteImageQuietly(String path) {
        if (path == null || !path.startsWith(IMAGE_URL_PREFIX)) {
            return;
        }
        String name = path.substring(IMAGE_URL_PREFIX.length());
        if (!SAFE_NAME.matcher(name).matches()) {
            return;
        }
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(IMAGE_FOLDER + name).build());
        } catch (Exception ex) {
            log.warn("Could not delete image {}: {}", name, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private void requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
    }

    private void put(String key, MultipartFile file, String contentType) {
        try (InputStream in = file.getInputStream()) {
            ensureBucket();
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Upload to MinIO failed", ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable right now. Please try again shortly.");
        }
    }

    private FileData open(String key) {
        try {
            StatObjectResponse stat = minio.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            InputStream stream = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
            return new FileData(stream, stat.contentType(), stat.size());
        } catch (ErrorResponseException ex) {
            throw ApiException.notFound("File");
        } catch (Exception ex) {
            log.error("Read from MinIO failed", ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable right now");
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (!bucketReady) {
                if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                bucketReady = true;
            }
        }
    }
}
