package com.bqagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAgentRequest {

    @NotBlank(message = "agent name must not be blank")
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @NotBlank(message = "lookerModel must not be blank")
    @JsonProperty("looker_model")
    private String lookerModel;

    @NotBlank(message = "lookerExplore must not be blank")
    @JsonProperty("looker_explore")
    private String lookerExplore;
}
