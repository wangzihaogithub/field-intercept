package com.github.case1.dao;

import com.github.case1.po.AbstractPO;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public interface AbstractMapper<PO extends AbstractPO<ID>, ID extends Number> {

    public default List<PO> findByIds(Collection<ID> id) {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 100));
            return findByIds0(id);
        } catch (Exception e) {
            throw new RuntimeException("InterruptedException", e);
        } finally {
            // LoggerFactory.getLogger(getClass()).info("findByIds({}) end {}/ms", id, System.currentTimeMillis() - start);
        }
    }

    public abstract List<PO> findByIds0(Collection<ID> id);

}
