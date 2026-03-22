package com.firstapi.backend.service;

import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayRoute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelayModelRouterTest {

    private final RelayModelRouter router = new RelayModelRouter();

    @Test
    void routesClaudeModelsToClaudeProvider() {
        RelayRoute route = router.route("claude-3-5-sonnet");

        assertThat(route.getProvider()).isEqualTo("claude");
    }

    @Test
    void routesGptModelsToOpenAiProvider() {
        RelayRoute route = router.route("gpt-4o-mini");

        assertThat(route.getProvider()).isEqualTo("openai");
    }

    @Test
    void rejectsUnknownModels() {
        assertThatThrownBy(() -> router.route("mystery-model"))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("Unsupported model");
    }
}
