package com.firstapi.backend.controller;

import com.firstapi.backend.model.MonitorData;
import com.firstapi.backend.service.MonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonitorControllerTest {

    private MonitorService monitorService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        monitorService = mock(MonitorService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MonitorController(monitorService)).build();
    }

    @Test
    void systemMonitorEndpointSupportsStartAndEndTimeQuery() throws Exception {
        when(monitorService.getSystemMonitorData("6h", "2026-03-20 10:00:00", "2026-03-20 12:00:00"))
                .thenReturn(new MonitorData());

        mockMvc.perform(get("/api/admin/monitor/system")
                        .param("timeRange", "6h")
                        .param("startTime", "2026-03-20 10:00:00")
                        .param("endTime", "2026-03-20 12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(monitorService).getSystemMonitorData("6h", "2026-03-20 10:00:00", "2026-03-20 12:00:00");
    }
}
