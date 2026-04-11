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

    /**
     * 여행자 희망 지역과 가이드 활동 지역이 다르지만, 내장/YAML 인접 맵상 이웃이면 true.
     */
    public boolean isAdjacentTo(String travelerRegion, String guideRegion) {
        if (travelerRegion == null || guideRegion == null) {
            return false;
        }
        String tr = travelerRegion.trim();
        String gr = guideRegion.trim();
        if (tr.isEmpty() || gr.isEmpty()) {
            return false;
        }
        if (tr.equalsIgnoreCase(gr)) {
            return false;
        }
        return neighbors(tr).stream().anyMatch(n -> n.equalsIgnoreCase(gr));
    }
}
