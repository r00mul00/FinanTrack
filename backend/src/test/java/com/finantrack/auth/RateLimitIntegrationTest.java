package com.finantrack.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.auth.service.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
        "finantrack.rate-limit.ip-limit=4",
        "finantrack.rate-limit.email-limit=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailSender emailSender;

    @Test
    void emailLimitBlocksAttemptsEvenFromDifferentAddresses() throws Exception {
        login("alvo@exemplo.com", "10.0.0.1").andExpect(status().isUnauthorized());
        login("alvo@exemplo.com", "10.0.0.2").andExpect(status().isUnauthorized());

        login("ALVO@exemplo.com", "10.0.0.3").andExpect(status().isTooManyRequests());
    }

    @Test
    void ipLimitBlocksAttemptsAcrossDifferentEmails() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("pessoa" + i + "@exemplo.com", "10.0.0.9").andExpect(status().isUnauthorized());
        }

        login("outra@exemplo.com", "10.0.0.9").andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"senha-qualquer\"}".formatted(email)));
    }
}
