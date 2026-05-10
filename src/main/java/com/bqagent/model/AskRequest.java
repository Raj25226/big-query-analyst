package com.bqagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class AskRequest {

    @NotBlank(message = "question must not be blank")
    private String question;

    // Optional: use a pre-created agent (from POST /agent)
    @JsonProperty("agent_id")
    private String agentId;

    // Optional: continue an existing conversation (multi-turn)
    @JsonProperty("conversation_id")
    private String conversationId;

    // Used when no agentId — inline BigQuery context
    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("dataset_id")
    private String datasetId;

    @JsonProperty("table_ids")
    private List<String> tableIds;
}
