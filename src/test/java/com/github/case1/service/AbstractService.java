package com.github.case1.service;


import com.github.fieldintercept.CompositeFieldIntercept;
import com.github.fieldintercept.util.TypeUtil;
import com.github.case1.dao.AbstractMapper;
import com.github.case1.po.AbstractPO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;

import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractService<REPOSITORY extends AbstractMapper<PO, ID>,
        PO extends AbstractPO<ID>,
        ID extends Number> extends CompositeFieldIntercept<ID, PO, Object> {
    @Autowired
    protected REPOSITORY repository;
    private final Class<?> beanType = TypeUtil.findGenericType(this, AbstractService.class, "PO");
    private final PropertyDescriptor nameGetter = BeanUtils.getPropertyDescriptor(beanType, "name");

    public List<PO> findByIds(Collection<ID> ids) {
        return repository.findByIds(ids);
    }

    @Override
    public Map<ID, ?> selectNameMapByKeys(Collection<ID> ids) {
        return convertNames(repository.findByIds(ids));
    }

    @Override
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
