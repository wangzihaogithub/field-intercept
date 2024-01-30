package com.github.fieldintercept;

import com.github.fieldintercept.util.*;

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
    private Class genericType;
    private Object value;
    private Object configurableEnvironment;

    public CField(String consumerName, BeanMap beanHandler, Field field, Annotation annotation, Object configurableEnvironment) {
        this.consumerName = consumerName;
        this.beanHandler = beanHandler;
        this.bean = beanHandler.getBean();
        this.field = field;
        this.annotation = annotation;
        this.genericType = BeanUtil.getGenericType(field);
        this.configurableEnvironment = configurableEnvironment;
    }

    private static List<String> getPlaceholderAttributes(Annotation annotation, String[] attributeNames) {
        List<String> placeholders = new ArrayList<>();
        for (String attributeName : attributeNames) {
            Object keyField = AnnotationUtil.getValue(annotation, attributeName);
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
        int beginIndex = template.indexOf("${");
        return beginIndex != -1 && template.indexOf("}", beginIndex) != -1;
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
        return genericType;
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
        return setValue ? value : beanHandler.get(field.getName());
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
                PlatformDependentUtil.logError(CField.class, "ReturnFieldDispatchAop on setValue. Type cast error. field={}, data='{}', sourceType={}, targetType={}",
                        bean.getClass().getSimpleName() + "[" + field.getName() + "]",
                        object,
                        object.getClass().getSimpleName(),
                        field.getType().getSimpleName(),
                        e);
            }
        }
        if (value != null) {
            if (beanHandler.set(field.getName(), value)) {
                this.value = value;
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
    public String resolvePlaceholders(Object configurableEnvironment, Object metadata) {
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
    public String resolvePlaceholders(Collection<String> placeholders, Object configurableEnvironment, Object metadata) {
        if (PlatformDependentUtil.EXIST_SPRING) {
            if (configurableEnvironment == null) {
                configurableEnvironment = this.configurableEnvironment;
            }
            return SpringUtil.resolvePlaceholders(placeholders, configurableEnvironment, metadata);
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return bean.getClass().getSimpleName() + "." + field.getName() + " = " + getValue();
    }
}
