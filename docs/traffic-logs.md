# Traffic Logs

This backend exposes recent HTTP traffic through public REST endpoints under `/api/v1/traffic`.

The feature is intended for training and debugging:
- it stores request and response metadata in the database
- it lets clients search and page through recent entries
- it keeps only recent data according to retention settings

## Endpoints

`GET /api/v1/traffic/info`

Returns websocket metadata for real-time traffic events.

Example response:

```json
{
  "webSocketEndpoint": "/api/v1/ws-traffic",
  "topic": "/topic/traffic",
  "description": "Connect to the WebSocket endpoint and subscribe to the topic to receive real-time HTTP traffic events"
}
```

`GET /api/v1/traffic/logs`

Returns paged persisted traffic entries.

Supported query parameters:
- `page`: zero-based page number
- `size`: requested page size, clamped to configured max
- `clientSessionId`: exact match against stored client session id
- `method`: exact match, case-insensitive
- `status`: exact numeric HTTP status
- `pathContains`: case-insensitive path fragment match
- `text`: case-insensitive search across `path`, `clientSessionId`, `queryString`, `requestBody`, `responseBody`
- `from`: inclusive ISO-8601 instant, for example `2026-04-08T16:00:00Z`
- `to`: inclusive ISO-8601 instant

Sorting:
- newest first by `timestamp`

Example request:

```text
GET /api/v1/traffic/logs?page=0&size=20&clientSessionId=browser-tab-7&pathContains=/api/v1/users&status=200
```

Example response:

```json
{
  "content": [
    {
      "correlationId": "375e14c3-36fb-434a-be52-5ad07c8268c2",
      "timestamp": "2026-04-08T16:06:25.686600Z",
      "clientSessionId": "browser-tab-7",
      "method": "POST",
      "path": "/api/v1/users/signin",
      "queryString": null,
      "status": 200,
      "durationMs": 419,
      "requestHeaders": "{\n  \"Host\" : [ \"localhost:4001\" ],\n  \"Content-Type\" : [ \"application/json\" ]\n}",
      "requestBody": "{\n  \"username\" : \"student1\",\n  \"password\" : \"***\"\n}",
      "responseHeaders": "{\n  \"Cache-Control\" : [ \"no-cache, no-store, max-age=0, must-revalidate\" ]\n}",
      "responseBody": "{\n  \"token\" : \"***\",\n  \"refreshToken\" : \"***\",\n  \"username\" : \"student1\",\n  \"email\" : \"student1@example.com\"\n}"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`GET /api/v1/traffic/logs/{correlationId}`

Returns a single stored entry by its traffic correlation id.

Example request:

```text
GET /api/v1/traffic/logs/375e14c3-36fb-434a-be52-5ad07c8268c2
```

Example response:

```json
{
  "correlationId": "375e14c3-36fb-434a-be52-5ad07c8268c2",
  "timestamp": "2026-04-08T16:06:25.686600Z",
  "clientSessionId": "browser-tab-7",
  "method": "POST",
  "path": "/api/v1/users/signin",
  "queryString": null,
  "status": 200,
  "durationMs": 419,
  "requestHeaders": "{\n  \"Host\" : [ \"localhost:4001\" ],\n  \"Content-Type\" : [ \"application/json\" ]\n}",
  "requestBody": "{\n  \"username\" : \"student1\",\n  \"password\" : \"***\"\n}",
  "responseHeaders": "{\n  \"Cache-Control\" : [ \"no-cache, no-store, max-age=0, must-revalidate\" ]\n}",
  "responseBody": "{\n  \"token\" : \"***\",\n  \"refreshToken\" : \"***\",\n  \"username\" : \"student1\",\n  \"email\" : \"student1@example.com\"\n}"
}
```

## What Gets Stored

Each traffic entry represents one HTTP request/response exchange.

Stored fields:
- `correlationId`: random identifier for this single request/response pair
- `clientSessionId`: optional client-provided session identifier
- `timestamp`: capture time
- `method`
- `path`
- `queryString`
- `status`
- `durationMs`
- `requestHeaders`
- `requestBody`
- `responseHeaders`
- `responseBody`

Important:
- `correlationId` is not a user session id
- `correlationId` is generated per request
- multiple requests from the same browser or user will have different `correlationId` values
- `clientSessionId` is the field intended to group multiple requests from the same client flow

## How `clientSessionId` Works

The backend reads `clientSessionId` in this order:
1. `X-Client-Session-Id` request header
2. `clientSessionId` cookie

If neither is present, the field is stored as `null`.

Example request:

```http
GET /api/v1/users/me HTTP/1.1
Host: localhost:4001
Authorization: Bearer eyJ...
X-Client-Session-Id: browser-tab-7
Accept: application/json
```

Then you can search related traffic with:

```text
GET /api/v1/traffic/logs?clientSessionId=browser-tab-7
```

## Sanitization

Traffic logs are sanitized before storage.

Always sanitized:
- JSON fields named `password`
- JSON fields named `token`
- JSON fields named `refreshToken`
- JSON fields named `accessToken`

Profile/property controlled:
- `Authorization` header masking
- email masking

Runtime logging via Logbook also obfuscates `Authorization` headers.

## Media Type Behavior

The traffic logger stores full text bodies for normal text and JSON traffic.

For streaming or binary-style responses, it stores a placeholder instead of the body to avoid buffering or corrupting the response flow.

Currently omitted response bodies include:
- `text/event-stream`
- `image/*`
- `application/octet-stream`

Example stored value:

```json
{
  "responseBody": "[omitted for media type text/event-stream]"
}
```

## Pagination And Limits

The API clamps requested page size to a configured max:

```yaml
app:
  traffic:
    max-page-size: 100
```

If a caller asks for more than the max, the response uses the configured limit.

## Retention

Traffic logs are not kept forever.

Relevant settings:

```yaml
app:
  traffic:
    retention: P1D
    cleanup-interval: PT24H
```

Meaning:
- keep entries for one day
- run cleanup every day

## Example Flows

Signup:

```text
POST /api/v1/users/signup
```

Possible stored response:

```json
{
  "status": 201,
  "requestBody": "{\n  \"username\" : \"student1\",\n  \"email\" : \"student1@example.com\",\n  \"password\" : \"***\"\n}",
  "responseBody": ""
}
```

Signin:

```text
POST /api/v1/users/signin
```

Possible stored response:

```json
{
  "status": 200,
  "requestBody": "{\n  \"username\" : \"student1\",\n  \"password\" : \"***\"\n}",
  "responseBody": "{\n  \"token\" : \"***\",\n  \"refreshToken\" : \"***\"\n}"
}
```

Authenticated request:

```text
GET /api/v1/users/me
```

Possible stored response:

```json
{
  "clientSessionId": "browser-tab-7",
  "status": 200,
  "responseBody": "{\n  \"username\" : \"student1\",\n  \"email\" : \"student1@example.com\"\n}"
}
```

## Notes

- The `/api/v1/traffic/logs` endpoints are excluded from persistence to avoid recursive noise.
- Websocket traffic events and persisted traffic logs are related features, but not the same thing.
- The traffic log API is public in this training app, so avoid enabling it in environments where that is not acceptable.
