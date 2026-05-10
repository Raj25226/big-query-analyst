package com.bqagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class CreateAgentRequest {

    @NotBlank(message = "agentId must not be blank")
    @JsonProperty("agent_id")
    private String agentId;

    @NotBlank(message = "datasetId must not be blank")
    @JsonProperty("dataset_id")
    private String datasetId;

    // Optional: specific tables — blank means expose whole dataset
    @JsonProperty("table_ids")
    private List<String> tableIds;

    // Optional: override the GCP project
    @JsonProperty("project_id")
    private String projectId;

    // Optional: custom system instructions for this agent
    @JsonProperty("system_instructions")
    private String systemInstructions;
}
