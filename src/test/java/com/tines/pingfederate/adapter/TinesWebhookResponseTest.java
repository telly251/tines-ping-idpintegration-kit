package com.tines.pingfederate.adapter;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for TinesWebhookResponse
 */
public class TinesWebhookResponseTest {

    @Test
    public void testSuccessResponse() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", "testuser");
        data.put("email", "test@example.com");

        TinesWebhookResponse response = TinesWebhookResponse.success(data);

        assertTrue(response.isSuccess());
        assertFalse(response.isError());
        assertEquals("testuser", response.getString("username"));
        assertEquals("test@example.com", response.getString("email"));
    }

    @Test
    public void testErrorResponse() {
        TinesWebhookResponse response = TinesWebhookResponse.error("Connection failed");

        assertFalse(response.isSuccess());
        assertTrue(response.isError());
        assertEquals("Connection failed", response.getMessage());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    public void testGetStringWithDefault() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", "testuser");

        TinesWebhookResponse response = TinesWebhookResponse.success(data);

        assertEquals("testuser", response.getString("username", "default"));
        assertEquals("default", response.getString("nonexistent", "default"));
    }

    @Test
    public void testHasKey() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", "testuser");

        TinesWebhookResponse response = TinesWebhookResponse.success(data);

        assertTrue(response.hasKey("username"));
        assertFalse(response.hasKey("nonexistent"));
    }

    @Test
    public void testSuccessWithMessage() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Custom success message");

        TinesWebhookResponse response = TinesWebhookResponse.success(data);

        assertEquals("Custom success message", response.getMessage());
    }
}
