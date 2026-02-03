/*
 * Tines Webhook Client
 *
 * HTTP client for calling Tines webhooks from PingFederate.
 */
package com.tines.pingfederate.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for communicating with Tines webhooks.
 * Handles JSON serialization, HTTP connection management, and response parsing.
 */
public class TinesWebhookClient {

    private static final Logger LOG = LoggerFactory.getLogger(TinesWebhookClient.class);

    private final String webhookUrl;
    private final int timeoutSeconds;
    private final String secretToken;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    /**
     * Creates a new Tines webhook client
     *
     * @param webhookUrl The Tines webhook URL
     * @param timeoutSeconds HTTP request timeout in seconds
     * @param secretToken Optional secret token for authentication
     */
    public TinesWebhookClient(String webhookUrl, int timeoutSeconds, String secretToken) {
        this.webhookUrl = webhookUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.secretToken = secretToken;

        // Configure Jackson ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Configure HTTP client with connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50);
        connectionManager.setDefaultMaxPerRoute(10);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(timeoutSeconds, TimeUnit.SECONDS))
            .setResponseTimeout(Timeout.of(timeoutSeconds, TimeUnit.SECONDS))
            .build();

        this.httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    /**
     * Calls the Tines webhook with the given payload
     *
     * @param payload The data to send to Tines
     * @return TinesWebhookResponse containing the result
     */
    public TinesWebhookResponse callWebhook(Map<String, Object> payload) {
        LOG.debug("Calling Tines webhook: {}", maskUrl(webhookUrl));

        HttpPost httpPost = new HttpPost(webhookUrl);

        try {
            // Serialize payload to JSON
            String jsonPayload = objectMapper.writeValueAsString(payload);
            LOG.debug("Webhook payload size: {} bytes", jsonPayload.length());

            // Set request body
            StringEntity entity = new StringEntity(jsonPayload, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            // Set headers
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("User-Agent", "PingFederate-Tines-Adapter/1.0");

            // Add secret token header if configured
            if (secretToken != null && !secretToken.isEmpty()) {
                httpPost.setHeader("X-Tines-Secret", secretToken);
            }

            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                LOG.debug("Tines webhook response: status={}, bodyLength={}",
                    statusCode, responseBody != null ? responseBody.length() : 0);

                return parseResponse(statusCode, responseBody);
            }

        } catch (IOException e) {
            LOG.error("Error calling Tines webhook: {}", e.getMessage(), e);
            return TinesWebhookResponse.error("Connection error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error calling Tines webhook: {}", e.getMessage(), e);
            return TinesWebhookResponse.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Parses the webhook response
     */
    @SuppressWarnings("unchecked")
    private TinesWebhookResponse parseResponse(int statusCode, String responseBody) {
        if (statusCode >= 200 && statusCode < 300) {
            // Success response
            try {
                if (responseBody != null && !responseBody.isEmpty()) {
                    Map<String, Object> data = objectMapper.readValue(responseBody, Map.class);

                    // Check if the response indicates an error
                    if (data.containsKey("error") && Boolean.TRUE.equals(data.get("error"))) {
                        String message = data.containsKey("message")
                            ? data.get("message").toString()
                            : "Tines returned error";
                        return TinesWebhookResponse.error(message);
                    }

                    // Check for status field
                    if (data.containsKey("status")) {
                        String status = data.get("status").toString().toLowerCase();
                        if (status.equals("error") || status.equals("failure") || status.equals("denied")) {
                            String message = data.containsKey("message")
                                ? data.get("message").toString()
                                : "Authentication denied by Tines";
                            return TinesWebhookResponse.error(message);
                        }
                    }

                    return TinesWebhookResponse.success(data);
                } else {
                    // Empty response body - treat as success with no data
                    return TinesWebhookResponse.success(Map.of());
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse Tines response as JSON: {}", e.getMessage());
                // Return success with raw response
                return TinesWebhookResponse.success(Map.of("rawResponse", responseBody));
            }
        } else if (statusCode == 401 || statusCode == 403) {
            LOG.error("Tines webhook authentication failed: {}", statusCode);
            return TinesWebhookResponse.error("Webhook authentication failed (HTTP " + statusCode + ")");
        } else if (statusCode == 404) {
            LOG.error("Tines webhook not found: {}", webhookUrl);
            return TinesWebhookResponse.error("Webhook not found (HTTP 404)");
        } else if (statusCode >= 500) {
            LOG.error("Tines server error: {}", statusCode);
            return TinesWebhookResponse.error("Tines server error (HTTP " + statusCode + ")");
        } else {
            LOG.error("Unexpected response from Tines: {}", statusCode);
            return TinesWebhookResponse.error("Unexpected response (HTTP " + statusCode + ")");
        }
    }

    /**
     * Masks the URL for logging
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        int pathStart = url.indexOf("/", url.indexOf("//") + 2);
        if (pathStart > 0) {
            return url.substring(0, Math.min(pathStart + 15, url.length())) + "...";
        }
        return url;
    }

    /**
     * Closes the HTTP client
     */
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing HTTP client: {}", e.getMessage());
        }
    }
}
