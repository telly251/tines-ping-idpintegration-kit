/*
 * Tines IDP Adapter for PingFederate
 *
 * This adapter allows PingFederate to call a Tines webhook during authentication
 * flows, enabling orchestration and enrichment of user attributes through Tines.
 */
package com.tines.pingfederate.adapter;

import com.pingidentity.sdk.AuthnAdapterResponse;
import com.pingidentity.sdk.AuthnAdapterResponse.AUTHN_STATUS;
import com.pingidentity.sdk.IdpAuthenticationAdapterV2;

import org.sourceid.saml20.adapter.AuthnAdapterException;
import org.sourceid.saml20.adapter.attribute.AttributeValue;
import org.sourceid.saml20.adapter.conf.Configuration;
import org.sourceid.saml20.adapter.conf.Field;
import org.sourceid.saml20.adapter.gui.AdapterConfigurationGuiDescriptor;
import org.sourceid.saml20.adapter.gui.CheckBoxFieldDescriptor;
import org.sourceid.saml20.adapter.gui.TextFieldDescriptor;
import org.sourceid.saml20.adapter.gui.validation.impl.RequiredFieldValidator;
import org.sourceid.saml20.adapter.gui.validation.impl.IntegerValidator;
import org.sourceid.saml20.adapter.idp.authn.AuthnPolicy;
import org.sourceid.saml20.adapter.idp.authn.IdpAuthnAdapterDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * TinesIdpAdapter is a PingFederate IDP adapter that integrates with Tines
 * orchestration platform. It calls a configured Tines webhook URL with the
 * authentication context and receives enriched attributes back.
 *
 * Configuration options:
 * - Webhook URL: The Tines webhook endpoint to call
 * - Timeout: HTTP request timeout in seconds
 * - Include Chained Attributes: Whether to forward attributes from previous adapters
 * - Include Request Parameters: Whether to include HTTP request parameters
 * - Fail on Error: Whether to fail authentication if the webhook call fails
 */
public class TinesIdpAdapter implements IdpAuthenticationAdapterV2 {

    private static final Logger LOG = LoggerFactory.getLogger(TinesIdpAdapter.class);

    // Adapter metadata
    private static final String ADAPTER_TYPE = "Tines IDP Adapter";
    private static final String ADAPTER_VERSION = "1.0.0";

    // Configuration field names
    public static final String CONFIG_WEBHOOK_URL = "Webhook URL";
    public static final String CONFIG_TIMEOUT = "Timeout (seconds)";
    public static final String CONFIG_INCLUDE_CHAINED_ATTRS = "Include Chained Attributes";
    public static final String CONFIG_INCLUDE_REQUEST_PARAMS = "Include Request Parameters";
    public static final String CONFIG_INCLUDE_REQUEST_HEADERS = "Include Request Headers";
    public static final String CONFIG_FAIL_ON_ERROR = "Fail on Webhook Error";
    public static final String CONFIG_SECRET_TOKEN = "Secret Token (Optional)";
    public static final String CONFIG_USERNAME_ATTRIBUTE = "Username Attribute Name";
    public static final String CONFIG_DYNAMIC_ATTRIBUTES = "Dynamic Attribute Names (comma-separated)";

    // Default attribute contract - these are always available
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_SUBJECT = "subject";
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_TINES_STATUS = "tines_status";
    public static final String ATTR_TINES_MESSAGE = "tines_message";

    // The adapter descriptor
    private IdpAuthnAdapterDescriptor descriptor;

    // Configuration values
    private String webhookUrl;
    private int timeoutSeconds;
    private boolean includeChainedAttributes;
    private boolean includeRequestParameters;
    private boolean includeRequestHeaders;
    private boolean failOnError;
    private String secretToken;
    private String usernameAttributeName;
    private Set<String> dynamicAttributeNames;

    // HTTP client for webhook calls
    private TinesWebhookClient webhookClient;

    /**
     * Constructor - initializes the adapter descriptor with configuration fields
     */
    public TinesIdpAdapter() {
        // Create the configuration GUI descriptor
        AdapterConfigurationGuiDescriptor guiDescriptor = new AdapterConfigurationGuiDescriptor(
            "Configure the Tines webhook integration settings."
        );

        // Webhook URL field (required)
        TextFieldDescriptor webhookUrlField = new TextFieldDescriptor(
            CONFIG_WEBHOOK_URL,
            "The full URL of the Tines webhook to call (e.g., https://your-tenant.tines.com/webhook/xxxxx)"
        );
        webhookUrlField.addValidator(new RequiredFieldValidator());
        webhookUrlField.setDefaultValue("");
        guiDescriptor.addField(webhookUrlField);

        // Secret token field (optional)
        TextFieldDescriptor secretTokenField = new TextFieldDescriptor(
            CONFIG_SECRET_TOKEN,
            "Optional secret token to include in the X-Tines-Secret header for webhook authentication"
        );
        secretTokenField.setDefaultValue("");
        guiDescriptor.addField(secretTokenField);

        // Timeout field
        TextFieldDescriptor timeoutField = new TextFieldDescriptor(
            CONFIG_TIMEOUT,
            "HTTP request timeout in seconds (default: 30)"
        );
        timeoutField.addValidator(new IntegerValidator(1, 300));
        timeoutField.setDefaultValue("30");
        guiDescriptor.addField(timeoutField);

        // Username attribute name field
        TextFieldDescriptor usernameAttrField = new TextFieldDescriptor(
            CONFIG_USERNAME_ATTRIBUTE,
            "The attribute name in Tines response that contains the username (default: username)"
        );
        usernameAttrField.setDefaultValue("username");
        guiDescriptor.addField(usernameAttrField);

        // Dynamic attributes field
        TextFieldDescriptor dynamicAttrsField = new TextFieldDescriptor(
            CONFIG_DYNAMIC_ATTRIBUTES,
            "Additional attribute names to extract from Tines response (comma-separated, e.g., department,role,group)"
        );
        dynamicAttrsField.setDefaultValue("");
        guiDescriptor.addField(dynamicAttrsField);

        // Include chained attributes checkbox
        CheckBoxFieldDescriptor includeChainedField = new CheckBoxFieldDescriptor(
            CONFIG_INCLUDE_CHAINED_ATTRS,
            "Include attributes from previously chained adapters in the webhook payload"
        );
        includeChainedField.setDefaultValue(true);
        guiDescriptor.addField(includeChainedField);

        // Include request parameters checkbox
        CheckBoxFieldDescriptor includeParamsField = new CheckBoxFieldDescriptor(
            CONFIG_INCLUDE_REQUEST_PARAMS,
            "Include HTTP request parameters in the webhook payload"
        );
        includeParamsField.setDefaultValue(true);
        guiDescriptor.addField(includeParamsField);

        // Include request headers checkbox
        CheckBoxFieldDescriptor includeHeadersField = new CheckBoxFieldDescriptor(
            CONFIG_INCLUDE_REQUEST_HEADERS,
            "Include HTTP request headers in the webhook payload"
        );
        includeHeadersField.setDefaultValue(false);
        guiDescriptor.addField(includeHeadersField);

        // Fail on error checkbox
        CheckBoxFieldDescriptor failOnErrorField = new CheckBoxFieldDescriptor(
            CONFIG_FAIL_ON_ERROR,
            "Fail authentication if the Tines webhook call fails or returns an error"
        );
        failOnErrorField.setDefaultValue(true);
        guiDescriptor.addField(failOnErrorField);

        // Define the default attribute contract
        Set<String> attributeContract = new HashSet<>();
        attributeContract.add(ATTR_USERNAME);
        attributeContract.add(ATTR_SUBJECT);
        attributeContract.add(ATTR_EMAIL);
        attributeContract.add(ATTR_TINES_STATUS);
        attributeContract.add(ATTR_TINES_MESSAGE);

        // Create the adapter descriptor
        this.descriptor = new IdpAuthnAdapterDescriptor(
            this,
            ADAPTER_TYPE,
            attributeContract,
            true,  // supportsExtendedContract - allows additional attributes
            guiDescriptor,
            false, // authnCtxClassRefSupported
            ADAPTER_VERSION
        );
    }

    /**
     * Returns the adapter descriptor containing metadata and configuration
     */
    @Override
    public IdpAuthnAdapterDescriptor getAdapterDescriptor() {
        return this.descriptor;
    }

    /**
     * Called when the adapter configuration is saved. Processes and validates
     * the administrator-entered configuration values.
     */
    @Override
    public void configure(Configuration configuration) {
        // Get configuration values
        this.webhookUrl = configuration.getFieldValue(CONFIG_WEBHOOK_URL);

        String timeoutStr = configuration.getFieldValue(CONFIG_TIMEOUT);
        this.timeoutSeconds = (timeoutStr != null && !timeoutStr.isEmpty())
            ? Integer.parseInt(timeoutStr) : 30;

        this.secretToken = configuration.getFieldValue(CONFIG_SECRET_TOKEN);
        this.usernameAttributeName = configuration.getFieldValue(CONFIG_USERNAME_ATTRIBUTE);
        if (this.usernameAttributeName == null || this.usernameAttributeName.isEmpty()) {
            this.usernameAttributeName = "username";
        }

        // Parse dynamic attribute names
        this.dynamicAttributeNames = new HashSet<>();
        String dynamicAttrs = configuration.getFieldValue(CONFIG_DYNAMIC_ATTRIBUTES);
        if (dynamicAttrs != null && !dynamicAttrs.isEmpty()) {
            for (String attr : dynamicAttrs.split(",")) {
                String trimmed = attr.trim();
                if (!trimmed.isEmpty()) {
                    this.dynamicAttributeNames.add(trimmed);
                }
            }
        }

        // Get checkbox values
        Field includeChainedField = configuration.getField(CONFIG_INCLUDE_CHAINED_ATTRS);
        this.includeChainedAttributes = includeChainedField != null
            && Boolean.parseBoolean(includeChainedField.getValue());

        Field includeParamsField = configuration.getField(CONFIG_INCLUDE_REQUEST_PARAMS);
        this.includeRequestParameters = includeParamsField != null
            && Boolean.parseBoolean(includeParamsField.getValue());

        Field includeHeadersField = configuration.getField(CONFIG_INCLUDE_REQUEST_HEADERS);
        this.includeRequestHeaders = includeHeadersField != null
            && Boolean.parseBoolean(includeHeadersField.getValue());

        Field failOnErrorField = configuration.getField(CONFIG_FAIL_ON_ERROR);
        this.failOnError = failOnErrorField == null
            || Boolean.parseBoolean(failOnErrorField.getValue());

        // Initialize the webhook client
        this.webhookClient = new TinesWebhookClient(this.webhookUrl, this.timeoutSeconds, this.secretToken);

        LOG.info("Tines IDP Adapter configured with webhook URL: {}",
            maskUrl(this.webhookUrl));
    }

    /**
     * Main authentication method. Called by PingFederate during SSO processing.
     * This method calls the Tines webhook and returns the result.
     *
     * @param request The HTTP request
     * @param response The HTTP response
     * @param inParameters Map containing input parameters from PingFederate
     * @return AuthnAdapterResponse with the authentication result
     */
    @Override
    @SuppressWarnings("unchecked")
    public AuthnAdapterResponse lookupAuthN(
            HttpServletRequest request,
            HttpServletResponse response,
            Map<String, Object> inParameters) throws AuthnAdapterException, IOException {

        LOG.debug("TinesIdpAdapter.lookupAuthN() called");

        AuthnAdapterResponse adapterResponse = new AuthnAdapterResponse();

        try {
            // Build the payload to send to Tines
            Map<String, Object> payload = buildWebhookPayload(request, inParameters);

            LOG.debug("Calling Tines webhook with payload");

            // Call the Tines webhook
            TinesWebhookResponse tinesResponse = webhookClient.callWebhook(payload);

            if (tinesResponse.isSuccess()) {
                // Build the attribute map from the Tines response
                Map<String, Object> attributeMap = buildAttributeMap(tinesResponse, inParameters);

                adapterResponse.setAttributeMap(attributeMap);
                adapterResponse.setAuthnStatus(AUTHN_STATUS.SUCCESS);

                LOG.info("Tines webhook call successful, user authenticated: {}",
                    attributeMap.get(ATTR_USERNAME));
            } else {
                // Webhook call failed
                LOG.warn("Tines webhook returned error: {}", tinesResponse.getMessage());

                if (failOnError) {
                    adapterResponse.setAuthnStatus(AUTHN_STATUS.FAILURE);
                    adapterResponse.setErrorMessage("Tines authentication failed: " + tinesResponse.getMessage());
                } else {
                    // Continue with default/chained attributes
                    Map<String, Object> attributeMap = buildFallbackAttributeMap(inParameters, tinesResponse);
                    adapterResponse.setAttributeMap(attributeMap);
                    adapterResponse.setAuthnStatus(AUTHN_STATUS.SUCCESS);
                }
            }

        } catch (Exception e) {
            LOG.error("Error calling Tines webhook", e);

            if (failOnError) {
                adapterResponse.setAuthnStatus(AUTHN_STATUS.FAILURE);
                adapterResponse.setErrorMessage("Tines webhook error: " + e.getMessage());
            } else {
                // Continue with fallback
                Map<String, Object> attributeMap = buildFallbackAttributeMap(inParameters, null);
                adapterResponse.setAttributeMap(attributeMap);
                adapterResponse.setAuthnStatus(AUTHN_STATUS.SUCCESS);
            }
        }

        return adapterResponse;
    }

    /**
     * Builds the payload to send to the Tines webhook
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildWebhookPayload(
            HttpServletRequest request,
            Map<String, Object> inParameters) {

        Map<String, Object> payload = new HashMap<>();

        // Add PingFederate context
        Map<String, Object> pfContext = new HashMap<>();

        // Add standard PingFederate parameters
        if (inParameters.containsKey(IN_PARAMETER_NAME_PARTNER_ENTITYID)) {
            pfContext.put("partnerEntityId", inParameters.get(IN_PARAMETER_NAME_PARTNER_ENTITYID));
        }
        if (inParameters.containsKey(IN_PARAMETER_NAME_PARTNER_ADAPTER_ID)) {
            pfContext.put("partnerAdapterId", inParameters.get(IN_PARAMETER_NAME_PARTNER_ADAPTER_ID));
        }
        if (inParameters.containsKey(IN_PARAMETER_NAME_USERID)) {
            pfContext.put("userId", inParameters.get(IN_PARAMETER_NAME_USERID));
        }
        if (inParameters.containsKey(IN_PARAMETER_NAME_RESUME_PATH)) {
            pfContext.put("resumePath", inParameters.get(IN_PARAMETER_NAME_RESUME_PATH));
        }
        if (inParameters.containsKey(IN_PARAMETER_NAME_APPLICATION_NAME)) {
            pfContext.put("applicationName", inParameters.get(IN_PARAMETER_NAME_APPLICATION_NAME));
        }
        if (inParameters.containsKey(IN_PARAMETER_NAME_TARGET_RESOURCE)) {
            pfContext.put("targetResource", inParameters.get(IN_PARAMETER_NAME_TARGET_RESOURCE));
        }

        payload.put("pingfederate", pfContext);

        // Add chained attributes from previous adapters
        if (includeChainedAttributes && inParameters.containsKey(IN_PARAMETER_NAME_CHAINED_ATTRIBUTES)) {
            Map<String, AttributeValue> chainedAttrs =
                (Map<String, AttributeValue>) inParameters.get(IN_PARAMETER_NAME_CHAINED_ATTRIBUTES);

            if (chainedAttrs != null && !chainedAttrs.isEmpty()) {
                Map<String, Object> chainedMap = new HashMap<>();
                for (Map.Entry<String, AttributeValue> entry : chainedAttrs.entrySet()) {
                    AttributeValue attrValue = entry.getValue();
                    if (attrValue != null) {
                        chainedMap.put(entry.getKey(), attrValue.getValue());
                    }
                }
                payload.put("chainedAttributes", chainedMap);
            }
        }

        // Add HTTP request information
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("remoteAddr", request.getRemoteAddr());
        requestInfo.put("remoteHost", request.getRemoteHost());
        requestInfo.put("serverName", request.getServerName());
        requestInfo.put("requestURI", request.getRequestURI());
        requestInfo.put("method", request.getMethod());
        requestInfo.put("protocol", request.getProtocol());
        requestInfo.put("scheme", request.getScheme());
        requestInfo.put("isSecure", request.isSecure());

        // Add user agent
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            requestInfo.put("userAgent", userAgent);
        }

        payload.put("request", requestInfo);

        // Add request parameters if configured
        if (includeRequestParameters) {
            Map<String, String[]> params = request.getParameterMap();
            if (params != null && !params.isEmpty()) {
                Map<String, Object> paramMap = new HashMap<>();
                for (Map.Entry<String, String[]> entry : params.entrySet()) {
                    String[] values = entry.getValue();
                    if (values != null && values.length == 1) {
                        paramMap.put(entry.getKey(), values[0]);
                    } else if (values != null) {
                        paramMap.put(entry.getKey(), values);
                    }
                }
                payload.put("parameters", paramMap);
            }
        }

        // Add request headers if configured
        if (includeRequestHeaders) {
            Map<String, String> headers = new HashMap<>();
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                // Skip sensitive headers
                if (!isSensitiveHeader(name)) {
                    headers.put(name, request.getHeader(name));
                }
            }
            payload.put("headers", headers);
        }

        // Add timestamp
        payload.put("timestamp", System.currentTimeMillis());

        return payload;
    }

    /**
     * Checks if a header name is sensitive and should not be forwarded
     */
    private boolean isSensitiveHeader(String headerName) {
        String lower = headerName.toLowerCase();
        return lower.equals("cookie")
            || lower.equals("authorization")
            || lower.equals("x-forwarded-for")
            || lower.contains("password")
            || lower.contains("secret")
            || lower.contains("token");
    }

    /**
     * Builds the attribute map from the Tines response
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildAttributeMap(
            TinesWebhookResponse tinesResponse,
            Map<String, Object> inParameters) {

        Map<String, Object> attributeMap = new HashMap<>();
        Map<String, Object> responseData = tinesResponse.getData();

        // Extract username from the configured attribute name
        String username = extractStringValue(responseData, usernameAttributeName);
        if (username == null || username.isEmpty()) {
            // Fallback to common attribute names
            username = extractStringValue(responseData, "user");
            if (username == null) {
                username = extractStringValue(responseData, "userId");
            }
            if (username == null) {
                username = extractStringValue(responseData, "subject");
            }
        }
        attributeMap.put(ATTR_USERNAME, username != null ? username : "unknown");
        attributeMap.put(ATTR_SUBJECT, username != null ? username : "unknown");

        // Extract email if present
        String email = extractStringValue(responseData, "email");
        if (email != null) {
            attributeMap.put(ATTR_EMAIL, email);
        }

        // Add status information
        attributeMap.put(ATTR_TINES_STATUS, "success");
        attributeMap.put(ATTR_TINES_MESSAGE, tinesResponse.getMessage() != null
            ? tinesResponse.getMessage() : "Authentication successful");

        // Extract dynamic attributes
        for (String attrName : dynamicAttributeNames) {
            Object value = responseData.get(attrName);
            if (value != null) {
                attributeMap.put(attrName, value.toString());
            }
        }

        // Add any additional attributes from the response that match extended contract
        for (Map.Entry<String, Object> entry : responseData.entrySet()) {
            String key = entry.getKey();
            if (!attributeMap.containsKey(key) && entry.getValue() != null) {
                // Only include string-convertible values
                attributeMap.put(key, entry.getValue().toString());
            }
        }

        return attributeMap;
    }

    /**
     * Builds a fallback attribute map when the webhook fails
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFallbackAttributeMap(
            Map<String, Object> inParameters,
            TinesWebhookResponse tinesResponse) {

        Map<String, Object> attributeMap = new HashMap<>();

        // Try to get username from chained attributes
        String username = null;
        if (inParameters.containsKey(IN_PARAMETER_NAME_CHAINED_ATTRIBUTES)) {
            Map<String, AttributeValue> chainedAttrs =
                (Map<String, AttributeValue>) inParameters.get(IN_PARAMETER_NAME_CHAINED_ATTRIBUTES);

            if (chainedAttrs != null) {
                AttributeValue usernameAttr = chainedAttrs.get("username");
                if (usernameAttr == null) {
                    usernameAttr = chainedAttrs.get("subject");
                }
                if (usernameAttr != null && usernameAttr.getValue() != null) {
                    username = usernameAttr.getValue().toString();
                }
            }
        }

        // Fall back to userId from inParameters
        if (username == null && inParameters.containsKey(IN_PARAMETER_NAME_USERID)) {
            Object userId = inParameters.get(IN_PARAMETER_NAME_USERID);
            if (userId != null) {
                username = userId.toString();
            }
        }

        attributeMap.put(ATTR_USERNAME, username != null ? username : "unknown");
        attributeMap.put(ATTR_SUBJECT, username != null ? username : "unknown");
        attributeMap.put(ATTR_TINES_STATUS, "fallback");
        attributeMap.put(ATTR_TINES_MESSAGE, tinesResponse != null
            ? tinesResponse.getMessage() : "Webhook call failed, using fallback");

        return attributeMap;
    }

    /**
     * Extracts a string value from a map, handling nested objects
     */
    private String extractStringValue(Map<String, Object> data, String key) {
        if (data == null || key == null) {
            return null;
        }
        Object value = data.get(key);
        if (value != null) {
            return value.toString();
        }
        return null;
    }

    /**
     * Masks the URL for logging purposes
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        // Mask everything after the domain
        int pathStart = url.indexOf("/", url.indexOf("//") + 2);
        if (pathStart > 0) {
            return url.substring(0, Math.min(pathStart + 10, url.length())) + "...";
        }
        return url;
    }

    /**
     * Called when a logout needs to be performed
     */
    @Override
    public boolean logoutAuthN(
            Map<String, Object> authnIdentifiers,
            HttpServletRequest request,
            HttpServletResponse response,
            String resumePath) throws AuthnAdapterException, IOException {

        // No special logout handling required
        LOG.debug("TinesIdpAdapter.logoutAuthN() called");
        return true;
    }

    /**
     * Returns the authentication source descriptor (deprecated in V2)
     */
    @Override
    public Map<String, Object> getAdapterInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("version", ADAPTER_VERSION);
        info.put("type", ADAPTER_TYPE);
        return info;
    }

    /**
     * Not used in V2 adapters
     */
    @Override
    public AuthnAdapterResponse lookupAuthN(
            HttpServletRequest request,
            HttpServletResponse response,
            String partnerSpEntityId,
            AuthnPolicy authnPolicy,
            String resumePath) throws AuthnAdapterException, IOException {
        // This method is deprecated - use the Map-based version
        throw new AuthnAdapterException("This method is deprecated. Use lookupAuthN with Map parameters.");
    }
}
