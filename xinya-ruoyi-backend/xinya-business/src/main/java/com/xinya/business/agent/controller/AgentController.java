package com.xinya.business.agent.controller;

import com.xinya.business.agent.dto.*;
import com.xinya.business.agent.service.AgentService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Tag(name = "AI 对话")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "患者端 AI 流式对话")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody AgentChatRequest request) {
        return agentService.chat(request);
    }

    @Operation(summary = "医护端 AI 流式对话")
    @PostMapping(value = "/nurse-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> nurseChat(@RequestBody NurseChatRequest request) {
        return agentService.nurseChat(request);
    }

    @Operation(summary = "获取/创建会话")
    @GetMapping("/conversation/{patientId}")
    public R<ConversationDto> getConversation(@PathVariable String patientId) {
        return R.ok(agentService.getOrCreateConversation(patientId));
    }
}
