package com.bqagent.controller;

import com.bqagent.model.AskRequest;
import com.bqagent.model.AskResponse;
import com.bqagent.model.CreateAgentRequest;
import com.bqagent.service.ConversationalAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
public class AgentController {

    private final ConversationalAnalyticsService caService;

    @Value("${gcp.project.id}")
    private String defaultProjectId;

    public AgentController(ConversationalAnalyticsService caService) {
        this.caService = caService;
    }

    // ── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "ok",
                "api",     "geminidataanalytics.googleapis.com",
                "project", defaultProjectId
        ));
    }

    // ── POST /ask — clean answer ──────────────────────────────────────────────
    //
    // Two modes:
    //  • Inline context (no agent_id): send project_id + dataset_id + table_ids every time
    //  • Agent mode    (with agent_id): reuse a pre-created agent (faster, more accurate)
    //
    // Multi-turn: pass conversation_id from a previous response to continue the thread.

    @PostMapping("/ask")
    public Mono<ResponseEntity<AskResponse>> ask(
            @Valid @RequestBody AskRequest req,
            @RequestParam(defaultValue = "false") boolean debug) {

        log.info("POST /ask — question: {} | conversationId: {}", req.getQuestion(), req.getConversationId());
        return caService.ask(req, debug)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error in /ask: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(AskResponse.builder()
                                    .answer("Error: " + e.getMessage())
                                    .build()));
                });
    }

    // ── POST /ask/raw — full NDJSON stream from CA API ────────────────────────
    //   Returns every line the agent sent back: THOUGHT, PROGRESS, SQL, text, charts.
    //   Great for debugging — you can see the agent's full reasoning chain.

    @PostMapping("/ask/raw")
    public Mono<ResponseEntity<String>> askRaw(@Valid @RequestBody AskRequest req) {
        log.info("POST /ask/raw — question: {} | conversationId: {}", req.getQuestion(), req.getConversationId());
        return caService.askRaw(req)
                .map(raw -> ResponseEntity.ok()
                        .header("Content-Type", "application/x-ndjson")
                        .body(raw))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.internalServerError().body("Error: " + e.getMessage())));
    }

    // ── POST /agents/create — create a persistent data agent ─────────────────
    //
    // Creates a reusable agent in GCP tied to your BigQuery tables.
    // After creation, use the returned agent_id in /ask requests.
    // You only need to do this once (or when your schema changes).

    @PostMapping("/agents/create")
    public Mono<ResponseEntity<JsonNode>> createAgent(
            @Valid @RequestBody CreateAgentRequest req) {

        log.info("POST /agents/create — agentId: {}", req.getAgentId());
        return caService.createAgent(req)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error in /agents/create: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    // ── POST /conversations/create — create a new conversation ────────────────
    //
    // Creates a new empty conversation resource in GCP.
    // Use the returned "name" field as the conversation_id in your /ask requests.

    @PostMapping("/conversations/create")
    public Mono<ResponseEntity<JsonNode>> createConversation(
            @RequestParam(required = false) String projectId) {

        log.info("POST /conversations/create");
        return caService.createConversation(projectId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error in /conversations/create: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}