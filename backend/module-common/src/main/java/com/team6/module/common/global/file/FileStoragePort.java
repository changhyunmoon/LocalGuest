package com.team6.module.common.global.file;

import org.springframework.web.multipart.MultipartFile;

public interface FileStoragePort {
    String upload(MultipartFile file, String folderName);

    void deleteByUrl(String fileUrl);
}
