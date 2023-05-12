package com.github.case1.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.case1.po.BizEnumPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public interface BizEnumMapper extends AbstractMapper<BizEnumPO, Long> {

    List<BizEnumPO> selectEnumGroupKeyValueList(Set<String> groups, Collection<Object> keys);

    @Component
    public static class Mock implements BizEnumMapper {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<BizEnumPO> db = objectMapper.readValue(
                "[\n" +
                        "    {\n" +
                        "        \"id\":1,\n" +
                        "        \"group\":\"inter_round\",\n" +
                        "        \"key\":\"1\",\n" +
                        "        \"value\":\"1面\"\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":2,\n" +
                        "        \"group\":\"inter_round\",\n" +
                        "        \"key\":\"2\",\n" +
                        "        \"value\":\"2面\"\n" +
                        "    }\n" +
                        "]", objectMapper.getTypeFactory().constructParametricType(List.class, BizEnumPO.class));

        public Mock() throws JsonProcessingException {
        }

        @Override
        public List<BizEnumPO> findByIds0(Collection<Long> id) {
            return db.stream().filter(e -> id != null && id.contains(e.getId())).collect(Collectors.toList());
        }

        @Override
        public List<BizEnumPO> selectEnumGroupKeyValueList(Set<String> groups, Collection<Object> keys) {
            long start = System.currentTimeMillis();
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(50, 100));
                return selectEnumGroupKeyValueList0(groups, keys);
            } catch (Exception e) {
                throw new RuntimeException("InterruptedException", e);
            } finally {
                // LoggerFactory.getLogger(getClass()).info("selectEnumGroupKeyValueList({}, {}) end {}/ms", groups, keys, System.currentTimeMillis() - start);
            }
        }

        private List<BizEnumPO> selectEnumGroupKeyValueList0(Set<String> groups, Collection<Object> keys) {
            Set<String> stringKeys = keys.stream().map(Object::toString).collect(Collectors.toSet());
            return db.stream()
                    .filter(e -> groups.contains(e.getGroup()) && stringKeys.contains(e.getKey()))
                    .collect(Collectors.toList());
        }
    }
}
