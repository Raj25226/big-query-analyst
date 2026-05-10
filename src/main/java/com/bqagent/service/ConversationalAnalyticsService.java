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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ConversationalAnalyticsService {

    // API base — geminidataanalytics.googleapis.com
    private static final String CA_BASE = "https://geminidataanalytics.googleapis.com/v1beta";
    private static final String LOCATION = "global";

    private static final String DEFAULT_SYSTEM_INSTRUCTION = """
            You are a data analyst assistant with access to BigQuery.
            When asked a question:
            1. Explore the available tables and their schemas.
            2. Write and execute the appropriate SQL.
            3. Interpret the results clearly and concisely.
            If a query fails, debug and retry.
            """;

    @Value("${gcp.project.id}")
    private String defaultProjectId;

    private final GoogleAuthService gcpAuthService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ConversationalAnalyticsService(GoogleAuthService gcpAuthService,
                                          WebClient webClient,
                                          ObjectMapper objectMapper) {
        this.gcpAuthService = gcpAuthService;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    // ── Auth helper ───────────────────────────────────────────────────────────

    private String bearerToken() {
        return "Bearer " + gcpAuthService.getBearerToken();
    }

    private String resolveProject(String requestProject) {
        return (requestProject != null && !requestProject.isBlank())
                ? requestProject : defaultProjectId;
    }

    // ── Build BigQuery table references ───────────────────────────────────────

    private ArrayNode buildTableRefs(String projectId, String datasetId, List<String> tableIds) {
        ArrayNode refs = objectMapper.createArrayNode();

        if (tableIds != null && !tableIds.isEmpty()) {
            for (String tableId : tableIds) {
                ObjectNode ref = objectMapper.createObjectNode();
                ref.put("project_id", projectId);
                ref.put("dataset_id", datasetId);
                ref.put("table_id", tableId);
                refs.add(ref);
            }
        } else if (datasetId != null && !datasetId.isBlank()) {
            // No specific tables — reference the whole dataset (agent auto-discovers)
            ObjectNode ref = objectMapper.createObjectNode();
            ref.put("project_id", projectId);
            ref.put("dataset_id", datasetId);
            refs.add(ref);
        }

        return refs;
    }

    // ── Create a persistent Data Agent ───────────────────────────────────────

    public Mono<JsonNode> createAgent(CreateAgentRequest req) {
        String projectId = resolveProject(req.getProjectId());
        String agentId   = req.getAgentId();
        String url = String.format("%s/projects/%s/locations/%s/dataAgents?dataAgentId=%s",
                CA_BASE, projectId, LOCATION, agentId);

        String sysInstruction = req.getSystemInstructions() != null
                ? req.getSystemInstructions() : DEFAULT_SYSTEM_INSTRUCTION;

        // Build table references
        ArrayNode tableRefs = buildTableRefs(projectId, req.getDatasetId(), req.getTableIds());
        ObjectNode bqRefs = objectMapper.createObjectNode();
        bqRefs.set("table_references", tableRefs);

        ObjectNode datasourceRefs = objectMapper.createObjectNode();
        datasourceRefs.set("bq", bqRefs);

        ObjectNode publishedContext = objectMapper.createObjectNode();
        publishedContext.put("system_instruction", sysInstruction);
        publishedContext.set("datasource_references", datasourceRefs);

        ObjectNode dataAnalyticsAgent = objectMapper.createObjectNode();
        dataAnalyticsAgent.set("published_context", publishedContext);

        ObjectNode agentPayload = objectMapper.createObjectNode();
        agentPayload.put("name",
                String.format("projects/%s/locations/%s/dataAgents/%s", projectId, LOCATION, agentId));
        agentPayload.set("data_analytics_agent", dataAnalyticsAgent);

        log.info("Creating data agent [{}] in project [{}]", agentId, projectId);

        return webClient.post()
                .uri(url)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .header("x-goog-user-project", projectId)
                .bodyValue(agentPayload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException(
                                    "Create agent failed [" + resp.statusCode() + "]: " + body))))
                .bodyToMono(JsonNode.class);
    }

    // ── Create a Conversation ─────────────────────────────────────────────────

    public Mono<JsonNode> createConversation(String requestProject) {
        String projectId = resolveProject(requestProject);
        String url = String.format("%s/projects/%s/locations/%s/conversations",
                CA_BASE, projectId, LOCATION);

        // The API requires either an 'agents' list or a non-empty 'context' object to be set.
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode context = objectMapper.createObjectNode();
        context.put("system_instruction", DEFAULT_SYSTEM_INSTRUCTION);
        payload.set("context", context);

        log.info("Creating a new conversation in project [{}]", projectId);

        return webClient.post()
                .uri(url)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .header("x-goog-user-project", projectId)
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
        String projectId = resolveProject(req.getProjectId());
        String chatUrl;
        if (req.getConversationId() != null && !req.getConversationId().isBlank()) {
            chatUrl = String.format("%s/projects/%s/locations/%s/conversations/%s:chat",
                    CA_BASE, projectId, LOCATION, req.getConversationId());
        } else {
            chatUrl = String.format("%s/projects/%s/locations/%s:chat",
                    CA_BASE, projectId, LOCATION);
        }

        // Build the chat payload
        ObjectNode payload  = objectMapper.createObjectNode();
        String      parent  = String.format("projects/%s/locations/%s", projectId, LOCATION);
        payload.put("parent", parent);

        // User message
        ArrayNode messages  = objectMapper.createArrayNode();
        ObjectNode userMsg  = objectMapper.createObjectNode();
        ObjectNode userText = objectMapper.createObjectNode();
        userText.put("text", req.getQuestion());
        userMsg.set("userMessage", userText);
        messages.add(userMsg);
        payload.set("messages", messages);

        // Data agent context — use existing agent or inline context
        if (req.getAgentId() != null && !req.getAgentId().isBlank()) {
            // Use a pre-created data agent
            ObjectNode ctx = objectMapper.createObjectNode();
            ctx.put("data_agent", String.format(
                    "projects/%s/locations/%s/dataAgents/%s", projectId, LOCATION, req.getAgentId()));

            payload.set("data_agent_context", ctx);
            log.info("Chatting with agent [{}] | project [{}] | question: {}",
                    req.getAgentId(), projectId, req.getQuestion());

        } else {
            // Inline context — no pre-created agent needed
            String sysInstruction = DEFAULT_SYSTEM_INSTRUCTION;
            ArrayNode tableRefs = buildTableRefs(
                    projectId, req.getDatasetId(), req.getTableIds());

            ObjectNode bqRefs       = objectMapper.createObjectNode();
            bqRefs.set("table_references", tableRefs);
            ObjectNode datasourceRef = objectMapper.createObjectNode();
            datasourceRef.set("bq", bqRefs);

            ObjectNode inlineCtx = objectMapper.createObjectNode();
            inlineCtx.put("system_instruction", sysInstruction);
            inlineCtx.set("datasource_references", datasourceRef);
            payload.set("inline_context", inlineCtx);

            log.info("Chatting with inline context | project [{}] | dataset [{}] | question: {}",
                    projectId, req.getDatasetId(), req.getQuestion());
        }

        return webClient.post()
                .uri(chatUrl)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .header("x-goog-user-project", projectId)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException(
                                    "CA API chat failed [" + resp.statusCode() + "]: " + body))))
                // Response is newline-delimited JSON — collect as a single string
                .bodyToMono(String.class)
                .map(raw -> parseStreamedResponse(raw, req.getAgentId(),
                        req.getConversationId(), includeThoughts));
    }

    // ── Parse the JSON array response ───────────────────────────────────────

    /**
     * The CA API returns a JSON array of message objects.
     * Each element has one of:
     *   {"systemMessage": {"text": {"parts": [...], "textType": "THOUGHT"}}}     — reasoning step
     *   {"systemMessage": {"text": {"parts": [...], "textType": "PROGRESS"}}}    — progress update
     *   {"systemMessage": {"text": {"parts": [...], "textType": "FINAL_RESPONSE"}}} — final answer
     *   {"systemMessage": {"text": {"parts": [...], "textType": "FOLLOWUP_QUESTIONS"}}} — suggestions
     *   {"agentMessage": {"text": "..."}}                                         — legacy text answer
     *   {"agentMessage": {"query": {"sql": "..."}}}                               — SQL executed
     */
    private AskResponse parseStreamedResponse(
            String rawBody, String agentId, String conversationId, boolean includeThoughts) {

        List<String> thoughts       = new ArrayList<>();
        List<String> followUps      = new ArrayList<>();
        StringBuilder answer        = new StringBuilder();
        String[]      sqlHolder     = new String[1];
        String        convId        = conversationId;

        // Try parsing as a JSON array first (actual API format)
        try {
            JsonNode root = objectMapper.readTree(rawBody);

            if (root.isArray()) {
                for (JsonNode node : root) {
                    processNode(node, thoughts, followUps, answer, convId, sqlHolder);

                    // Extract SQL from agentMessage (if present)
                    JsonNode agentMsg = node.path("agentMessage");
                    if (!agentMsg.isMissingNode()) {
                        JsonNode queryNode = agentMsg.path("query");
                        if (!queryNode.isMissingNode() && queryNode.has("sql")) {
                            sqlHolder[0] = queryNode.path("sql").asText();
                        }
                        // Legacy: agentMessage.text as plain string
                        if (agentMsg.has("text") && agentMsg.get("text").isTextual()) {
                            String t = agentMsg.path("text").asText("").trim();
                            if (!t.isBlank()) {
                                if (!answer.isEmpty()) answer.append("\n");
                                answer.append(t);
                            }
                        }
                    }

                    // Conversation ID
                    JsonNode convNode = node.path("conversationId");
                    if (!convNode.isMissingNode()) {
                        convId = convNode.asText();
                    }
                }
            } else {
                // Single object — process it directly
                processNode(root, thoughts, followUps, answer, convId, sqlHolder);
            }

        } catch (Exception e) {
            // Fallback: try line-by-line NDJSON parsing
            log.warn("Failed to parse as JSON array, falling back to NDJSON: {}", e.getMessage());
            for (String line : rawBody.split("\n")) {
                line = line.trim();
                if (line.isBlank() || line.equals("[") || line.equals("]")
                        || line.equals(",") || line.startsWith("//")) continue;
                if (line.endsWith(",")) line = line.substring(0, line.length() - 1);
                try {
                    JsonNode node = objectMapper.readTree(line);
                    processNode(node, thoughts, followUps, answer, convId, sqlHolder);
                } catch (Exception ex) {
                    log.debug("Skipping unparseable line: {}", line);
                }
            }
        }

        return AskResponse.builder()
                .answer(answer.toString().isBlank() ? "No answer returned from agent." : answer.toString())
                .sqlUsed(sqlHolder[0])
                .conversationId(convId)
                .agentId(agentId)
                .thoughts(includeThoughts ? thoughts : null)
                .build();
    }

    /**
     * Process a single response node, extracting text from the actual CA API format:
     *   systemMessage.text.parts  → array of strings
     *   systemMessage.text.textType → "THOUGHT", "PROGRESS", "FINAL_RESPONSE", "FOLLOWUP_QUESTIONS"
     *   systemMessage.data.result → table data
     *   systemMessage.data.generatedSql → actual SQL
     */
    private void processNode(JsonNode node, List<String> thoughts, List<String> followUps,
                             StringBuilder answer, String convId, String[] sqlHolder) {
        JsonNode sysMsg = node.path("systemMessage");
        if (sysMsg.isMissingNode()) return;

        // 1. Text processing
        JsonNode textObj = sysMsg.path("text");
        if (!textObj.isMissingNode()) {
            if (textObj.isObject()) {
                String textType = textObj.path("textType").asText("");
                JsonNode partsNode = textObj.path("parts");

                if (partsNode.isArray()) {
                    for (JsonNode part : partsNode) {
                        String partText = part.asText("").trim();
                        if (partText.isBlank()) continue;

                        switch (textType) {
                            case "FINAL_RESPONSE" -> {
                                if (!answer.isEmpty()) answer.append("\n");
                                answer.append(partText);
                            }
                            case "THOUGHT", "PROGRESS" -> thoughts.add(partText);
                            case "FOLLOWUP_QUESTIONS" -> followUps.add(partText);
                            default -> log.debug("Unknown textType [{}]: {}", textType, partText);
                        }
                    }
                }
            } else if (textObj.isTextual()) {
                // Legacy format: text is a plain string
                String text = textObj.asText("").trim();
                if (!text.isBlank()) {
                    thoughts.add(text);
                }
            }
        }

        // 2. Data Processing (SQL and Table Results)
        JsonNode dataObj = sysMsg.path("data");
        if (!dataObj.isMissingNode()) {
            // Check for generated SQL
            if (dataObj.has("generatedSql")) {
                sqlHolder[0] = dataObj.get("generatedSql").asText();
            }

            // Check for table results to append as a Markdown table
            JsonNode result = dataObj.path("result");
            if (!result.isMissingNode() && result.has("schema") && result.has("data")) {
                JsonNode fields = result.path("schema").path("fields");
                JsonNode rows = result.path("data");

                if (fields.isArray() && rows.isArray() && !rows.isEmpty()) {
                    StringBuilder tableMd = new StringBuilder("\n\n");

                    // Headers
                    List<String> colNames = new ArrayList<>();
                    for (JsonNode field : fields) {
                        colNames.add(field.path("name").asText("Unknown"));
                    }
                    tableMd.append("| ").append(String.join(" | ", colNames)).append(" |\n");

                    // Separator
                    tableMd.append("|");
                    for (int i = 0; i < colNames.size(); i++) tableMd.append("---|");
                    tableMd.append("\n");

                    // Rows
                    for (JsonNode row : rows) {
                        tableMd.append("|");
                        for (String colName : colNames) {
                            String val = row.path(colName).asText("");
                            tableMd.append(" ").append(val).append(" |");
                        }
                        tableMd.append("\n");
                    }
                    tableMd.append("\n");

                    answer.append(tableMd);
                }
            }
        }
    }

    // ── Raw chat response (for debugging) ────────────────────────────────────

    public Mono<String> askRaw(AskRequest req) {
        String projectId = resolveProject(req.getProjectId());
        String chatUrl;
        if (req.getConversationId() != null && !req.getConversationId().isBlank()) {
            chatUrl = String.format("%s/projects/%s/locations/%s/conversations/%s:chat",
                    CA_BASE, projectId, LOCATION, req.getConversationId());
        } else {
            chatUrl = String.format("%s/projects/%s/locations/%s:chat",
                    CA_BASE, projectId, LOCATION);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("parent", String.format("projects/%s/locations/%s", projectId, LOCATION));

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode msg     = objectMapper.createObjectNode();
        ObjectNode text    = objectMapper.createObjectNode();
        text.put("text", req.getQuestion());
        msg.set("userMessage", text);
        messages.add(msg);
        payload.set("messages", messages);

        if (req.getAgentId() != null && !req.getAgentId().isBlank()) {
            ObjectNode ctx = objectMapper.createObjectNode();
            ctx.put("data_agent", String.format(
                    "projects/%s/locations/%s/dataAgents/%s", projectId, LOCATION, req.getAgentId()));
            payload.set("data_agent_context", ctx);
        } else {
            ArrayNode tableRefs     = buildTableRefs(projectId, req.getDatasetId(), req.getTableIds());
            ObjectNode bqRefs       = objectMapper.createObjectNode();
            bqRefs.set("table_references", tableRefs);
            ObjectNode datasourceRef = objectMapper.createObjectNode();
            datasourceRef.set("bq", bqRefs);
            ObjectNode inlineCtx    = objectMapper.createObjectNode();
            inlineCtx.put("system_instruction", DEFAULT_SYSTEM_INSTRUCTION);
            inlineCtx.set("datasource_references", datasourceRef);
            payload.set("inline_context", inlineCtx);
        }

        return webClient.post()
                .uri(chatUrl)
                .header("Authorization", bearerToken())
                .header("Content-Type", "application/json")
                .header("x-goog-user-project", projectId)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class);
    }
}