package com.jhun.backend.unit.config.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class AiDevProfileDefaultsTest {

    @Test
    void shouldEnableQwenChatByDefaultInDevProfile() {
        Map<String, Object> properties = resolvePlaceholders(loadYaml("application-dev.yml"));

        assertThat(requireProperty(properties, "ai.enabled")).isEqualTo("true");
        assertThat(requireProperty(properties, "ai.provider")).isEqualTo("qwen");
    }

    @Test
    void shouldResolveApiKeyFromPreferredAiQwenAlias() {
        Map<String, Object> properties = new LinkedHashMap<>(loadYaml("application.yml"));
        properties.put("AI_QWEN_API_KEY", "dev-qwen-key");

        Map<String, Object> resolved = resolvePlaceholders(properties);

        assertThat(requireProperty(resolved, "ai.qwen.api-key")).isEqualTo("dev-qwen-key");
    }

    private Map<String, Object> loadYaml(String resourcePath) {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource(resourcePath));
        Properties properties = yamlFactory.getObject();

        Map<String, Object> loaded = new LinkedHashMap<>();
        if (properties == null) {
            return loaded;
        }

        for (String propertyName : properties.stringPropertyNames()) {
            loaded.put(propertyName, properties.getProperty(propertyName));
        }
        return loaded;
    }

    private Map<String, Object> resolvePlaceholders(Map<String, Object> merged) {
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(new MapPropertySource("merged", merged));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String stringValue) {
                resolved.put(entry.getKey(), resolver.resolvePlaceholders(stringValue));
                continue;
            }
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    private String requireProperty(Map<String, Object> properties, String propertyName) {
        Object value = properties.get(propertyName);
        return value == null ? "" : value.toString();
    }
}
