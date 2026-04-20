package com.team6.module.common.global.file;

import com.team6.module.common.global.file.exception.FileDeleteFailedException;
import com.team6.module.common.global.file.exception.FileUploadFailedException;
import com.team6.module.common.global.file.exception.InvalidFileTypeException;
import com.team6.module.common.global.file.exception.InvalidFileUrlException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3FileStorageAdapter implements FileStoragePort {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket:}")
    private String bucket;

    @Value("${cloud.aws.s3.public-url:https://%s.s3.amazonaws.com}")
    private String publicUrlFormat;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    @Override
    public String upload(MultipartFile file, String folder) {
        String key = createKey(file.getOriginalFilename(), folder);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            try (var in = file.getInputStream()) {
                s3Client.putObject(
                        putObjectRequest,
                        RequestBody.fromInputStream(in, file.getSize())
                );
            }

        } catch (IOException | SdkException e) {
            throw new FileUploadFailedException();
        }

        return getPublicUrl(key);
    }

    @Override
    public void deleteByUrl(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);

        } catch (SdkException e) {
            throw new FileDeleteFailedException();
        }
    }

    private String createKey(String originalFilename, String folder) {
        String extension = getExtension(originalFilename);
        return folder + "/" + UUID.randomUUID() + "." + extension;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".") || filename.endsWith(".")) {
            throw new InvalidFileTypeException();
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1)
                .toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileTypeException();
        }

        return extension;
    }

    private String getPublicUrl(String key) {
        String[] parts = key.split("/");
        String encodedKey = java.util.Arrays.stream(parts)
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
        return String.format(publicUrlFormat, bucket) + "/" + encodedKey;
    }

    private String extractKeyFromUrl(String url) {
        int index = url.indexOf(".amazonaws.com/");
        if (index == -1) {
            throw new InvalidFileUrlException();
        }
        return url.substring(index + ".amazonaws.com/".length());
    }
}