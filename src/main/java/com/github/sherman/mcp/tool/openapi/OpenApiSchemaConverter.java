package com.github.sherman.mcp.tool.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class OpenApiSchemaConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Schema> resolvedSchemas = new HashMap<>();

    public String convertOperationToJsonSchema(OpenAPI openAPI, io.swagger.v3.oas.models.Operation operation) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        Set<String> required = new HashSet<>();

        // Handle null inputs gracefully
        if (operation == null) {
            return schema.toString();
        }

        // Handle path, query, and header parameters as top-level properties in the schema
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                String paramName = parameter.getName();
                Schema paramSchema = parameter.getSchema();
                // Optionally, you could annotate header parameters here if needed
                // For now, all parameters (path, query, header) are included as top-level properties
                if (paramSchema != null) {
                    properties.set(paramName, convertSchemaToJsonSchema(openAPI, paramSchema));
                    if (Boolean.TRUE.equals(parameter.getRequired())) {
                        required.add(paramName);
                    }
                }
            }
        }

        // Handle request body
        if (operation.getRequestBody() != null) {
            RequestBody requestBody = operation.getRequestBody();
            if (requestBody.getContent() != null && requestBody.getContent().containsKey("application/json")) {
                Schema bodySchema = requestBody.getContent().get("application/json").getSchema();
                if (bodySchema != null) {
//                    try {
//                        log.info("bodySchema:{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(bodySchema));
//                    } catch (JsonProcessingException e) {
//                        throw new RuntimeException(e);
//                    }
                    // For request body, we'll merge the properties into the main schema
                    JsonNode bodyJsonSchema = convertSchemaToJsonSchema(openAPI, bodySchema);
                    // Recursively resolve $ref until we get properties
                    int maxDepth = 5; // prevent infinite loop
                    while (bodyJsonSchema.has("$ref") && maxDepth-- > 0) {
                        String ref = bodyJsonSchema.get("$ref").asText();
                        bodyJsonSchema = resolveReference(openAPI, ref);
                    }
                    if (bodyJsonSchema.has("properties")) {
                        Iterator<Map.Entry<String, JsonNode>> fields = bodyJsonSchema.get("properties").fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            properties.set(field.getKey(), field.getValue());
                        }
                    } else if (bodyJsonSchema.has("allOf")) {
                        // Handle allOf structure - merge properties from all allOf items
                        bodyJsonSchema.get("allOf").forEach(allOfItem -> {
                            if (allOfItem.has("properties")) {
                                Iterator<Map.Entry<String, JsonNode>> fields = allOfItem.get("properties").fields();
                                while (fields.hasNext()) {
                                    Map.Entry<String, JsonNode> field = fields.next();
                                    properties.set(field.getKey(), field.getValue());
                                }
                            }
                        });
                    }

                    if (bodyJsonSchema.has("required")) {
                        bodyJsonSchema.get("required").forEach(requiredField ->
                            required.add(requiredField.asText()));
                    } else if (bodyJsonSchema.has("allOf")) {
                        // Handle required fields from allOf structure
                        bodyJsonSchema.get("allOf").forEach(allOfItem -> {
                            if (allOfItem.has("required")) {
                                allOfItem.get("required").forEach(requiredField ->
                                    required.add(requiredField.asText()));
                            }
                        });
                    }
                }
            }
        }

        if (!properties.isEmpty()) {
            schema.set("properties", properties);
        }

        if (!required.isEmpty()) {
            schema.set("required", objectMapper.valueToTree(new ArrayList<>(required)));
        }

        return schema.toString();
    }

    public JsonNode convertSchemaToJsonSchema(OpenAPI openAPI, Schema schema) {
        if (schema == null) {
            return objectMapper.createObjectNode().put("type", "object");
        }

        // Handle $ref
        if (schema.get$ref() != null) {
            return resolveReference(openAPI, schema.get$ref());
        }

        ObjectNode jsonSchema = objectMapper.createObjectNode();

        // Handle generic Schema with type object and properties
        if (("object".equals(schema.getType()) ||
             (schema.getTypes() != null && schema.getTypes().contains("object"))) &&
            schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            handleObjectSchema(openAPI, schema, jsonSchema);
            addCommonSchemaProperties(schema, jsonSchema);
            return jsonSchema;
        }

        // Handle different schema types
        if (schema instanceof StringSchema) {
            handleStringSchema((StringSchema) schema, jsonSchema);
        } else if (schema instanceof IntegerSchema) {
            handleIntegerSchema((IntegerSchema) schema, jsonSchema);
        } else if (schema instanceof NumberSchema) {
            handleNumberSchema((NumberSchema) schema, jsonSchema);
        } else if (schema instanceof BooleanSchema) {
            handleBooleanSchema((BooleanSchema) schema, jsonSchema);
        } else if (schema instanceof ArraySchema) {
            handleArraySchema(openAPI, (ArraySchema) schema, jsonSchema);
        } else if (schema instanceof ObjectSchema) {
            handleObjectSchema(openAPI, schema, jsonSchema);
        } else if (schema instanceof ComposedSchema) {
            handleComposedSchema(openAPI, (ComposedSchema) schema, jsonSchema);
        } else {
            // Generic schema handling
            handleGenericSchema(openAPI, schema, jsonSchema);
        }

        // Add common properties
        addCommonSchemaProperties(schema, jsonSchema);

        return jsonSchema;
    }

    private void handleStringSchema(StringSchema schema, ObjectNode jsonSchema) {
        jsonSchema.put("type", "string");

        if (schema.getMinLength() != null) {
            jsonSchema.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            jsonSchema.put("maxLength", schema.getMaxLength());
        }
        if (schema.getPattern() != null) {
            jsonSchema.put("pattern", schema.getPattern());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            jsonSchema.set("enum", objectMapper.valueToTree(schema.getEnum()));
        }
        if (schema.getFormat() != null) {
            jsonSchema.put("format", schema.getFormat());
        }
    }

    private void handleIntegerSchema(IntegerSchema schema, ObjectNode jsonSchema) {
        jsonSchema.put("type", "integer");

        if (schema.getMinimum() != null) {
            jsonSchema.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            jsonSchema.put("maximum", schema.getMaximum());
        }
        if (schema.getExclusiveMinimum() != null) {
            jsonSchema.put("exclusiveMinimum", schema.getExclusiveMinimum());
        }
        if (schema.getExclusiveMaximum() != null) {
            jsonSchema.put("exclusiveMaximum", schema.getExclusiveMaximum());
        }
        if (schema.getMultipleOf() != null) {
            jsonSchema.put("multipleOf", schema.getMultipleOf());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            jsonSchema.set("enum", objectMapper.valueToTree(schema.getEnum()));
        }
        if (schema.getFormat() != null) {
            jsonSchema.put("format", schema.getFormat());
        }
    }

    private void handleNumberSchema(NumberSchema schema, ObjectNode jsonSchema) {
        jsonSchema.put("type", "number");

        if (schema.getMinimum() != null) {
            jsonSchema.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            jsonSchema.put("maximum", schema.getMaximum());
        }
        if (schema.getExclusiveMinimum() != null) {
            jsonSchema.put("exclusiveMinimum", schema.getExclusiveMinimum());
        }
        if (schema.getExclusiveMaximum() != null) {
            jsonSchema.put("exclusiveMaximum", schema.getExclusiveMaximum());
        }
        if (schema.getMultipleOf() != null) {
            jsonSchema.put("multipleOf", schema.getMultipleOf());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            jsonSchema.set("enum", objectMapper.valueToTree(schema.getEnum()));
        }
        if (schema.getFormat() != null) {
            jsonSchema.put("format", schema.getFormat());
        }
    }

    private void handleBooleanSchema(BooleanSchema schema, ObjectNode jsonSchema) {
        jsonSchema.put("type", "boolean");

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            jsonSchema.set("enum", objectMapper.valueToTree(schema.getEnum()));
        }
    }

    private void handleArraySchema(OpenAPI openAPI, ArraySchema schema, ObjectNode jsonSchema) {
        jsonSchema.put("type", "array");

        if (schema.getItems() != null) {
            jsonSchema.set("items", convertSchemaToJsonSchema(openAPI, schema.getItems()));
        }

        if (schema.getMinItems() != null) {
            jsonSchema.put("minItems", schema.getMinItems());
        }
        if (schema.getMaxItems() != null) {
            jsonSchema.put("maxItems", schema.getMaxItems());
        }
        if (schema.getUniqueItems() != null) {
            jsonSchema.put("uniqueItems", schema.getUniqueItems());
        }
    }

    private void handleObjectSchema(OpenAPI openAPI, Schema schema, ObjectNode jsonSchema) {
        jsonSchema.put("type", "object");

        // Handle object-specific properties
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            ObjectNode properties = objectMapper.createObjectNode();
            // Use a safer approach to iterate over properties
            for (Object key : schema.getProperties().keySet()) {
                String keyStr = String.valueOf(key);
                Object value = schema.getProperties().get(key);
                if (value instanceof Schema) {
                    properties.set(keyStr, convertSchemaToJsonSchema(openAPI, (Schema) value));
                }
            }
            jsonSchema.set("properties", properties);
        }

        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            jsonSchema.set("required", objectMapper.valueToTree(schema.getRequired()));
        }

        if (schema.getMinProperties() != null) {
            jsonSchema.put("minProperties", schema.getMinProperties());
        }
        if (schema.getMaxProperties() != null) {
            jsonSchema.put("maxProperties", schema.getMaxProperties());
        }

        // Handle additional properties
        if (schema.getAdditionalProperties() != null) {
            if (schema.getAdditionalProperties() instanceof Boolean) {
                jsonSchema.put("additionalProperties", (Boolean) schema.getAdditionalProperties());
            } else if (schema.getAdditionalProperties() instanceof Schema) {
                jsonSchema.set("additionalProperties",
                    convertSchemaToJsonSchema(openAPI, (Schema) schema.getAdditionalProperties()));
            }
        }
    }

    private void handleComposedSchema(OpenAPI openAPI, ComposedSchema schema, ObjectNode jsonSchema) {
        // Handle allOf
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            ObjectNode allOf = objectMapper.createObjectNode();
            allOf.put("type", "object");
            ObjectNode allOfProperties = objectMapper.createObjectNode();
            List<String> allOfRequired = new ArrayList<>();

            for (Schema allOfSchema : schema.getAllOf()) {
                JsonNode converted = convertSchemaToJsonSchema(openAPI, allOfSchema);
                if (converted.has("properties")) {
                    Iterator<Map.Entry<String, JsonNode>> fields = converted.get("properties").fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        allOfProperties.set(field.getKey(), field.getValue());
                    }
                }
                if (converted.has("required")) {
                    converted.get("required").forEach(requiredField ->
                        allOfRequired.add(requiredField.asText()));
                }
            }

            allOf.set("properties", allOfProperties);
            if (!allOfRequired.isEmpty()) {
                allOf.set("required", objectMapper.valueToTree(allOfRequired));
            }
            jsonSchema.set("allOf", objectMapper.createArrayNode().add(allOf));
        }

        // Handle anyOf
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            jsonSchema.set("anyOf", objectMapper.valueToTree(
                schema.getAnyOf().stream()
                    .map(s -> convertSchemaToJsonSchema(openAPI, s))
                    .toList()));
        }

        // Handle oneOf
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            jsonSchema.set("oneOf", objectMapper.valueToTree(
                schema.getOneOf().stream()
                    .map(s -> convertSchemaToJsonSchema(openAPI, s))
                    .toList()));
        }

        // Handle not
        if (schema.getNot() != null) {
            jsonSchema.set("not", convertSchemaToJsonSchema(openAPI, schema.getNot()));
        }
    }

    private void handleGenericSchema(OpenAPI openAPI, Schema schema, ObjectNode jsonSchema) {
        // Handle generic schema properties
        if (schema.getType() != null) {
            jsonSchema.put("type", schema.getType());
        } else if (schema.getTypes() != null && !schema.getTypes().isEmpty()) {
            // Handle types array - take the first type for now
            // In JSON Schema, we typically use a single type, not an array
            String firstType = String.valueOf(schema.getTypes().iterator().next());
            jsonSchema.put("type", firstType);
        }

        // Handle string-specific properties
        if ("string".equals(schema.getType()) ||
            (schema.getTypes() != null && schema.getTypes().contains("string"))) {
            if (schema.getMinLength() != null) {
                jsonSchema.put("minLength", schema.getMinLength());
            }
            if (schema.getMaxLength() != null) {
                jsonSchema.put("maxLength", schema.getMaxLength());
            }
            if (schema.getPattern() != null) {
                jsonSchema.put("pattern", schema.getPattern());
            }
            if (schema.getFormat() != null) {
                jsonSchema.put("format", schema.getFormat());
            }
        }

        // Handle number/integer-specific properties
        if ("number".equals(schema.getType()) || "integer".equals(schema.getType()) ||
            (schema.getTypes() != null && (schema.getTypes().contains("number") || schema.getTypes().contains("integer")))) {
            if (schema.getMinimum() != null) {
                jsonSchema.put("minimum", schema.getMinimum());
            }
            if (schema.getMaximum() != null) {
                jsonSchema.put("maximum", schema.getMaximum());
            }
            if (schema.getExclusiveMinimum() != null) {
                jsonSchema.put("exclusiveMinimum", schema.getExclusiveMinimum());
            }
            if (schema.getExclusiveMaximum() != null) {
                jsonSchema.put("exclusiveMaximum", schema.getExclusiveMaximum());
            }
            if (schema.getMultipleOf() != null) {
                jsonSchema.put("multipleOf", schema.getMultipleOf());
            }
            if (schema.getFormat() != null) {
                jsonSchema.put("format", schema.getFormat());
            }
        }

        // Handle array-specific properties
        if ("array".equals(schema.getType()) ||
            (schema.getTypes() != null && schema.getTypes().contains("array"))) {
            if (schema.getItems() != null) {
                jsonSchema.set("items", convertSchemaToJsonSchema(openAPI, schema.getItems()));
            }
            if (schema.getMinItems() != null) {
                jsonSchema.put("minItems", schema.getMinItems());
            }
            if (schema.getMaxItems() != null) {
                jsonSchema.put("maxItems", schema.getMaxItems());
            }
            if (schema.getUniqueItems() != null) {
                jsonSchema.put("uniqueItems", schema.getUniqueItems());
            }
        }

        if (schema.getDescription() != null) {
            jsonSchema.put("description", schema.getDescription());
        }

        if (schema.getTitle() != null) {
            jsonSchema.put("title", schema.getTitle());
        }

        if (schema.getDefault() != null) {
            jsonSchema.set("default", objectMapper.valueToTree(schema.getDefault()));
        }

        if (schema.getExample() != null) {
            jsonSchema.set("example", objectMapper.valueToTree(schema.getExample()));
        }

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            jsonSchema.set("enum", objectMapper.valueToTree(schema.getEnum()));
        }
    }

    private void addCommonSchemaProperties(Schema schema, ObjectNode jsonSchema) {
        if (schema.getDescription() != null) {
            jsonSchema.put("description", schema.getDescription());
        }

        if (schema.getTitle() != null) {
            jsonSchema.put("title", schema.getTitle());
        }

        if (schema.getDefault() != null) {
            jsonSchema.set("default", objectMapper.valueToTree(schema.getDefault()));
        }

        if (schema.getExample() != null) {
            jsonSchema.set("example", objectMapper.valueToTree(schema.getExample()));
        }

        if (schema.getNullable() != null) {
            jsonSchema.put("nullable", schema.getNullable());
        }

        if (schema.getReadOnly() != null) {
            jsonSchema.put("readOnly", schema.getReadOnly());
        }

        if (schema.getWriteOnly() != null) {
            jsonSchema.put("writeOnly", schema.getWriteOnly());
        }
    }

    private JsonNode resolveReference(OpenAPI openAPI, String ref) {
        log.info("Resolving reference: {}", ref);

        // Check if we've already resolved this reference
        if (resolvedSchemas.containsKey(ref)) {
            log.info("Found cached reference: {}", ref);
            Schema resolved = resolvedSchemas.get(ref);
            // Recursively resolve $ref if present
            while (resolved.get$ref() != null) {
                String nextRef = resolved.get$ref();
                log.info("Following nested reference: {}", nextRef);
                resolved = openAPI.getComponents().getSchemas().get(nextRef.substring("#/components/schemas/".length()));
                if (resolved == null) break;
            }
            JsonNode result = convertSchemaToJsonSchema(openAPI, resolved);
            log.info("Cached resolution result: {}", result.toString());
            return result;
        }

        // Parse the reference
        if (ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring("#/components/schemas/".length());
            log.info("Looking for schema: {}", schemaName);
            Components components = openAPI.getComponents();

            if (components != null && components.getSchemas() != null) {
                Schema referencedSchema = components.getSchemas().get(schemaName);
                if (referencedSchema != null) {
                    log.info("Found schema: {}", schemaName);
                    resolvedSchemas.put(ref, referencedSchema);
                    // Recursively resolve $ref if present
                    while (referencedSchema.get$ref() != null) {
                        String nextRef = referencedSchema.get$ref();
                        log.info("Following nested reference: {}", nextRef);
                        referencedSchema = components.getSchemas().get(nextRef.substring("#/components/schemas/".length()));
                        if (referencedSchema == null) break;
                    }
                    JsonNode result = convertSchemaToJsonSchema(openAPI, referencedSchema);
                    log.info("Resolution result: {}", result.toString());
                    return result;
                } else {
                    log.warn("Schema not found: {}", schemaName);
                }
            } else {
                log.warn("No components or schemas found");
            }
        }

        // If we can't resolve the reference, return a generic object
        log.warn("Could not resolve reference: {}, returning generic object", ref);
        return objectMapper.createObjectNode().put("type", "object");
    }

    public void clearCache() {
        resolvedSchemas.clear();
    }
}
