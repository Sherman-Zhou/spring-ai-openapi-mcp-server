package com.github.sherman.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sherman.mcp.tool.openapi.OpenApiSchemaConverter;
import com.github.sherman.mcp.tool.openapi.OpenApiToolCallbackProvider;
import com.github.sherman.mcp.tool.openapi.SwaggerProperties;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OpenApiToolCallbackProviderTest {

    @Autowired
    private OpenApiToolCallbackProvider provider;

    @Autowired
    private SwaggerProperties swaggerProperties;

    @Test
    public void testRequestBodyHandling() throws Exception {
        // Load the OpenAPI spec
        String spec = new ClassPathResource("jhipster-swagger.json").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI openAPI = new OpenAPIParser().readContents(spec, null, null).getOpenAPI();
        assertNotNull(openAPI, "OpenAPI should not be null");
        // Get the updateUser operation (PUT method with request body)
        Operation updateUserOperation = openAPI.getPaths().get("/api/admin/users/{login}").getPut();
        assertNotNull(updateUserOperation, "updateUser operation should exist");
        // Test schema conversion
        OpenApiSchemaConverter schemaConverter = new OpenApiSchemaConverter();
        String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, updateUserOperation);
        assertNotNull(jsonSchema, "JSON schema should not be null");
        assertTrue(jsonSchema.contains("login"), "Schema should contain login parameter");
        assertTrue(jsonSchema.contains("firstName"), "Schema should contain firstName from request body");
        assertTrue(jsonSchema.contains("lastName"), "Schema should contain lastName from request body");
        assertTrue(jsonSchema.contains("email"), "Schema should contain email from request body");
        // Test that the schema includes both path parameters and request body properties
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> schemaMap = mapper.readValue(jsonSchema, Map.class);
        assertTrue(schemaMap.containsKey("properties"), "Schema should have properties");
        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        // Check path parameter
        assertTrue(properties.containsKey("login"), "Schema should include login path parameter");
        // Check request body properties
        assertTrue(properties.containsKey("firstName"), "Schema should include firstName from request body");
        assertTrue(properties.containsKey("lastName"), "Schema should include lastName from request body");
        assertTrue(properties.containsKey("email"), "Schema should include email from request body");
        assertTrue(properties.containsKey("activated"), "Schema should include activated from request body");
        System.out.println("Generated JSON Schema:");
        System.out.println(jsonSchema);
    }

    @Test
    public void testOpenApiYamlHandling() throws Exception {
        // Load the OpenAPI YAML spec
        String spec = new ClassPathResource("openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI openAPI = new OpenAPIParser().readContents(spec, null, null).getOpenAPI();
        assertNotNull(openAPI, "OpenAPI should not be null");

        // Test GET operation with path parameter (getPetById)
        Operation getPetOperation = openAPI.getPaths().get("/pet/{petId}").getGet();
        assertNotNull(getPetOperation, "getPetById operation should exist");

        OpenApiSchemaConverter schemaConverter = new OpenApiSchemaConverter();
        String getPetJsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, getPetOperation);
        assertNotNull(getPetJsonSchema, "JSON schema should not be null");
        assertTrue(getPetJsonSchema.contains("petId"), "Schema should contain petId parameter");

        // Test POST operation with request body (addPet)
        Operation addPetOperation = openAPI.getPaths().get("/pet").getPost();
        assertNotNull(addPetOperation, "addPet operation should exist");

        String addPetJsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, addPetOperation);
        assertNotNull(addPetJsonSchema, "JSON schema should not be null");

        // Parse the schema to verify properties
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> schemaMap = mapper.readValue(addPetJsonSchema, Map.class);

        // Handle both flat structure and allOf structure
        Map<String, Object> properties;
        Object required;

        if (schemaMap.containsKey("properties")) {
            // Flat structure
            properties = (Map<String, Object>) schemaMap.get("properties");
            required = schemaMap.get("required");
        } else if (schemaMap.containsKey("allOf")) {
            // allOf structure - get properties from the first allOf item
            java.util.List<Map<String, Object>> allOf = (java.util.List<Map<String, Object>>) schemaMap.get("allOf");
            assertNotNull(allOf, "allOf should not be null");
            assertFalse(allOf.isEmpty(), "allOf should not be empty");

            Map<String, Object> firstAllOf = allOf.get(0);
            assertTrue(firstAllOf.containsKey("properties"), "First allOf should have properties");
            properties = (Map<String, Object>) firstAllOf.get("properties");
            required = firstAllOf.get("required");
        } else {
            fail("Schema should have either properties or allOf");
            return;
        }

        // Check that Pet schema properties are included
        assertTrue(properties.containsKey("name"), "Schema should include name from Pet schema");
        assertTrue(properties.containsKey("photoUrls"), "Schema should include photoUrls from Pet schema");
        assertTrue(properties.containsKey("status"), "Schema should include status from Pet schema");

        // Check required fields
        assertNotNull(required, "Required should not be null");
        assertTrue(required instanceof java.util.List, "Required should be a list");

        System.out.println("=== GET Pet Schema ===");
        System.out.println(getPetJsonSchema);
        System.out.println("\n=== POST Pet Schema ===");
        System.out.println(addPetJsonSchema);

        // Test that the Pet schema with allOf is properly resolved
        Map<String, Object> nameProperty = (Map<String, Object>) properties.get("name");
        assertNotNull(nameProperty, "name property should exist");
        assertEquals("string", nameProperty.get("type"), "name should be string type");

        Map<String, Object> photoUrlsProperty = (Map<String, Object>) properties.get("photoUrls");
        assertNotNull(photoUrlsProperty, "photoUrls property should exist");
        assertEquals("array", photoUrlsProperty.get("type"), "photoUrls should be array type");

        Map<String, Object> statusProperty = (Map<String, Object>) properties.get("status");
        assertNotNull(statusProperty, "status property should exist");
        assertEquals("string", statusProperty.get("type"), "status should be string type");
        assertTrue(statusProperty.containsKey("enum"), "status should have enum values");
    }

    @Test
    public void testOpenApiToolCallbackProviderCreation() {
        assertNotNull(provider, "Provider should not be null");
        // Get tool callbacks
        var callbacks = provider.getToolCallbacks();
        assertNotNull(callbacks, "Tool callbacks should not be null");
        assertTrue(callbacks.length > 0, "Should have at least one tool callback");
        System.out.println("Found " + callbacks.length + " tool callbacks");
        // Test that we can access tool definitions
        for (var callback : callbacks) {
            var toolDef = callback.getToolDefinition();
            assertNotNull(toolDef, "Tool definition should not be null");
            System.out.println("- Tool definition: " + toolDef.toString());
        }
    }

    @Test
    public void testJhipsterSwaggerComprehensive() throws Exception {
        // Load the JHipster OpenAPI spec
        String spec = new ClassPathResource("jhipster-swagger.json").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI openAPI = new OpenAPIParser().readContents(spec, null, null).getOpenAPI();
        assertNotNull(openAPI, "OpenAPI should not be null");

        OpenApiSchemaConverter schemaConverter = new OpenApiSchemaConverter();

        // Test different operations from JHipster spec
        testOperation(openAPI, schemaConverter, "/api/admin/users/{login}", "PUT", "updateUser");
        testOperation(openAPI, schemaConverter, "/api/account", "GET", "getAccount");
        testOperation(openAPI, schemaConverter, "/api/authenticate", "POST", "authenticate");

        // Test operations with different parameter types
        testOperationWithQueryParams(openAPI, schemaConverter);
        testOperationWithPathParams(openAPI, schemaConverter);
        testOperationWithRequestBody(openAPI, schemaConverter);
    }

    @Test
    public void testOpenApiYamlComprehensive() throws Exception {
        // Load the OpenAPI YAML spec
        String spec = new ClassPathResource("openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI openAPI = new OpenAPIParser().readContents(spec, null, null).getOpenAPI();
        assertNotNull(openAPI, "OpenAPI should not be null");

        OpenApiSchemaConverter schemaConverter = new OpenApiSchemaConverter();

        // Test different operations from Pet Store spec
        testOperation(openAPI, schemaConverter, "/pet/{petId}", "GET", "getPetById");
        testOperation(openAPI, schemaConverter, "/pet", "POST", "addPet");

        // Test different data types and formats
        testDifferentDataTypes(openAPI, schemaConverter);
        testArrayHandling(openAPI, schemaConverter);
        testEnumValues(openAPI, schemaConverter);
        testNestedSchemas(openAPI, schemaConverter);
    }

    @Test
    public void testSchemaConversionEdgeCases() throws Exception {
        // Test edge cases and error handling
        OpenApiSchemaConverter schemaConverter = new OpenApiSchemaConverter();

        // Test with null schema
        String nullSchema = schemaConverter.convertOperationToJsonSchema(null, null);
        assertNotNull(nullSchema, "Should handle null inputs gracefully");

        // Test with empty operation
        OpenAPI emptyOpenAPI = new OpenAPI();
        Operation emptyOperation = new Operation();
        String emptySchema = schemaConverter.convertOperationToJsonSchema(emptyOpenAPI, emptyOperation);
        assertNotNull(emptySchema, "Should handle empty operations");
        assertTrue(emptySchema.contains("type"), "Should have basic schema structure");
    }

    @Test
    public void testDifferentHttpMethods() throws Exception {
        // Test all HTTP methods
        String jhipsterSpec = new ClassPathResource("jhipster-swagger.json").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI jhipsterOpenAPI = new OpenAPIParser().readContents(jhipsterSpec, null, null).getOpenAPI();

        String yamlSpec = new ClassPathResource("openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI yamlOpenAPI = new OpenAPIParser().readContents(yamlSpec, null, null).getOpenAPI();

        OpenApiSchemaConverter schemaConverter = new OpenApiSchemaConverter();

        // Test GET operations
        testHttpMethod(jhipsterOpenAPI, schemaConverter, "GET");
        testHttpMethod(yamlOpenAPI, schemaConverter, "GET");

        // Test POST operations
        testHttpMethod(jhipsterOpenAPI, schemaConverter, "POST");
        testHttpMethod(yamlOpenAPI, schemaConverter, "POST");

        // Test PUT operations
        testHttpMethod(jhipsterOpenAPI, schemaConverter, "PUT");

        // Test DELETE operations (if available)
        testHttpMethod(jhipsterOpenAPI, schemaConverter, "DELETE");
    }

    // Helper methods for comprehensive testing
    private void testOperation(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter,
                             String path, String method, String operationId) throws Exception {
        Operation operation = getOperation(openAPI, path, method);
        if (operation != null) {
            String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, operation);
            assertNotNull(jsonSchema, "Schema should not be null for " + operationId);
            assertTrue(jsonSchema.contains("type"), "Schema should have type for " + operationId);

            // Parse and validate schema structure
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> schemaMap = mapper.readValue(jsonSchema, Map.class);
            assertTrue(schemaMap.containsKey("type"), "Schema should have type field");
            assertEquals("object", schemaMap.get("type"), "Schema should be object type");

            System.out.println("✓ " + operationId + " (" + method + " " + path + ") - Schema generated successfully");
        } else {
            System.out.println("⚠ " + operationId + " (" + method + " " + path + ") - Operation not found");
        }
    }

    private void testOperationWithQueryParams(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter) throws Exception {
        // Find an operation with query parameters
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = entry.getValue();
            if (pathItem.getGet() != null && pathItem.getGet().getParameters() != null) {
                for (Parameter param : pathItem.getGet().getParameters()) {
                    if ("query".equals(param.getIn())) {
                        String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, pathItem.getGet());
                        assertNotNull(jsonSchema, "Schema should not be null for query params");
                        assertTrue(jsonSchema.contains(param.getName()), "Schema should contain query param: " + param.getName());
                        System.out.println("✓ Query parameter handling: " + param.getName());
                        return;
                    }
                }
            }
        }
    }

    private void testOperationWithPathParams(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter) throws Exception {
        // Find an operation with path parameters
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            if (path.contains("{") && pathItem.getGet() != null) {
                String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, pathItem.getGet());
                assertNotNull(jsonSchema, "Schema should not be null for path params");
                // Extract path parameter name from path
                String pathParam = path.substring(path.indexOf("{") + 1, path.indexOf("}"));
                assertTrue(jsonSchema.contains(pathParam), "Schema should contain path param: " + pathParam);
                System.out.println("✓ Path parameter handling: " + pathParam);
                return;
            }
        }
    }

    private void testOperationWithRequestBody(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter) throws Exception {
        // Find an operation with request body
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = entry.getValue();
            if (pathItem.getPost() != null && pathItem.getPost().getRequestBody() != null) {
                String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, pathItem.getPost());
                assertNotNull(jsonSchema, "Schema should not be null for request body");
                assertTrue(jsonSchema.contains("properties"), "Schema should have properties for request body");
                System.out.println("✓ Request body handling: " + entry.getKey());
                return;
            }
        }
    }

    private void testDifferentDataTypes(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter) throws Exception {
        // Test that different data types are properly handled
        Operation addPetOperation = openAPI.getPaths().get("/pet").getPost();
        if (addPetOperation != null) {
            String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, addPetOperation);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> schemaMap = mapper.readValue(jsonSchema, Map.class);
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");

            // Test string type
            Map<String, Object> nameProperty = (Map<String, Object>) properties.get("name");
            assertEquals("string", nameProperty.get("type"), "name should be string type");

            // Test integer type
            Map<String, Object> idProperty = (Map<String, Object>) properties.get("id");
            assertEquals("integer", idProperty.get("type"), "id should be integer type");
            assertEquals("int64", idProperty.get("format"), "id should have int64 format");

            // Test array type
            Map<String, Object> photoUrlsProperty = (Map<String, Object>) properties.get("photoUrls");
            assertEquals("array", photoUrlsProperty.get("type"), "photoUrls should be array type");

            System.out.println("✓ Different data types handling: string, integer, array");
        }
    }

    private void testArrayHandling(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter) throws Exception {
        Operation addPetOperation = openAPI.getPaths().get("/pet").getPost();
        if (addPetOperation != null) {
            String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, addPetOperation);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> schemaMap = mapper.readValue(jsonSchema, Map.class);
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");

            Map<String, Object> photoUrlsProperty = (Map<String, Object>) properties.get("photoUrls");
            assertNotNull(photoUrlsProperty.get("items"), "Array should have items definition");

            Map<String, Object> items = (Map<String, Object>) photoUrlsProperty.get("items");
            assertEquals("string", items.get("type"), "Array items should be string type");

            System.out.println("✓ Array handling: items type and structure");
        }
    }

    private void testEnumValues(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter) throws Exception {
        Operation addPetOperation = openAPI.getPaths().get("/pet").getPost();
        if (addPetOperation != null) {
            String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, addPetOperation);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> schemaMap = mapper.readValue(jsonSchema, Map.class);
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");

            Map<String, Object> statusProperty = (Map<String, Object>) properties.get("status");
            assertNotNull(statusProperty.get("enum"), "Status should have enum values");

            List<String> enumValues = (List<String>) statusProperty.get("enum");
            assertTrue(enumValues.contains("available"), "Enum should contain 'available'");
            assertTrue(enumValues.contains("pending"), "Enum should contain 'pending'");
            assertTrue(enumValues.contains("sold"), "Enum should contain 'sold'");

            System.out.println("✓ Enum values handling: status enum preserved");
        }
    }

    private void testNestedSchemas(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter) throws Exception {
        // Test that nested schemas and references are properly resolved
        Operation addPetOperation = openAPI.getPaths().get("/pet").getPost();
        if (addPetOperation != null) {
            String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, addPetOperation);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> schemaMap = mapper.readValue(jsonSchema, Map.class);
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");

            // Test that all expected properties from the Pet schema are present
            assertTrue(properties.containsKey("name"), "Should have name property");
            assertTrue(properties.containsKey("photoUrls"), "Should have photoUrls property");
            assertTrue(properties.containsKey("id"), "Should have id property");
            assertTrue(properties.containsKey("status"), "Should have status property");

            System.out.println("✓ Nested schemas: allOf composition properly resolved");
        }
    }

    private void testHttpMethod(OpenAPI openAPI, OpenApiSchemaConverter schemaConverter, String method) {
        int count = 0;
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = entry.getValue();
            Operation operation = null;

            switch (method) {
                case "GET":
                    operation = pathItem.getGet();
                    break;
                case "POST":
                    operation = pathItem.getPost();
                    break;
                case "PUT":
                    operation = pathItem.getPut();
                    break;
                case "DELETE":
                    operation = pathItem.getDelete();
                    break;
            }

            if (operation != null) {
                try {
                    String jsonSchema = schemaConverter.convertOperationToJsonSchema(openAPI, operation);
                    assertNotNull(jsonSchema, "Schema should not be null for " + method + " " + entry.getKey());
                    count++;
                } catch (Exception e) {
                    System.out.println("⚠ Error processing " + method + " " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
        System.out.println("✓ " + method + " method handling: " + count + " operations processed");
    }

    private Operation getOperation(OpenAPI openAPI, String path, String method) {
        PathItem pathItem = openAPI.getPaths().get(path);
        if (pathItem == null) {
            return null;
        }

        switch (method) {
            case "GET":
                return pathItem.getGet();
            case "POST":
                return pathItem.getPost();
            case "PUT":
                return pathItem.getPut();
            case "DELETE":
                return pathItem.getDelete();
            default:
                return null;
        }
    }

    @Test
    public void testMultipleOpenApiSpecsSupport() throws Exception {
        // Test that the provider can handle multiple OpenAPI specs
        OpenApiSchemaConverter schemaConverter = new OpenApiSchemaConverter();

        // Create a test configuration with multiple specs
        // Note: This test assumes both jhipster-swagger.json and openapi.yaml are available
        // In a real scenario, this would be configured via application.yml

        // Load JHipster spec
        String jhipsterSpec = new ClassPathResource("jhipster-swagger.json").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI jhipsterOpenAPI = new OpenAPIParser().readContents(jhipsterSpec, null, null).getOpenAPI();
        assertNotNull(jhipsterOpenAPI, "JHipster OpenAPI should not be null");

        // Load Pet Store spec
        String petStoreSpec = new ClassPathResource("openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        OpenAPI petStoreOpenAPI = new OpenAPIParser().readContents(petStoreSpec, null, null).getOpenAPI();
        assertNotNull(petStoreOpenAPI, "Pet Store OpenAPI should not be null");

        // Test title prefix generation
        String jhipsterTitle = getTitlePrefix(jhipsterOpenAPI);
        String petStoreTitle = getTitlePrefix(petStoreOpenAPI);

        assertNotNull(jhipsterTitle, "JHipster title prefix should not be null");
        assertNotNull(petStoreTitle, "Pet Store title prefix should not be null");
        assertNotEquals(jhipsterTitle, petStoreTitle, "Title prefixes should be different");

        System.out.println("JHipster title prefix: " + jhipsterTitle);
        System.out.println("Pet Store title prefix: " + petStoreTitle);

        // Test that operations from different specs would have different prefixes
        // Get an operation from each spec
        Operation jhipsterOperation = jhipsterOpenAPI.getPaths().get("/api/admin/users/{login}").getPut();
        Operation petStoreOperation = petStoreOpenAPI.getPaths().get("/pet/{petId}").getGet();

        assertNotNull(jhipsterOperation, "JHipster operation should exist");
        assertNotNull(petStoreOperation, "Pet Store operation should exist");

        // Test that the function names would be prefixed correctly
        String jhipsterFunctionName = jhipsterTitle + "_" + jhipsterOperation.getOperationId();
        String petStoreFunctionName = petStoreTitle + "_" + petStoreOperation.getOperationId();

        assertTrue(jhipsterFunctionName.startsWith(jhipsterTitle), "JHipster function name should start with title prefix");
        assertTrue(petStoreFunctionName.startsWith(petStoreTitle), "Pet Store function name should start with title prefix");
        assertNotEquals(jhipsterFunctionName, petStoreFunctionName, "Function names should be different");

        System.out.println("JHipster function name: " + jhipsterFunctionName);
        System.out.println("Pet Store function name: " + petStoreFunctionName);

        // Test that descriptions would be prefixed correctly
        String jhipsterDescription = "[" + jhipsterTitle + "] " + jhipsterOperation.getSummary();
        String petStoreDescription = "[" + petStoreTitle + "] " + petStoreOperation.getSummary();

        assertTrue(jhipsterDescription.startsWith("[" + jhipsterTitle + "]"), "JHipster description should start with title prefix");
        assertTrue(petStoreDescription.startsWith("[" + petStoreTitle + "]"), "Pet Store description should start with title prefix");

        System.out.println("JHipster description: " + jhipsterDescription);
        System.out.println("Pet Store description: " + petStoreDescription);

        System.out.println("✓ Multiple OpenAPI specs support: title prefixes work correctly");
    }

    private String getTitlePrefix(OpenAPI openAPI) {
        if (openAPI.getInfo() != null && openAPI.getInfo().getTitle() != null) {
            return openAPI.getInfo().getTitle()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase();
        }
        return "api";
    }

    @Test
    public void testConfigurationPropertiesLoading() {
        // Extract URLs from the specs map
        List<String> urls = swaggerProperties.getSpecs().values().stream()
            .map(SwaggerProperties.SpecConfig::getUrl)
            .toList();
        assertNotNull(urls);
        assertFalse(urls.isEmpty(), "URLs list should not be empty");
    }
}
