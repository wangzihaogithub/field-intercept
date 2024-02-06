package com.github.case1.service;


import com.github.case1.dao.AbstractMapper;
import com.github.case1.po.AbstractPO;
import com.github.fieldintercept.CompositeFieldIntercept;
import com.github.fieldintercept.KeyNameFieldIntercept;
import com.github.fieldintercept.KeyValueFieldIntercept;
import com.github.fieldintercept.util.TypeUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractService<REPOSITORY extends AbstractMapper<PO, ID>,
        PO extends AbstractPO<ID>,
        ID extends Number> implements CompositeFieldIntercept<ID, PO, Object> {
    @Autowired
    protected REPOSITORY repository;
    private final Class<?> beanType = TypeUtil.findGenericType(this, AbstractService.class, "PO");
    private final PropertyDescriptor nameGetter = BeanUtils.getPropertyDescriptor(beanType, "name");

    private final Class<ID> keyClass = CompositeFieldIntercept.getKeyClass(this, AbstractService.class, "ID", Integer.class);
    private final Class<PO> valueClass = CompositeFieldIntercept.getValueClass(this, AbstractService.class, "PO", Object.class);
    private final KeyNameFieldIntercept<ID, Object> keyNameFieldIntercept = new KeyNameFieldIntercept<>(keyClass, this::selectNameMapByKeys);
    private final KeyValueFieldIntercept<ID, PO, Object> keyValueFieldIntercept = new KeyValueFieldIntercept<>(keyClass, valueClass, this::selectValueMapByKeys);

    @Override
    public KeyNameFieldIntercept<ID, Object> keyNameFieldIntercept() {
        return keyNameFieldIntercept;
    }

    @Override
    public KeyValueFieldIntercept<ID, PO, Object> keyValueFieldIntercept() {
        return keyValueFieldIntercept;
    }

    public List<PO> findByIds(Collection<ID> ids) {
        return repository.findByIds(ids);
    }

    public Map<ID, String> selectNameMapByKeys(Collection<ID> ids) {
        return convertNames(repository.findByIds(ids));
    }

    public Map<ID, PO> selectValueMapByKeys(Collection<ID> ids) {
        return repository.findByIds(ids).stream()
                .collect(Collectors.toMap(AbstractPO::getId, e -> e));
    }

    protected Map<ID, String> convertNames(List<PO> pos) {
        return pos.stream().collect(Collectors.toMap(AbstractPO::getId, this::convertName));
    }

    protected String convertName(PO po) {
        try {
            return (String) nameGetter.getReadMethod().invoke(po);
        } catch (Exception e) {
            throw new UnsupportedOperationException(beanType + ".convertName", e);
        }
    }

}
