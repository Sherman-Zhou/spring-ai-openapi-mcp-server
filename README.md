# Spring AI OpenAPI MCP Server

A Spring AI MCP server that dynamically exposes OpenAPI-defined APIs as callable tools, supporting multiple OpenAPI specifications and full parameter mapping (path, query, body, and header parameters).

## Features
- **Multiple OpenAPI Spec Support**: Load and expose multiple OpenAPI specs as callable tools.
- **Dynamic Tool Generation**: Each OpenAPI operation becomes a tool with a generated input schema.
- **Full Parameter Mapping**: Supports path, query, body, and header parameters (header support added recently).
- **Reactive HTTP Calls**: Uses Spring WebClient for non-blocking API calls.

## Getting Started

### Prerequisites
- Java 17 or later
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run
```bash
mvn spring-boot:run
```

### MCP Endpoint
```
http://localhost:8080/sse
```


### Configuration Example
Edit `src/main/resources/application.yml`:
```yaml
swagger:
  specs:
    example:
      url: classpath:petStore.yaml
      serverUrl: http://localhost:8080
```

### API Usage Example
Suppose your OpenAPI spec defines an operation with path, query, and header parameters:
```yaml
paths:
  /greet:
    get:
      operationId: greetUser
      parameters:
        - name: name
          in: query
          required: true
          schema:
            type: string
        - name: X-Request-Id
          in: header
          required: false
          schema:
            type: string
```
The generated tool will accept both `name` and `X-Request-Id` as input. When called, the server will send `X-Request-Id` as an HTTP header.

### Contribution
Contributions are welcome! Please fork the repo and submit pull requests. For major changes, open an issue first to discuss your ideas.

### License
[MIT](LICENSE)

