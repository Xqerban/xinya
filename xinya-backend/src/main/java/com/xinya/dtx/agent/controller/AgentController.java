package com.xinya.dtx.agent.controller;

import com.xinya.dtx.agent.dto.AgentChatRequest;
import com.xinya.dtx.agent.dto.AgentChatResponse;
import com.xinya.dtx.agent.dto.ConversationItemDto;
import com.xinya.dtx.agent.dto.NursePushRequest;
import com.xinya.dtx.agent.dto.NursePushResponse;
import com.xinya.dtx.agent.dto.RecommendationsResponse;
import com.xinya.dtx.agent.service.AgentService;
import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.common.response.PageResult;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        try {
            AgentChatResponse resp = agentService.chat(request);
            return ApiResponse.success(resp);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationsResponse> getRecommendations(
            @RequestParam("patientId") String patientId,
            @RequestParam("agentType") String agentType) {
        try {
            RecommendationsResponse resp = agentService.getRecommendations(patientId, agentType);
            return ApiResponse.success(resp);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/history")
    public ApiResponse<PageResult<ConversationItemDto>> history(
            @RequestParam("patientId") String patientId,
            @RequestParam(value = "agentType", required = false) String agentType,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        try {
            PageResult<ConversationItemDto> result =
                    agentService.getHistory(patientId, agentType, sessionId, page, pageSize);
            return ApiResponse.success(result);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @PostMapping("/nurse/push")
    public ApiResponse<NursePushResponse> nursePush(@RequestBody NursePushRequest request) {
        NursePushResponse resp = agentService.nursePush(request);
        return ApiResponse.success(resp);
    }
}

