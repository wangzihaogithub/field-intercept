package com.github.fieldintercept;

import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.BeanUtil;
import com.github.fieldintercept.util.TypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.*;
import org.springframework.util.SystemPropertyUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * AOP用的消费字段
 *
 * @author acer01
 */
public class CField {
    private final static Logger log = LoggerFactory.getLogger(CField.class);
    private final Object bean;
    private final BeanMap beanHandler;
    private final Field field;
    private final Annotation annotation;
    /**
     * 这个字段被这个消费者消费
     */
    private final String consumerName;
    /**
     * key的数据
     */
    private Object keyData;
    private Collection keyDataList;
    /**
     * 是否已经调用过setter方法并成功赋值
     */
    private boolean setValue;
    /**
     * 是否存在用占位符解析
     */
    private Boolean existPlaceholder;
    private List<String> placeholders;

    public CField(String consumerName, BeanMap beanHandler, Field field, Annotation annotation) {
        this.consumerName = consumerName;
        this.beanHandler = beanHandler;
        this.bean = beanHandler.getBean();
        this.field = field;
        this.annotation = annotation;
    }

    private static List<String> getPlaceholderAttributes(Annotation annotation, String[] attributeNames) {
        List<String> placeholders = new ArrayList<>();
        for (String attributeName : attributeNames) {
            Object keyField = AnnotationUtils.getValue(annotation, attributeName);
            if (keyField instanceof String[] && ((String[]) keyField).length > 0) {
                placeholders.addAll(Arrays.asList((String[]) keyField));
            } else if (keyField instanceof String && !"".equals(keyField)) {
                placeholders.add((String) keyField);
            }
        }
        placeholders.removeIf(e -> !existResolve(e));
        return placeholders;
    }

    public static boolean existResolve(String template) {
        if (template == null) {
            return false;
        }
        int beginIndex = template.indexOf(SystemPropertyUtils.PLACEHOLDER_PREFIX);
        return beginIndex != -1 && template.indexOf(SystemPropertyUtils.PLACEHOLDER_SUFFIX, beginIndex) != -1;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public BeanMap getBeanHandler() {
        return beanHandler;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public Object getBean() {
        return bean;
    }

    public Field getField() {
        return field;
    }

    public Object getKeyData() {
        return keyData;
    }

    public void setKeyData(Object keyData) {
        this.keyData = keyData;
    }

    public Collection getKeyDataList() {
        return keyDataList;
    }

    public void setKeyDataList(Collection keyDataList) {
        this.keyDataList = keyDataList;
    }

    public Class getGenericType() {
        return BeanUtil.getGenericType(field);
    }

    public boolean existPlaceholder() {
        if (existPlaceholder == null) {
            existPlaceholder = getPlaceholders().size() > 0;
        }
        return existPlaceholder;
    }

    public Class getType() {
        return field.getType();
    }

    public Object getValue() {
        return beanHandler.get(field.getName());
    }

    public void setValue(Object object) {
        if (object == null) {
            return;
        }
        Object value = null;
        try {
            value = TypeUtil.cast(object, field.getType());
        } catch (Exception e) {
            try {
                value = BeanUtil.transform(object, field.getType());
            } catch (Exception e1) {
                log.error("ReturnFieldDispatchAop on setValue. Type cast error. field={}, data='{}', sourceType={}, targetType={}",
                        bean.getClass().getSimpleName() + "[" + field.getName() + "]",
                        object,
                        object.getClass().getSimpleName(),
                        field.getType().getSimpleName(),
                        e);
            }
        }
        if (value != null) {
            if (beanHandler.set(field.getName(), value)) {
                this.setValue = true;
            }
        }
    }

    public boolean isSetValue() {
        return setValue;
    }

    public boolean existValue() {
        if (Modifier.isFinal(field.getModifiers())) {
            return true;
        }
        Object value = getValue();
        if (value == null || "".equals(value)) {
            return false;
        }
        if (value instanceof Iterable) {
            return ((Iterable) value).iterator().hasNext();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        if (value instanceof Map) {
            return !((Map) value).isEmpty();
        }
        return true;
    }

    /**
     * 解析占位符
     *
     * @param configurableEnvironment spring配置环境
     * @param metadata                元数据
     * @return 解析后
     */
    public String resolvePlaceholders(ConfigurableEnvironment configurableEnvironment, Object metadata) {
        List<String> placeholders = getPlaceholders();
        return resolvePlaceholders(placeholders, configurableEnvironment, metadata);
    }

    public List<String> getPlaceholders() {
        if (placeholders == null) {
            String[] attributeNames = {"valueField", "keyField"};
            placeholders = getPlaceholderAttributes(annotation, attributeNames);
        }
        return placeholders;
    }

    /**
     * 解析占位符
     *
     * @param placeholders            占位符
     * @param configurableEnvironment spring配置环境
     * @param metadata                元数据
     * @return 解析后
     */
    public String resolvePlaceholders(Collection<String> placeholders, ConfigurableEnvironment configurableEnvironment, Object metadata) {
        if (placeholders == null || placeholders.isEmpty()) {
            return null;
        }
        Map map = BeanMap.toMap(metadata);
        MutablePropertySources propertySources = new MutablePropertySources();
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources) {
            @Override
            protected String getPropertyAsRawString(String key) {
                String[] keys = key.split("[.]");
                if (keys.length == 1) {
                    return getProperty(key, String.class, true);
                } else {
                    Object value = map.get(keys[0]);
                    Map value2Map = BeanMap.toMap(value);
                    for (int i = 1; i < keys.length; i++) {
                        value = value2Map.get(keys[i]);
                        if (value == null) {
                            break;
                        }
                        if (i != keys.length - 1) {
                            value2Map = BeanMap.toMap(value);
                        }
                    }
                    return value == null ? null : String.valueOf(value);
                }
            }

            @Override
            protected void logKeyFound(String key, PropertySource<?> propertySource, Object value) {

            }
        };
        propertySources.addLast(new MapPropertySource(map.getClass().getSimpleName(), map));
        if (configurableEnvironment != null) {
            for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
                propertySources.addLast(propertySource);
            }
            resolver.setConversionService(configurableEnvironment.getConversionService());
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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CField) {
            CField target = (CField) obj;
            return bean == target.bean
                    && Objects.equals(keyData, target.keyData)
                    && Objects.equals(consumerName, target.consumerName)
                    && Objects.equals(field.getName(), target.field.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + System.identityHashCode(bean);
        result = 31 * result + (keyData == null ? 0 : keyData.hashCode());
        result = 31 * result + (consumerName == null ? 0 : consumerName.hashCode());
        result = 31 * result + field.getName().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return bean.getClass().getSimpleName() + "." + field.getName() + " = " + beanHandler.get(field.getName());
    }
}
