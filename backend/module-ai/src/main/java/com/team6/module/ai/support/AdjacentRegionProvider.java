package com.team6.module.ai.support;

import com.team6.module.ai.config.LocalGuestAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 인접 지역: YAML({@link LocalGuestAiProperties#getAdjacentRegions()})에 키가 있으면 그 목록을 쓰고,
 * 없으면 {@link AdjacentRegionMap} 기본값을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class AdjacentRegionProvider {

    private final LocalGuestAiProperties properties;

    public Set<String> neighbors(String region) {
        if (region == null || region.isBlank()) {
            return Set.of();
        }
        String r = region.trim();
        Map<String, List<String>> cfg = properties.getAdjacentRegions();
        if (cfg != null && !cfg.isEmpty() && cfg.containsKey(r)) {
            List<String> list = cfg.get(r);
            if (list == null || list.isEmpty()) {
                return Set.of();
            }
            return new LinkedHashSet<>(list);
        }
        return AdjacentRegionMap.neighbors(r);
    }
}
