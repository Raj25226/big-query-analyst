package com.bqagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AskResponse {

    private String answer;

    @JsonProperty("sql_used")
    private String sqlUsed;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("agent_id")
    private String agentId;

    // Only included when you call POST /ask/thoughts
    private List<String> thoughts;
}
