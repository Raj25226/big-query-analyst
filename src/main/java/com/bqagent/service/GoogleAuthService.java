package com.bqagent.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class GoogleAuthService {

    private static final List<String> SCOPES = List.of(
        "https://www.googleapis.com/auth/cloud-platform"
    );

    @Value("${gcp.sa.key.path:}")
    private String saKeyPath;

    private GoogleCredentials credentials;

    @PostConstruct
    public void init() throws IOException {
        if (saKeyPath != null && !saKeyPath.isBlank()) {
            log.info("Loading credentials from SA file: {}", saKeyPath);
            try (var stream = new FileInputStream(saKeyPath)) {
                credentials = ServiceAccountCredentials
                        .fromStream(stream)
                        .createScoped(SCOPES);
            }
        } else {
            log.info("No SA key path — using Application Default Credentials");
            credentials = GoogleCredentials
                        .getApplicationDefault()
                        .createScoped(SCOPES);
        }
    }

    public String getBearerToken() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get GCP access token", e);
        }
    }
}
