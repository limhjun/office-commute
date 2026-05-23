package com.company.officecommute.controller.commute;

import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class CommuteHistoryControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private CommuteHistoryService commuteHistoryService;

    @Test
    @DisplayName("POST /commute — 같은 날 중복 출근이면 409 DUPLICATE_WORK")
    void registerWorkStartTime_duplicateWorkReturns409() {
        doThrow(new DuplicateWorkOnDateException(LocalDate.of(2026, 5, 23)))
                .when(commuteHistoryService).registerWorkStartTime(2L);

        assertThat(mockMvcTester
                .post()
                .uri("/commute")
                .session(memberSession()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "DUPLICATE_WORK",
                            "message": "해당 일자에 이미 출근 기록이 존재합니다: 2026-05-23"
                        }
                        """);
    }

    private MockHttpSession memberSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 2L);
        session.setAttribute("currentRole", Role.MEMBER);
        return session;
    }
}
