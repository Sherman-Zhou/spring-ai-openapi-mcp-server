package com.github.sherman.mcp.tool.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenApiToolCallbackProvider implements ToolCallbackProvider {

    private final List<ToolCallback> toolCallbacks;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenApiSchemaConverter schemaConverter;
    private final SwaggerProperties swaggerProperties;

    public OpenApiToolCallbackProvider(OpenApiSchemaConverter schemaConverter, SwaggerProperties swaggerProperties) {
        this.schemaConverter = schemaConverter;
        this.swaggerProperties = swaggerProperties;
        this.webClient = WebClient.builder().build();
        this.toolCallbacks = loadToolCallbacksFromMultipleOpenApiSpecs();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return this.toolCallbacks.toArray(new ToolCallback[0]);
    }

    private List<ToolCallback> loadToolCallbacksFromMultipleOpenApiSpecs() {
        List<ToolCallback> callbacks = new ArrayList<>();
        Map<String, SwaggerProperties.SpecConfig> specs = swaggerProperties.getSpecs();
        if (specs == null || specs.isEmpty()) {
            throw new IllegalStateException("No OpenAPI specifications configured in swagger properties.");
        }
        for (Map.Entry<String, SwaggerProperties.SpecConfig> entry : specs.entrySet()) {
            SwaggerProperties.SpecConfig specConfig = entry.getValue();
            String specConfigUrl = specConfig.getUrl();
            String serverUrl = specConfig.getServerUrl();
            String specKey = entry.getKey();
            try {
               // String spec = new ClassPathResource(specConfigUrl).getContentAsString(StandardCharsets.UTF_8);
                OpenAPI openAPI = new OpenAPIParser().readLocation(specConfigUrl, null, null).getOpenAPI();

                if (openAPI == null) {
                    throw new IllegalStateException("Failed to parse OpenAPI specification: " + specConfigUrl);
                }

                openAPI.getPaths().forEach((path, pathItem) -> {
                    pathItem.readOperationsMap().forEach((method, operation) -> {
                        String functionName = operation.getOperationId();
                        String description = operation.getSummary() != null ? operation.getSummary() : operation.getDescription();

                        String prefixedFunctionName = specKey + "_" + functionName;
                        String prefixedDescription = "[" + specKey + "] " + description;

                        callbacks.add(createToolCallback(prefixedFunctionName, prefixedDescription, path, method, serverUrl, operation, openAPI));
                    });
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to read OpenAPI spec: " + specConfigUrl, e);
            }
        }
        return callbacks;
    }

    private ToolCallback createToolCallback(String functionName, String description, String path,
                                            io.swagger.v3.oas.models.PathItem.HttpMethod method, String serverUrl,
                                            Operation operation, OpenAPI openAPI) {
        ToolDefinition toolDefinition = ToolDefinition.builder()
            .name(functionName)
            .description(description)
            .inputSchema(schemaConverter.convertOperationToJsonSchema(openAPI, operation))
            .build();
        ToolMetadata toolMetadata = ToolMetadata.builder().build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }
            @Override
            public ToolMetadata getToolMetadata() {
                return toolMetadata;
            }
            @Override
            public String call(String input) {
                return doCall(input, null);
            }
            @Override
            public String call(String input, ToolContext toolContext) {
                return doCall(input, toolContext);
            }
            private String doCall(String input, ToolContext toolContext) {
                try {
                    String processedPath = path;
                    Object body = null;
                    Map<String, Object> requestMap = objectMapper.readValue(input, Map.class);

                    // Extract different types of parameters
                    Map<String, Object> pathParams = new HashMap<>();
                    Map<String, Object> queryParams = new HashMap<>();
                    Map<String, Object> headerParams = new HashMap<>();
                    Map<String, Object> bodyParams = new HashMap<>();

                    // First, identify path, query, and header parameters from the operation definition
                    if (operation.getParameters() != null) {
                        for (var param : operation.getParameters()) {
                            String paramName = param.getName();
                            if (requestMap.containsKey(paramName)) {
                                if ("path".equals(param.getIn())) {
                                    pathParams.put(paramName, requestMap.get(paramName));
                                } else if ("query".equals(param.getIn())) {
                                    queryParams.put(paramName, requestMap.get(paramName));
                                } else if ("header".equals(param.getIn())) {
                                    headerParams.put(paramName, requestMap.get(paramName));
                                }
                            }
                        }
                    }

                    // Handle request body for non-GET requests
                    if (method != io.swagger.v3.oas.models.PathItem.HttpMethod.GET) {
                        if (operation.getRequestBody() != null &&
                            operation.getRequestBody().getContent() != null &&
                            operation.getRequestBody().getContent().containsKey("application/json")) {
                            // For requests with body, everything not in path/query goes to body
                            bodyParams = new HashMap<>(requestMap);
                            pathParams.forEach(bodyParams::remove);
                            queryParams.forEach(bodyParams::remove);
                            body = bodyParams;
                        } else {
                            // For requests without body, treat remaining as query params
                            bodyParams = new HashMap<>(requestMap);
                            pathParams.forEach(bodyParams::remove);
                            queryParams.putAll(bodyParams);
                        }
                    } else {
                        // For GET requests, everything not in path goes to query
                        bodyParams = new HashMap<>(requestMap);
                        pathParams.forEach(bodyParams::remove);
                        queryParams.putAll(bodyParams);
                    }

                    // Process path parameters
                    for (var entry : pathParams.entrySet()) {
                        processedPath = processedPath.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                    }

                    // Build URI with query parameters
                    StringBuilder uriBuilder = new StringBuilder(serverUrl + processedPath);
                    if (!queryParams.isEmpty()) {
                        uriBuilder.append("?");
                        boolean first = true;
                        for (var entry : queryParams.entrySet()) {
                            if (!first) {
                                uriBuilder.append("&");
                            }
                            uriBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                            first = false;
                        }
                    }

                    HttpMethod httpMethod = HttpMethod.valueOf(method.name());
                    WebClient.RequestHeadersSpec<?> requestSpec = webClient.method(httpMethod)
                        .uri(uriBuilder.toString())
                        .header("Content-Type", "application/json");

                    // Add header parameters dynamically
                    for (var entry : headerParams.entrySet()) {
                        requestSpec = requestSpec.header(entry.getKey(), String.valueOf(entry.getValue()));
                    }

                    String result;
                    if (body != null) {
                        result = ((WebClient.RequestBodySpec) requestSpec).bodyValue(body).retrieve().bodyToMono(String.class).block();
                    } else {
                        result = requestSpec.retrieve().bodyToMono(String.class).block();
                    }
                    return result != null ? result : "";
                } catch (Exception e) {
                    return "Error executing API call " + functionName + ": " + e.getMessage();
                }
            }
        };
    }
}
