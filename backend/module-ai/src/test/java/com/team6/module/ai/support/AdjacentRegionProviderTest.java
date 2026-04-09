package com.team6.module.ai.support;

import com.team6.module.ai.config.LocalGuestAiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdjacentRegionProviderTest {

    @Test
    void neighbors_should_use_builtin_when_yaml_empty() {
        AdjacentRegionProvider provider = new AdjacentRegionProvider(new LocalGuestAiProperties());
        assertThat(provider.neighbors("부산")).contains("경주", "울산");
    }

    @Test
    void neighbors_should_override_region_when_yaml_has_key() {
        LocalGuestAiProperties props = new LocalGuestAiProperties();
        props.setAdjacentRegions(Map.of("강릉", List.of("테스트인접")));
        AdjacentRegionProvider provider = new AdjacentRegionProvider(props);
        assertThat(provider.neighbors("강릉")).containsExactly("테스트인접");
        assertThat(provider.neighbors("부산")).contains("경주");
    }
}
