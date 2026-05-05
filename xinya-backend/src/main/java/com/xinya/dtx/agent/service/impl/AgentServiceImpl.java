package com.xinya.dtx.agent.service.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xinya.dtx.agent.dto.AgentChatRequest;
import com.xinya.dtx.agent.dto.AgentChatResponse;
import com.xinya.dtx.agent.dto.AgentChatPayload;
import com.xinya.dtx.agent.dto.AgentStreamEvent;
import com.xinya.dtx.agent.dto.ConversationItemDto;
import com.xinya.dtx.agent.dto.NursePushContentDto;
import com.xinya.dtx.agent.dto.NursePushRequest;
import com.xinya.dtx.agent.dto.NursePushResponse;
import com.xinya.dtx.agent.dto.NurseSymptomTriggerPayload;
import com.xinya.dtx.agent.dto.RecommendationsResponse;
import com.xinya.dtx.agent.service.AgentService;
import com.xinya.dtx.clinical.dto.ClinicalStageInfoDto;
import com.xinya.dtx.clinical.service.ClinicalService;
import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.patient.entity.Patient;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.alerts.entity.Alert;
import com.xinya.dtx.agent.entity.Conversation;
import com.xinya.dtx.hopetree.entity.HopeTreeGrowthHistory;
import com.xinya.dtx.hopetree.entity.HopeTreeProgress;
import com.xinya.dtx.patient.entity.PsychEnergyLog;
import com.xinya.dtx.alerts.mapper.AlertMapper;
import com.xinya.dtx.agent.mapper.ConversationMapper;
import com.xinya.dtx.hopetree.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.dtx.hopetree.mapper.HopeTreeProgressMapper;
import com.xinya.dtx.patient.mapper.PsychEnergyLogMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final PatientMapper patientMapper;
    private final ConversationMapper conversationMapper;
    private final PsychEnergyLogMapper psychEnergyLogMapper;
    private final HopeTreeProgressMapper hopeTreeProgressMapper;
    private final HopeTreeGrowthHistoryMapper hopeTreeGrowthHistoryMapper;
    private final AlertMapper alertMapper;
    private final ClinicalService clinicalService;
    private final WebClient.Builder webClientBuilder;

    @Value("${xinya.ai.psych-base-url:http://localhost:9001}")
    private String psychBaseUrl;

    @Value("${xinya.ai.nurse-base-url:http://localhost:9002}")
    private String nurseBaseUrl;

    @Value("${xinya.ai.api-key:demo-key}")
    private String apiKey;

    @Override
    @Transactional
    public AgentChatResponse chat(AgentChatRequest request) {
        Patient patient = patientMapper.findById(request.getPatientId())
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        String agentType = request.getAgentType();
        if (!"psych".equals(agentType) && !"nurse".equals(agentType)) {
            throw new IllegalArgumentException("agentType 必须为 psych 或 nurse");
        }

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        long clientTs = request.getClientTimestamp() != null
                ? request.getClientTimestamp()
                : System.currentTimeMillis();

        // 1. 记录用户消息
        Conversation userMsg = Conversation.builder()
                .patientId(patient.getId())
                .agentType(agentType)
                .sessionId(sessionId)
                .message(request.getMessage())
                .isFromUser(true)
                .psychEnergyDelta(0)
                .hopeTreeExpDelta(0)
                .crisisAlert(false)
                .crisisLevel(null)
                .crisisKeywords(null)
                .emotionSignals(null)
                .clientTimestamp(clientTs)
                .build();
        userMsg = conversationMapper.save(userMsg);

        // 2. 组装调用 Agent 的上下文
        ClinicalStageInfoDto stageInfo = clinicalService.getCurrentStage(patient.getId());

        AgentChatPayload.PatientContext patientContext = AgentChatPayload.PatientContext.builder()
                .patientId(patient.getId())
                .name(patient.getName())
                .stage(patient.getStage())
                .stageName(stageInfo != null ? stageInfo.getStageName() : null)
                .daysInStage(stageInfo != null ? stageInfo.getDaysInStage() : null)
                .psychEnergy(patient.getPsychEnergy())
                .treeLevel(patient.getTreeLevel())
                .age(patient.getAge())
                .gender(patient.getGender())
                .diagnosis(patient.getDiagnosis())
                .build();

        // 最近 10 条对话
        Pageable recentPage = PageRequest.of(0, 10);
        List<Conversation> recent = conversationMapper.findRecentByPatientIdAndAgentType(
                patient.getId(), agentType, recentPage);
        List<AgentChatPayload.HistoryItem> historyList = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            Conversation c = recent.get(i);
            AgentChatPayload.HistoryItem h = AgentChatPayload.HistoryItem.builder()
                    .role(Boolean.TRUE.equals(c.getIsFromUser()) ? "user" : "assistant")
                    .content(c.getMessage())
                    .build();
            historyList.add(h);
        }
        AgentChatPayload payload = AgentChatPayload.builder()
                .sessionId(sessionId)
                .patientContext(patientContext)
                .history(historyList)
                .message(request.getMessage())
                .build();

        // 3. 调用对应 Agent
        String baseUrl = "psych".equals(agentType) ? psychBaseUrl : nurseBaseUrl;
        String path = "psych".equals(agentType) ? "/v1/psych/chat" : "/v1/nurse/chat";

        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        JsonObject agentResp;
        try {
            if ("psych".equals(agentType)) {
                // psych-agent 永远返回 SSE 流，等待 done 事件取出完整响应体
                agentResp = client.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("X-Api-Key", apiKey)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                        .filter(sse -> "done".equals(sse.event()))
                        .next()
                        .map(sse -> {
                            String data = sse.data();
                            if (data == null || data.isBlank()) return (JsonObject) null;
                            return com.google.gson.JsonParser.parseString(data).getAsJsonObject();
                        })
                        .block(Duration.ofSeconds(60));
            } else {
                // nurse-agent 返回普通 JSON
                String respJson = client.post()
                        .uri(path)
                        .header("Content-Type", "application/json")
                        .header("X-Api-Key", apiKey)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(30));
                agentResp = respJson != null
                        ? com.google.gson.JsonParser.parseString(respJson).getAsJsonObject()
                        : null;
            }
        } catch (WebClientResponseException e) {
            // 超时或 5xx 降级
            return buildFallbackResponse(patient, sessionId, agentType, userMsg);
        } catch (Exception e) {
            return buildFallbackResponse(patient, sessionId, agentType, userMsg);
        }

        if (agentResp == null) {
            return buildFallbackResponse(patient, sessionId, agentType, userMsg);
        }

        return persistAndBuildChatResponse(patient, sessionId, agentType, userMsg, agentResp);
    }

    @Override
    @Transactional
    public Flux<AgentStreamEvent> chatStream(AgentChatRequest request) {
        Patient patient = patientMapper.findById(request.getPatientId())
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        String agentType = request.getAgentType();
        if (!"psych".equals(agentType)) {
            throw new IllegalArgumentException("stream 接口仅支持 psych");
        }

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        final String finalSessionId = sessionId;

        long clientTs = request.getClientTimestamp() != null
                ? request.getClientTimestamp()
                : System.currentTimeMillis();

        // 1. 记录用户消息
        Conversation userMsg = Conversation.builder()
                .patientId(patient.getId())
                .agentType(agentType)
                .sessionId(sessionId)
                .message(request.getMessage())
                .isFromUser(true)
                .psychEnergyDelta(0)
                .hopeTreeExpDelta(0)
                .crisisAlert(false)
                .crisisLevel(null)
                .crisisKeywords(null)
                .emotionSignals(null)
                .clientTimestamp(clientTs)
                .build();
        userMsg = conversationMapper.save(userMsg);
        final Conversation finalUserMsg = userMsg;

        // 2. 组装调用 Agent 的上下文（与 chat() 保持一致）
        ClinicalStageInfoDto stageInfo = clinicalService.getCurrentStage(patient.getId());

        AgentChatPayload.PatientContext patientContext = AgentChatPayload.PatientContext.builder()
                .patientId(patient.getId())
                .name(patient.getName())
                .stage(patient.getStage())
                .stageName(stageInfo != null ? stageInfo.getStageName() : null)
                .daysInStage(stageInfo != null ? stageInfo.getDaysInStage() : null)
                .psychEnergy(patient.getPsychEnergy())
                .treeLevel(patient.getTreeLevel())
                .age(patient.getAge())
                .gender(patient.getGender())
                .diagnosis(patient.getDiagnosis())
                .build();

        Pageable recentPage = PageRequest.of(0, 10);
        List<Conversation> recent = conversationMapper.findRecentByPatientIdAndAgentType(
                patient.getId(), agentType, recentPage);
        List<AgentChatPayload.HistoryItem> historyList = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            Conversation c = recent.get(i);
            AgentChatPayload.HistoryItem h = AgentChatPayload.HistoryItem.builder()
                    .role(Boolean.TRUE.equals(c.getIsFromUser()) ? "user" : "assistant")
                    .content(c.getMessage())
                    .build();
            historyList.add(h);
        }
        AgentChatPayload payload = AgentChatPayload.builder()
                .sessionId(finalSessionId)
                .patientContext(patientContext)
                .history(historyList)
                .message(request.getMessage())
                .build();

        WebClient client = webClientBuilder.baseUrl(psychBaseUrl).build();

        AtomicReference<JsonObject> doneObjRef = new AtomicReference<>(null);

        // 使用 ServerSentEvent<String> 类型参数，让 WebClient 的 SSE codec 正确解析
        // event 名称和 data 字段，避免只拿到裸 data 字符串而丢失 event 类型。
        Flux<SseEvent> events = client.post()
                .uri("/v1/psych/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("X-Api-Key", apiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(sse -> {
                    String eventName = sse.event() != null ? sse.event() : "message";
                    String dataText = sse.data() != null ? sse.data() : "{}";
                    JsonObject dataJson = null;
                    try {
                        dataJson = com.google.gson.JsonParser.parseString(dataText).getAsJsonObject();
                    } catch (Exception ignore) {
                        // 非 JSON 数据忽略
                    }
                    return new SseEvent(eventName, dataText, dataJson);
                })
                .filter(e -> e.event != null)
                .takeUntil(e -> "done".equals(e.event) || "error".equals(e.event))
                .doOnNext(e -> {
                    if ("done".equals(e.event) && e.dataJson != null) {
                        doneObjRef.set(e.dataJson);
                    }
                })
                .doOnComplete(() -> {
                    JsonObject doneObj = doneObjRef.get();
                    if (doneObj != null) {
                        // done 时执行与 chat() 一致的落库/业务更新逻辑
                        persistAndBuildChatResponse(patient, finalSessionId, agentType, finalUserMsg, doneObj);
                    }
                });

        // 透传 SSE（保持 start/delta/done/error 协议不变）
        return events
                .map(e -> new AgentStreamEvent(
                        e.event,
                        e.dataText != null ? e.dataText : "{}"
                ))
                .onErrorResume(err -> Flux.just(new AgentStreamEvent(
                        "error",
                        "{\"error\":\"internal_error\",\"message\":\"" + safeJsonString(err.getMessage()) + "\"}"
                )));
    }

    private AgentChatResponse persistAndBuildChatResponse(Patient patient,
                                                         String sessionId,
                                                         String agentType,
                                                         Conversation userMsg,
                                                         JsonObject agentResp) {
        // 解析 Agent 响应
        String reply = getAsString(agentResp, "reply", "对不起，我现在有点忙，请稍后再试。");
        List<String> questions = getAsStringList(agentResp, "recommendedQuestions");

        int psychDelta = 0;
        int hopeExpDelta = 0;
        boolean crisisAlert = false;
        String crisisLevel = null;
        String crisisKeywords = null;
        String emotionSignals = null;

        if ("psych".equals(agentType)) {
            JsonObject energy = getAsObject(agentResp, "energyAssessment");
            if (energy != null) {
                psychDelta = getAsInt(energy, "totalDelta", 0);
                hopeExpDelta = getAsInt(energy, "hopeTreeExpDelta", 0);
            }
            JsonObject crisis = getAsObject(agentResp, "crisisAssessment");
            if (crisis != null) {
                crisisAlert = getAsBoolean(crisis, "crisisAlert", false);
                crisisLevel = getAsString(crisis, "crisisLevel", null);
                crisisKeywords = joinStringArray(crisis, "crisisKeywords");
                emotionSignals = joinStringArray(crisis, "emotionSignals");
            }
        }

        // 更新心理能量 & 希望之树 & 预警
        if (psychDelta != 0) {
            updatePsychEnergy(patient, psychDelta, "conversation", "conv-" + userMsg.getId());
        }
        if (hopeExpDelta > 0) {
            addHopeTreeExp(patient, hopeExpDelta, "conversation", "conv-" + userMsg.getId());
        }
        if (crisisAlert) {
            createAlert(patient, crisisLevel, crisisKeywords, reply);
        }

        // 记录 AI 回复
        Conversation aiMsg = Conversation.builder()
                .patientId(patient.getId())
                .agentType(agentType)
                .sessionId(sessionId)
                .message(reply)
                .isFromUser(false)
                .psychEnergyDelta(psychDelta)
                .hopeTreeExpDelta(hopeExpDelta)
                .crisisAlert(crisisAlert)
                .crisisLevel(crisisLevel)
                .crisisKeywords(crisisKeywords)
                .emotionSignals(emotionSignals)
                .clientTimestamp(System.currentTimeMillis())
                .build();
        conversationMapper.save(aiMsg);

        return AgentChatResponse.builder()
                .reply(reply)
                .sessionId(sessionId)
                .psychEnergyDelta(psychDelta)
                .recommendedQuestions(questions)
                .crisisAlert(crisisAlert)
                .hopeTreeExpDelta(hopeExpDelta)
                .build();
    }

    private static final class SseEvent {
        private final String event;
        private final String dataText;
        private final JsonObject dataJson;

        private SseEvent(String event, String dataText, JsonObject dataJson) {
            this.event = event;
            this.dataText = dataText;
            this.dataJson = dataJson;
        }

        // no text rendering here; controller uses SseEmitter
    }

    private static String safeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    @Transactional
    public RecommendationsResponse getRecommendations(String patientId, String agentType) {
        AgentChatRequest req = new AgentChatRequest();
        req.setPatientId(patientId);
        req.setAgentType(agentType);
        req.setMessage("推荐一些我可以继续问的问题。");
        AgentChatResponse resp = chat(req);
        List<String> questions = resp.getRecommendedQuestions();
        if (questions == null) {
            questions = Collections.emptyList();
        }
        return RecommendationsResponse.builder()
                .questions(questions)
                .build();
    }

    @Override
    @Transactional
    public PageResult<ConversationItemDto> getHistory(String patientId, String agentType, String sessionId,
                                                      Integer page, Integer pageSize) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }
        int p = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Pageable pageable = PageRequest.of(p, size);

        Page<Conversation> cPage;
        if (sessionId != null && !sessionId.isBlank()) {
            cPage = conversationMapper.findByPatientIdAndSessionId(patientId, sessionId, pageable);
        } else if (agentType != null && !agentType.isBlank()) {
            cPage = conversationMapper.findByPatientIdAndAgentTypeOrderByCreatedAtDesc(
                    patientId, agentType, pageable);
        } else {
            cPage = conversationMapper.findByPatientIdOrderByCreatedAtDesc(patientId, pageable);
        }

        List<ConversationItemDto> list = cPage.getContent().stream()
                .map(c -> ConversationItemDto.builder()
                        .id(c.getId())
                        .sessionId(c.getSessionId())
                        .agentType(c.getAgentType())
                        .message(c.getMessage())
                        .isFromUser(c.getIsFromUser())
                        .psychEnergyDelta(c.getPsychEnergyDelta())
                        .crisisAlert(c.getCrisisAlert())
                        .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return PageResult.<ConversationItemDto>builder()
                .list(list)
                .total(cPage.getTotalElements())
                .page(p + 1)
                .pageSize(size)
                .build();
    }

    @Override
    public NursePushResponse nursePush(NursePushRequest request) {
        // 这里先直接调用 nurse /v1/nurse/symptom-trigger，后续可根据需要补充本地兜底逻辑
        WebClient client = webClientBuilder.baseUrl(nurseBaseUrl).build();

        Patient patient = patientMapper.findById(request.getPatientId()).orElse(null);
        NurseSymptomTriggerPayload.PatientContext ctx = null;
        if (patient != null) {
            // 简化：仅传 stage/currentEnergy，后续可按文档补全字段
            ctx = NurseSymptomTriggerPayload.PatientContext.builder()
                    .stage(patient.getStage())
                    .psychEnergy(patient.getPsychEnergy())
                    .build();
        }

        NurseSymptomTriggerPayload payload = NurseSymptomTriggerPayload.builder()
                .patientId(request.getPatientId())
                .patientContext(ctx)
                .triggerSource(request.getTriggerType())
                .build();

        JsonObject agentResp;
        try {
            String respJson = client.post()
                    .uri("/v1/nurse/symptom-trigger")
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", apiKey)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            agentResp = respJson != null
                    ? com.google.gson.JsonParser.parseString(respJson).getAsJsonObject()
                    : null;
        } catch (Exception e) {
            return NursePushResponse.builder()
                    .recommendedContents(Collections.emptyList())
                    .build();
        }
        if (agentResp == null) {
            return NursePushResponse.builder()
                    .recommendedContents(Collections.emptyList())
                    .build();
        }

        List<NursePushContentDto> contents = new ArrayList<>();
        if (agentResp.has("recommendedContents") && agentResp.get("recommendedContents").isJsonArray()) {
            for (JsonElement el : agentResp.getAsJsonArray("recommendedContents")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                NursePushContentDto dto = NursePushContentDto.builder()
                        .contentId(getAsString(o, "contentId", null))
                        .title(getAsString(o, "title", null))
                        .contentType(getAsString(o, "contentType", null))
                        .thumbnailUrl(getAsString(o, "thumbnailUrl", null))
                        .durationSeconds(getAsInt(o, "durationSeconds", 0))
                        .relevanceScore(getAsDouble(o, "relevanceScore", 0.0))
                        .build();
                contents.add(dto);
            }
        }
        return NursePushResponse.builder()
                .recommendedContents(contents)
                .build();
    }

    private AgentChatResponse buildFallbackResponse(Patient patient, String sessionId, String agentType,
                                                    Conversation userMsg) {
        String fallback =
                "当前服务暂时繁忙，我已经记录下你的这条消息了，请稍后再试一次，可以吗？";
        Conversation aiMsg = Conversation.builder()
                .patientId(patient.getId())
                .agentType(agentType)
                .sessionId(sessionId)
                .message(fallback)
                .isFromUser(false)
                .psychEnergyDelta(0)
                .hopeTreeExpDelta(0)
                .crisisAlert(false)
                .clientTimestamp(System.currentTimeMillis())
                .build();
        conversationMapper.save(aiMsg);
        return AgentChatResponse.builder()
                .reply(fallback)
                .sessionId(sessionId)
                .psychEnergyDelta(0)
                .recommendedQuestions(Collections.emptyList())
                .crisisAlert(false)
                .hopeTreeExpDelta(0)
                .build();
    }

    private void updatePsychEnergy(Patient patient, int delta, String triggerType, String sourceRef) {
        int before = patient.getPsychEnergy() != null ? patient.getPsychEnergy() : 0;
        int after = Math.max(0, Math.min(100, before + delta));
        patientMapper.addPsychEnergy(patient.getId(), delta);

        PsychEnergyLog log = PsychEnergyLog.builder()
                .patientId(patient.getId())
                .logDate(LocalDate.now())
                .psychEnergy(after)
                .delta(delta)
                .triggerType(triggerType)
                .sourceRef(sourceRef)
                .build();
        psychEnergyLogMapper.save(log);
    }

    private void addHopeTreeExp(Patient patient, int exp, String source, String sourceRefId) {
        HopeTreeProgress progress = hopeTreeProgressMapper.findByPatientId(patient.getId())
                .orElse(null);
        int levelBefore = progress != null && progress.getCurrentLevel() != null
                ? progress.getCurrentLevel()
                : 1;
        hopeTreeProgressMapper.addExp(patient.getId(), exp, LocalDateTime.now());

        HopeTreeGrowthHistory history = HopeTreeGrowthHistory.builder()
                .patientId(patient.getId())
                .growthSource(source)
                .expAmount(exp)
                .levelBefore(levelBefore)
                .levelAfter(levelBefore)
                .levelUp(false)
                .sourceRefId(sourceRefId)
                .build();
        hopeTreeGrowthHistoryMapper.save(history);
    }

    private void createAlert(Patient patient, String level, String crisisKeywords, String reply) {
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .patientId(patient.getId())
                .patientName(patient.getName())
                .alertType("crisis")
                .level(level != null ? level : "warning")
                .message("心理智能体检测到潜在危机信号，请及时评估。回复内容：" + reply)
                .triggerMessage(crisisKeywords)
                .resolved(false)
                .build();
        alertMapper.save(alert);
    }

    private String getAsString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }

    private int getAsInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double getAsDouble(JsonObject obj, String key, double defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean getAsBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private JsonObject getAsObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonObject()) {
            return null;
        }
        return obj.get(key).getAsJsonObject();
    }

    private List<String> getAsStringList(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray(key)) {
            if (!el.isJsonNull()) {
                list.add(el.getAsString());
            }
        }
        return list;
    }

    private String joinStringArray(JsonObject obj, String key) {
        List<String> list = getAsStringList(obj, key);
        if (list.isEmpty()) {
            return null;
        }
        return String.join(",", list);
    }
}

