package com.github.sherman.mcp.tool.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "swagger")
public class SwaggerProperties {
    private Map<String, SpecConfig> specs;

    public Map<String, SpecConfig> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, SpecConfig> specs) {
        this.specs = specs;
    }

    public static class SpecConfig {
        private String url;
        private String serverUrl;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }
    }
}
