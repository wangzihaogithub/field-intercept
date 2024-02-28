package com.github.fieldintercept;

import com.github.fieldintercept.util.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOP用的消费字段
 *
 * @author acer01
 */
public class CField {
    private static final AtomicLong idIncr = new AtomicLong();
    private static final Set<String> NO_SUCH_FIELD_SET = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> CLASS_NOT_FOUND_SET = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final long id;
    private final Object bean;
    private final BeanMap beanHandler;
    private final Field field;
    private final Annotation annotation;
    private final Annotation castAnnotation;
    private final Class<? extends Annotation> castType;
    private final String consumerName;
    private final Class genericType;
    private final Object configurableEnvironment;
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
    private Object value;

    public CField(String consumerName, BeanMap beanHandler, Field field, Annotation annotation, Annotation castAnnotation, Class<? extends Annotation> castType, Object configurableEnvironment) {
        this.id = idIncr.getAndIncrement();
        this.consumerName = consumerName;
        this.beanHandler = beanHandler;
        this.bean = beanHandler.getBean();
        this.field = field;
        this.annotation = annotation;
        this.castAnnotation = castAnnotation;
        this.castType = castType;
        this.genericType = BeanUtil.getGenericType(field);
        this.configurableEnvironment = configurableEnvironment;
    }

    public CField(SerializeCField serializeCField) throws ClassNotFoundException, NoSuchFieldException {
        Object bean = serializeCField.bean;
        String consumerName = serializeCField.consumerName;
        int annotationIndex = serializeCField.annotationIndex;
        String declaringClassName = serializeCField.declaringClassName;
        String castTypeName = serializeCField.castTypeName;
        String fieldName = serializeCField.fieldName;
        Class declaringClass;
        try {
            declaringClass = Class.forName(declaringClassName);
        } catch (ClassNotFoundException e) {
            CLASS_NOT_FOUND_SET.add(declaringClassName);
            throw e;
        }
        Class castTypeClass;
        try {
            castTypeClass = Class.forName(castTypeName);
        } catch (ClassNotFoundException e) {
            CLASS_NOT_FOUND_SET.add(castTypeName);
            throw e;
        }
        Field field;
        try {
            field = declaringClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            NO_SUCH_FIELD_SET.add(declaringClassName + "#" + fieldName);
            throw e;
        }
        Annotation[] declaredAnnotations = field.getDeclaredAnnotations();
        ReturnFieldDispatchAop<Object> instance = ReturnFieldDispatchAop.getInstance();
        serializeCField.cField = this;
        this.id = serializeCField.id;
        this.consumerName = consumerName;
        this.keyDataList = serializeCField.keyDataList;
        this.beanHandler = new BeanMap(bean);
        this.bean = bean;
        this.field = field;
        this.annotation = annotationIndex < declaredAnnotations.length ? declaredAnnotations[annotationIndex] : null;
        this.castAnnotation = AnnotationUtil.cast(this.annotation, castTypeClass);
        this.genericType = BeanUtil.getGenericType(field);
        this.configurableEnvironment = instance != null ? instance.getConfigurableEnvironment() : null;
        this.castType = castTypeClass;
    }

    private static boolean isDeconstruct(String attr) {
        return "./".equals(attr) || ".".equals(attr);
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
        if (isDeconstruct(template)) {
            return true;
        }
        int beginIndex = template.indexOf("${");
        return beginIndex != -1 && template.indexOf("}", beginIndex) != -1;
    }

    public static List<CField> parse(List<SerializeCField> cFields) {
        List<CField> cFieldList = new ArrayList<>(cFields.size());
        for (CField.SerializeCField serializeCField : cFields) {
            CField cField = serializeCField.asCField();
            if (cField != null) {
                cFieldList.add(cField);
            }
        }
        return cFieldList;
    }

    public String keyDataId(Object keyData) {
        return "kd_" + id + "_" + keyData;
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

    public long getId() {
        return id;
    }

    public Annotation getCastAnnotation() {
        return castAnnotation;
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
        try {
            Object value = TypeUtil.cast(object, field.getType(), true);
            if (value != null) {
                if (beanHandler.set(field.getName(), value)) {
                    this.value = value;
                    this.setValue = true;
                }
            }
        } catch (Exception e) {
            PlatformDependentUtil.logError(CField.class, "ReturnFieldDispatchAop on setValue. Type cast error. field={}, data='{}', sourceType={}, targetType={}",
                    bean.getClass().getSimpleName() + "[" + field.getName() + "]",
                    object,
                    object.getClass().getSimpleName(),
                    field.getType().getSimpleName(),
                    e);
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
        return resolvePlaceholders(getPlaceholders(), configurableEnvironment, metadata);
    }

    public String resolvePlaceholders(Object metadata) {
        return resolvePlaceholders(getPlaceholders(), configurableEnvironment, metadata);
    }

    public List<String> getPlaceholders() {
        if (placeholders == null) {
            String[] attributeNames = {"valueField", "keyField"};
            List<String> placeholderAttributes = getPlaceholderAttributes(annotation, attributeNames);
            for (int i = 0, size = placeholderAttributes.size(); i < size; i++) {
                if (isDeconstruct(placeholderAttributes.get(i))) {
                    placeholderAttributes.set(i, "${" + field.getName() + "}");
                }
            }
            placeholders = placeholderAttributes;
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
        return PlatformDependentUtil.EXIST_SPRING ? SpringUtil.resolvePlaceholders(placeholders, configurableEnvironment, metadata) : null;
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

    public static class SerializeCField {
        private long id;
        private Object bean;
        private Collection keyDataList;
        private String consumerName;
        private int annotationIndex;
        private String declaringClassName;
        private String castTypeName;
        private String fieldName;
        private transient CField cField;

        public SerializeCField() {

        }

        public SerializeCField(CField cField) {
            int index = 0;
            for (Annotation declaredAnnotation : cField.field.getDeclaredAnnotations()) {
                if (declaredAnnotation == cField.annotation) {
                    break;
                }
                index++;
            }
            this.id = cField.id;
            this.keyDataList = cField.keyDataList;
            this.annotationIndex = index;
            this.bean = cField.bean;
            this.consumerName = cField.consumerName;
            this.declaringClassName = cField.field.getDeclaringClass().getName();
            this.castTypeName = cField.castType.getName();
            this.fieldName = cField.field.getName();
        }

        public CField asCField() {
            if (cField == null) {
                if (CLASS_NOT_FOUND_SET.contains(declaringClassName)
                        || CLASS_NOT_FOUND_SET.contains(castTypeName)
                        || NO_SUCH_FIELD_SET.contains(declaringClassName + "#" + fieldName)) {
                    return null;
                } else {
                    try {
                        cField = new CField(this);
                    } catch (ClassNotFoundException | NoSuchFieldException ignored) {

                    }
                }
            }
            return cField;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public Collection getKeyDataList() {
            return keyDataList;
        }

        public void setKeyDataList(Collection keyDataList) {
            this.keyDataList = keyDataList;
        }

        public Object getBean() {
            return bean;
        }

        public void setBean(Object bean) {
            this.bean = bean;
        }

        public String getConsumerName() {
            return consumerName;
        }

        public void setConsumerName(String consumerName) {
            this.consumerName = consumerName;
        }

        public int getAnnotationIndex() {
            return annotationIndex;
        }

        public void setAnnotationIndex(int annotationIndex) {
            this.annotationIndex = annotationIndex;
        }

        public String getDeclaringClassName() {
            return declaringClassName;
        }

        public void setDeclaringClassName(String declaringClassName) {
            this.declaringClassName = declaringClassName;
        }

        public String getCastTypeName() {
            return castTypeName;
        }

        public void setCastTypeName(String castTypeName) {
            this.castTypeName = castTypeName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }
    }
}
