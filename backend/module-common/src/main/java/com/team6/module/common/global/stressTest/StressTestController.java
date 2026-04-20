package com.team6.module.common.global.stressTest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/stress-test")
public class StressTestController {

    /**
     * 1. CPU 부하 테스트 (복잡한 연산)
     * k6에서 요청을 보낼 때 'loop' 파라미터로 난이도를 조절하세요.
     */
    @GetMapping("/cpu")
    public ResponseEntity<String> cpuStress(@RequestParam(defaultValue = "100000") int loop) {
        long sum = 0;
        for (int i = 0; i < loop; i++) {
            sum += Math.sqrt(i) * Math.atan(i); // 의미 없는 연산 반복
        }
        return ResponseEntity.ok("CPU Stress Success: " + sum);
    }

    /**
     * 2. 메모리 부하 테스트 (객체 생성)
     * 주의: 너무 크게 잡으면 Heap Space Error로 서버가 죽을 수 있습니다.
     */
    @GetMapping("/memory")
    public ResponseEntity<String> memoryStress(@RequestParam(defaultValue = "10000") int size) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(UUID.randomUUID().toString()); // 랜덤 문자열로 메모리 점유
        }
        return ResponseEntity.ok("Memory Stress Success: Allocated " + list.size() + " objects");
    }

    /**
     * 3. 지연 시간 테스트 (Thread Sleep)
     * DB나 외부 API 호출 시의 병목 현상을 시뮬레이션합니다.
     */
    @GetMapping("/delay")
    public ResponseEntity<String> delayStress(@RequestParam(defaultValue = "500") int ms) {
        try {
            Thread.sleep(ms); // 의도적 지연
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ResponseEntity.ok("Delay Stress Success: " + ms + "ms");
    }

}
