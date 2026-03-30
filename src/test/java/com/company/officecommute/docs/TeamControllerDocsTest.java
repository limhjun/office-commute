package com.company.officecommute.docs;

import com.company.officecommute.controller.team.TeamController;
import com.company.officecommute.dto.team.request.TeamRegisterRequest;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.service.team.TeamService;
import com.company.officecommute.support.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerDocsTest extends RestDocsSupport {

    private final TeamService teamService = mock(TeamService.class);

    @Override
    protected Object initController() {
        return new TeamController(teamService);
    }

    @Test
    @DisplayName("팀 등록 API")
    void registerTeam() throws Exception {
        TeamRegisterRequest request = new TeamRegisterRequest("개발팀");
        doNothing().when(teamService).registerTeam(any(TeamRegisterRequest.class));

        mockMvc.perform(post("/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("team-register",
                        requestFields(
                                fieldWithPath("teamName").type(JsonFieldType.STRING)
                                        .description("등록할 팀 이름")
                        )
                ));
    }

    @Test
    @DisplayName("팀 전체 조회 API")
    void findAllTeam() throws Exception {
        List<TeamFindResponse> responses = List.of(
                new TeamFindResponse("개발팀", "홍길동", 5),
                new TeamFindResponse("기획팀", "김철수", 3)
        );
        given(teamService.findTeam()).willReturn(responses);

        mockMvc.perform(get("/team")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("team-find-all",
                        responseFields(
                                fieldWithPath("[].name").type(JsonFieldType.STRING)
                                        .description("팀 이름"),
                                fieldWithPath("[].managerName").type(JsonFieldType.STRING)
                                        .description("팀 관리자 이름"),
                                fieldWithPath("[].memberCount").type(JsonFieldType.NUMBER)
                                        .description("팀 멤버 수")
                        )
                ));
    }
}
