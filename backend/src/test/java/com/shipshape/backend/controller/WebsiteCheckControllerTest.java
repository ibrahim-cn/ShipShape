package com.shipshape.backend.controller;

import com.shipshape.backend.dto.WebsiteCheckRequest;
import com.shipshape.backend.service.WebsiteCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebsiteCheckController.class)
class WebsiteCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WebsiteCheckService websiteCheckService;

    @Test
    void validRequestReturnsResponse() throws Exception {
        com.shipshape.backend.dto.WebsiteCheckResponse resp = new com.shipshape.backend.dto.WebsiteCheckResponse();
        resp.setUrl("https://example.com");
        resp.setReachable(true);
        resp.setHttpStatusCode(200);
        resp.setResponseTimeMs(100L);
        resp.setHttpsEnabled(true);
        resp.setOverallStatus("PASS");
        resp.setSecurityHeaders(java.util.List.of());
        resp.setFindings(java.util.List.of());

        when(websiteCheckService.check(anyString())).thenReturn(resp);

        WebsiteCheckRequest req = new WebsiteCheckRequest("https://example.com");
        mockMvc.perform(post("/api/checks/website")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.httpStatusCode").value(200))
                .andExpect(jsonPath("$.httpsEnabled").value(true));
    }
}
