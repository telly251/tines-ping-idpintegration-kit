/*
 * Tines Webhook Response
 *
 * Represents the response from a Tines webhook call.
 */
package com.tines.pingfederate.adapter;

import java.util.Collections;
import java.util.Map;

/**
 * Encapsulates the response from a Tines webhook call.
 * Contains success/failure status, message, and response data.
 */
public class TinesWebhookResponse {

    private final boolean success;
    private final String message;
    private final Map<String, Object> data;

    /**
     * Private constructor - use factory methods
     */
    private TinesWebhookResponse(boolean success, String message, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data != null ? data : Collections.emptyMap();
    }

    /**
     * Creates a successful response with data
     *
     * @param data The response data from Tines
     * @return A success response
     */
    public static TinesWebhookResponse success(Map<String, Object> data) {
        String message = "Success";
        if (data != null && data.containsKey("message")) {
            message = data.get("message").toString();
        }
        return new TinesWebhookResponse(true, message, data);
    }

    /**
     * Creates a successful response with data and custom message
     *
     * @param data The response data from Tines
     * @param message Custom message
     * @return A success response
     */
    public static TinesWebhookResponse success(Map<String, Object> data, String message) {
        return new TinesWebhookResponse(true, message, data);
    }

    /**
     * Creates an error response
     *
     * @param message The error message
     * @return An error response
     */
    public static TinesWebhookResponse error(String message) {
        return new TinesWebhookResponse(false, message, Collections.emptyMap());
    }

    /**
     * Creates an error response with data
     *
     * @param message The error message
     * @param data Any additional error data
     * @return An error response
     */
    public static TinesWebhookResponse error(String message, Map<String, Object> data) {
        return new TinesWebhookResponse(false, message, data);
    }

    /**
     * Returns true if the webhook call was successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns true if the webhook call failed
     */
    public boolean isError() {
        return !success;
    }

    /**
     * Returns the response message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the response data
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Gets a specific value from the response data
     *
     * @param key The key to look up
     * @return The value, or null if not found
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Gets a string value from the response data
     *
     * @param key The key to look up
     * @return The string value, or null if not found
     */
    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a string value with a default
     *
     * @param key The key to look up
     * @param defaultValue Default value if not found
     * @return The string value, or the default
     */
    public String getString(String key, String defaultValue) {
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Checks if the response data contains a key
     *
     * @param key The key to check
     * @return true if the key exists
     */
    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    @Override
    public String toString() {
        return "TinesWebhookResponse{" +
            "success=" + success +
            ", message='" + message + '\'' +
            ", dataKeys=" + data.keySet() +
            '}';
    }
}
