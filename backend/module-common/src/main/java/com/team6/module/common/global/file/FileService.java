package com.team6.module.common.global.file;
import com.team6.module.common.global.file.exception.EmptyFolderNameException;
import com.team6.module.common.global.file.exception.FileNotFoundException;
import com.team6.module.common.global.file.exception.InvalidFileUrlException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileStoragePort fileStoragePort;

    public String upload(MultipartFile file, String folder) {
        validate(file);
        validateFolder(folder);
        return fileStoragePort.upload(file, folder);
    }

    public void delete(String fileUrl) {
        validateUrl(fileUrl);
        fileStoragePort.deleteByUrl(fileUrl);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileNotFoundException();
        }
    }

    private void validateFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            throw new EmptyFolderNameException();
        }
    }

    private void validateUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new InvalidFileUrlException();
        }
    }
}

