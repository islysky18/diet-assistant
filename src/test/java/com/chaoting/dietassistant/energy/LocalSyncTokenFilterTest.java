package com.chaoting.dietassistant.energy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSyncTokenFilterTest {
    @Test void absentConfigurationLeavesHealthApiDisabled() throws Exception {
        LocalSyncTokenFilter filter = new LocalSyncTokenFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/health/daily-energy/2026-08-05");
        request.addHeader("Authorization", "Bearer any-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Invalid or missing local sync token.\"}");
    }
}
