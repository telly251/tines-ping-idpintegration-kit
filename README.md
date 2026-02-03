# Tines IDP Adapter for PingFederate

A PingFederate Identity Provider (IDP) adapter that integrates with the Tines orchestration platform. This adapter allows you to call Tines webhooks during authentication flows, enabling powerful orchestration, attribute enrichment, and custom authentication logic through Tines.

## Features

- **Webhook Integration**: Call Tines webhooks with full authentication context
- **Attribute Enrichment**: Receive enriched user attributes from Tines orchestrations
- **Flexible Configuration**: Configure webhook URL, timeout, and which data to include
- **Chained Adapter Support**: Forward attributes from previously executed adapters
- **Error Handling**: Configurable behavior for webhook failures
- **Dynamic Attributes**: Support for extended attribute contracts

## Prerequisites

- PingFederate 11.x or later
- Java 11 or later
- Maven 3.6 or later
- A Tines tenant with webhook capabilities

## Building the Adapter

### Option 1: Using Maven (Recommended)

1. First, install the PingFederate SDK JAR to your local Maven repository:

```bash
# Copy pf-protocolengine.jar from your PingFederate installation
# Located at: <PF_INSTALL>/pingfederate/server/default/lib/pf-protocolengine.jar

mvn install:install-file \
  -Dfile=/path/to/pf-protocolengine.jar \
  -DgroupId=com.pingidentity.pingfederate \
  -DartifactId=pf-protocolengine \
  -Dversion=11.3.0 \
  -Dpackaging=jar
```

2. Build the adapter:

```bash
mvn clean package
```

3. The built JAR will be at: `target/tines-idp-adapter-1.0.0.jar`

### Option 2: Using the provided build script

```bash
./build.sh
```

## Installation

1. Copy the built JAR file to your PingFederate server:

```bash
cp target/tines-idp-adapter-1.0.0.jar <PF_INSTALL>/pingfederate/server/default/deploy/
```

2. Restart PingFederate:

```bash
<PF_INSTALL>/pingfederate/bin/run.sh restart
```

## Configuration

### Creating an Adapter Instance

1. Log into the PingFederate Admin Console
2. Navigate to **Authentication** → **Integration** → **IdP Adapters**
3. Click **Create New Instance**
4. Select **Tines IDP Adapter** from the Type dropdown
5. Configure the following settings:

| Setting | Description | Required |
|---------|-------------|----------|
| **Webhook URL** | The full Tines webhook URL (e.g., `https://your-tenant.tines.com/webhook/xxxxx`) | Yes |
| **Secret Token** | Optional secret token for webhook authentication (sent as `X-Tines-Secret` header) | No |
| **Timeout** | HTTP request timeout in seconds (default: 30) | No |
| **Username Attribute** | The attribute name in Tines response containing the username (default: `username`) | No |
| **Dynamic Attributes** | Comma-separated list of additional attributes to extract from Tines response | No |
| **Include Chained Attributes** | Forward attributes from previous adapters to Tines | No |
| **Include Request Parameters** | Include HTTP request parameters in webhook payload | No |
| **Include Request Headers** | Include HTTP request headers in webhook payload | No |
| **Fail on Webhook Error** | Fail authentication if webhook call fails | No |

### Extended Attribute Contract

You can add additional attributes to the adapter's contract beyond the defaults:
- `username` - The authenticated user's username
- `subject` - The subject identifier (same as username by default)
- `email` - User's email address
- `tines_status` - Status from the Tines response
- `tines_message` - Message from the Tines response

## Tines Webhook Setup

### Incoming Webhook Payload

The adapter sends a JSON payload to your Tines webhook with the following structure:

```json
{
  "pingfederate": {
    "partnerEntityId": "sp-entity-id",
    "applicationName": "Application Name",
    "targetResource": "https://target.url/",
    "userId": "existing-user-id"
  },
  "chainedAttributes": {
    "username": "john.doe",
    "email": "john.doe@example.com"
  },
  "request": {
    "remoteAddr": "192.168.1.100",
    "remoteHost": "client.example.com",
    "serverName": "pingfederate.example.com",
    "requestURI": "/idp/SSO.saml2",
    "method": "POST",
    "userAgent": "Mozilla/5.0..."
  },
  "parameters": {
    "param1": "value1"
  },
  "timestamp": 1699876543210
}
```

### Expected Response Format

Your Tines workflow should return a JSON response with user attributes:

```json
{
  "status": "success",
  "message": "User authenticated successfully",
  "username": "john.doe",
  "email": "john.doe@example.com",
  "department": "Engineering",
  "role": "admin",
  "groups": ["developers", "admins"]
}
```

#### Response Fields

| Field | Description |
|-------|-------------|
| `status` | Must be `success` for successful authentication. Values like `error`, `failure`, or `denied` will fail authentication. |
| `message` | Optional message (shown in logs) |
| `username` | The authenticated user's username (or configure a different attribute name) |
| Any other fields | Available as extended attributes in PingFederate |

### Error Response

To deny authentication, return:

```json
{
  "status": "denied",
  "message": "User not authorized",
  "error": true
}
```

## Example Tines Story

Here's a simple example Tines story that processes the webhook:

1. **Webhook Action**: Receives the PingFederate request
2. **HTTP Request**: Look up user in your identity provider or database
3. **Event Transform**: Prepare the response with user attributes
4. **Return Response**: Send the enriched attributes back to PingFederate

### Sample Tines Actions

**1. Webhook Trigger (incoming)**
- Receives the authentication context from PingFederate

**2. Enrichment Logic**
- Query Active Directory, database, or other systems
- Apply business rules
- Perform risk assessment

**3. Response Action**
```json
{
  "status": "success",
  "username": "{{.username}}",
  "email": "{{.email}}",
  "department": "{{.lookup_result.department}}",
  "risk_score": "{{.risk_assessment.score}}"
}
```

## Deployment Scenarios

### Scenario 1: Attribute Enrichment

Use this adapter to enrich user attributes after initial authentication:

1. Chain this adapter after your primary authentication adapter (e.g., HTML Form Adapter)
2. Enable "Include Chained Attributes"
3. In Tines, look up additional attributes from external systems
4. Return the enriched attribute set

### Scenario 2: Risk-Based Authentication

Implement adaptive authentication based on risk:

1. Call Tines with request context (IP, user agent, etc.)
2. Tines performs risk assessment using external threat intel
3. Return risk score as an attribute
4. Use PingFederate policy to step up authentication if needed

### Scenario 3: Custom Authentication Logic

Implement completely custom authentication:

1. Use this adapter as the primary authentication method
2. Tines handles all authentication logic
3. Return user identity and attributes from Tines

## Troubleshooting

### Logs

Enable debug logging for the adapter by adding to `<PF_INSTALL>/pingfederate/server/default/conf/log4j2.xml`:

```xml
<Logger name="com.tines.pingfederate" level="DEBUG" additivity="false">
    <AppenderRef ref="FILE"/>
</Logger>
```

### Common Issues

**1. Adapter not appearing in dropdown**
- Ensure the JAR is in the `deploy/` directory
- Check that PingFederate was restarted
- Look for class loading errors in `server.log`

**2. Webhook connection failures**
- Verify the webhook URL is accessible from PingFederate
- Check firewall rules and proxy settings
- Ensure SSL certificates are valid

**3. Authentication failures**
- Check the Tines response format matches expected structure
- Verify the `status` field is set to `success`
- Review PingFederate logs for detailed error messages

## Security Considerations

- Always use HTTPS for webhook URLs
- Configure a secret token and validate it in your Tines webhook
- Be cautious about which request headers you forward
- Sensitive headers (Cookie, Authorization) are automatically filtered

## License

This project is provided as-is for integration purposes.

## Support

For issues with:
- **This adapter**: Open an issue in this repository
- **PingFederate**: Contact Ping Identity support
- **Tines**: Contact Tines support
