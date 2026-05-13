package com.bqagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Slf4j
@Service
public class LookerAuthService {

    @Value("${looker.base.url}")
    private String lookerBaseUrl;

    @Value("${looker.client.id}")
    private String clientId;

    @Value("${looker.client.secret}")
    private String clientSecret;

    private final WebClient webClient;
    private String currentToken = null;
    private Instant tokenExpiration = null;

    public LookerAuthService(WebClient webClient) {
        this.webClient = webClient;
    }

    public synchronized String getBearerToken() {
        // Refresh token if it's null or expires within the next 30 seconds
        if (currentToken != null && tokenExpiration != null && Instant.now().plusSeconds(30).isBefore(tokenExpiration)) {
            return currentToken;
        }

        log.info("Authenticating with Looker at {}", lookerBaseUrl);
        String url = lookerBaseUrl + "/api/4.0/login";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        JsonNode response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response != null && response.has("access_token")) {
            currentToken = response.get("access_token").asText();
            
            long expiresInSeconds = 3600; // Default to 1 hour if not provided
            if (response.has("expires_in")) {
                expiresInSeconds = response.get("expires_in").asLong();
            }
            tokenExpiration = Instant.now().plusSeconds(expiresInSeconds);
            
            return currentToken;
        }

        throw new RuntimeException("Failed to get Looker access token. Ensure client ID and secret are correct.");
    }
}
