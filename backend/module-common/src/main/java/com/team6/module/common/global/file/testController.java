package com.team6.module.common.global.file;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class testController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("folder") String folder) {

        String uploadUrl = fileService.upload(file, folder);

        // 결과 확인을 위해 JSON 형태로 반환
        return ResponseEntity.ok(Map.of(
                "message", "파일 업로드 성공",
                "url", uploadUrl
        ));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteFile(
            @RequestParam("fileUrl") String fileUrl) {

        fileService.delete(fileUrl);

        return ResponseEntity.ok(Map.of(
                "message", "파일 삭제 성공",
                "deletedUrl", fileUrl
        ));
    }

}
