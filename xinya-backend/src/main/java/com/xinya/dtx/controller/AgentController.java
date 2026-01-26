package com.xinya.dtx.controller;

import com.xinya.dtx.dto.AgentChatResponse;
import com.xinya.dtx.dto.ApiResponse;
import com.xinya.dtx.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "智能体对话", description = "小芽(心理陪护)和小护士(护理宣教)的对话接口")
public class AgentController {
    
    private final AgentService agentService;
    
    @PostMapping("/chat")
    @Operation(summary = "发送消息进行对话", description = "支持两种智能体类型：psych(小芽-心理陪护)、nurse(小护士-护理宣教)")
    public ApiResponse<AgentChatResponse> chat(@RequestBody ChatRequest request) {
        AgentChatResponse response = agentService.chat(
            request.getPatientId(),
            request.getAgentType(),
            request.getMessage(),
            request.getSessionId()
        );
        return ApiResponse.success(response);
    }
    
    @GetMapping("/recommendations")
    @Operation(summary = "获取推荐问题列表")
    public ApiResponse<Map<String, List<String>>> getRecommendedQuestions(
        @RequestParam String patientId,
        @RequestParam String agentType
    ) {
        List<String> questions = agentService.getRecommendedQuestions(patientId, agentType);
        return ApiResponse.success(Map.of("questions", questions));
    }
    
    @Data
    static class ChatRequest {
        private String patientId;
        private String agentType;  // "psych" | "nurse"
        private String message;
        private String sessionId;
    }
}
