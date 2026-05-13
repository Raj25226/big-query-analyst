package com.bqagent.service;

import com.bqagent.model.AskRequest;
import com.bqagent.model.AskResponse;
import com.bqagent.model.CreateAgentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ConversationalAnalyticsService {

    @Value("${looker.base.url}")
    private String lookerBaseUrl;

    private final LookerAuthService lookerAuthService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ConversationalAnalyticsService(LookerAuthService lookerAuthService,
                                          WebClient webClient,
                                          ObjectMapper objectMapper) {
        this.lookerAuthService = lookerAuthService;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    private String bearerToken() {
        return "Bearer " + lookerAuthService.getBearerToken();
    }

    // ── Create an Agent ───────────────────────────────────────────────────────

    public Mono<JsonNode> createAgent(CreateAgentRequest req) {
        String url = lookerBaseUrl + "/api/4.0/agents";

        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "looker");
        source.put("model", req.getLookerModel());
        source.put("explore", req.getLookerExplore());

        ArrayNode sources = objectMapper.createArrayNode();
        sources.add(source);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", req.getName());
        payload.put("description", req.getDescription() != null ? req.getDescription() : "Looker AI Agent");
        payload.put("category", "conversation");
        payload.set("sources", sources);
        payload.put("code_interpreter", true);

        log.info("Creating agent [{}] with Looker explore [{}]", req.getName(), req.getLookerExplore());

        return webClient.post()
                .uri(url)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException(
                                    "Create agent failed [" + resp.statusCode() + "]: " + body))))
                .bodyToMono(JsonNode.class);
    }

    // ── Create a Conversation ─────────────────────────────────────────────────

    public Mono<JsonNode> createConversation(String projectId) {
        String url = lookerBaseUrl + "/api/4.0/conversations";

        ObjectNode payload = objectMapper.createObjectNode();
        
        log.info("Creating a new Looker conversation");

        return webClient.post()
                .uri(url)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Create conversation failed [" + resp.statusCode() + "]: " + body))))
                .bodyToMono(JsonNode.class);
    }

    // ── Chat (main ask method) ────────────────────────────────────────────────

    public Mono<AskResponse> ask(AskRequest req, boolean includeThoughts) {
        String chatUrl = lookerBaseUrl + "/api/4.0/conversational_analytics/chat";

        ObjectNode payload = objectMapper.createObjectNode();
        
        if (req.getConversationId() != null && !req.getConversationId().isBlank()) {
            payload.put("conversation_id", req.getConversationId());
        } else {
            return Mono.error(new IllegalArgumentException("Looker Conversational Analytics requires a conversation_id. Call /conversations/create first."));
        }
        
        payload.put("user_message", req.getQuestion());

        log.info("Chatting with Looker API | conversation [{}] | question: {}", 
                 req.getConversationId(), req.getQuestion());

        return webClient.post()
                .uri(chatUrl)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException(
                                    "Looker CA API chat failed [" + resp.statusCode() + "]: " + body))))
                .bodyToMono(String.class)
                .map(raw -> parseResponse(raw, req.getConversationId(), includeThoughts));
    }

    // ── Parse the Looker Response ─────────────────────────────────────────────

    private AskResponse parseResponse(String rawBody, String conversationId, boolean includeThoughts) {
        StringBuilder answer = new StringBuilder();
        
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            
            // Looker typically returns the agent's message inside a 'message' or 'response' field depending on streaming state
            // Let's attempt to extract standard text fields
            if (root.has("system_message")) {
                answer.append(root.get("system_message").asText());
            } else if (root.has("message")) {
                answer.append(root.get("message").asText());
            } else {
                // If it's a raw string or an array of streams, just return the raw payload for now
                answer.append(rawBody);
            }

            // Note: In Looker, you must explicitly persist messages to maintain history.
            // A robust implementation would call POST /api/4.0/conversations/{conversationId}/messages here.

        } catch (Exception e) {
            log.warn("Failed to parse Looker response, returning raw. {}", e.getMessage());
            answer.append(rawBody);
        }

        return AskResponse.builder()
                .answer(answer.toString().isBlank() ? "No answer returned from agent." : answer.toString())
                .conversationId(conversationId)
                .build();
    }

    // ── Raw chat response (for debugging) ────────────────────────────────────

    public Mono<String> askRaw(AskRequest req) {
        String chatUrl = lookerBaseUrl + "/api/4.0/conversational_analytics/chat";

        ObjectNode payload = objectMapper.createObjectNode();
        if (req.getConversationId() != null && !req.getConversationId().isBlank()) {
            payload.put("conversation_id", req.getConversationId());
        }
        payload.put("user_message", req.getQuestion());

        return webClient.post()
                .uri(chatUrl)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class);
    }
}