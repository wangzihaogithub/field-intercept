package com.github.fieldintercept.util;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.*;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class SpringUtil {
    private static final Pattern DOT_PATTERN = Pattern.compile("[.]");

    public static void registryBean(BeanDefinitionRegistry registry, String beanName, Supplier<Object> bean) {
        registry.registerBeanDefinition(beanName,
                BeanDefinitionBuilder.genericBeanDefinition(Object.class, bean).getBeanDefinition());
    }

    public static void registryBean(BeanDefinitionRegistry registry, String beanName, Object bean) {
        Class beanClass = bean.getClass();
        registry.registerBeanDefinition(beanName,
                BeanDefinitionBuilder.genericBeanDefinition(beanClass, () -> bean).getBeanDefinition());
    }

    public static String resolvePlaceholders(Collection<String> placeholders, Object configurableEnvironment, Object metadata) {
        if (placeholders == null || placeholders.isEmpty() || metadata == null) {
            return null;
        }
        Map<String, Object> metadataGetter = BeanMap.toMap(metadata);
        MutablePropertySources propertySources = new MutablePropertySources();
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources) {
            @Override
            protected String getPropertyAsRawString(String key) {
                String[] keys = DOT_PATTERN.split(key);
                if (keys.length == 1) {
                    return getProperty(trim(key), String.class, true);
                } else {
                    Object value = metadataGetter.get(trim(keys[0]));
                    if (value != null) {
                        Map value2Map = BeanMap.toMap(value);
                        for (int i = 1; i < keys.length; i++) {
                            value = value2Map.get(trim(keys[i]));
                            if (value == null) {
                                break;
                            }
                            if (i != keys.length - 1) {
                                value2Map = BeanMap.toMap(value);
                            }
                        }
                    }
                    return value == null ? null : String.valueOf(value);
                }
            }

            @Override
            protected void logKeyFound(String key, PropertySource<?> propertySource, Object value) {

            }
        };

        propertySources.addLast(new MapPropertySource(metadata.getClass().getSimpleName(), metadataGetter));
        if (configurableEnvironment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment env = (ConfigurableEnvironment) configurableEnvironment;
            for (PropertySource<?> propertySource : env.getPropertySources()) {
                propertySources.addLast(propertySource);
            }
            resolver.setConversionService(env.getConversionService());
        }
        for (String placeholder : placeholders) {
            try {
                String value = resolver.resolvePlaceholders(placeholder);
                if (Objects.equals(value, placeholder)) {
                    continue;
                }
                return value;
            } catch (IllegalArgumentException e) {
                //skip
            }
        }
        return null;
    }

    private static String trim(String key) {
        if (!key.isEmpty() && key.charAt(0) == '_') {
            return key.substring(1);
        } else {
            return key;
        }
    }

}
