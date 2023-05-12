package com.github.case1.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.case1.po.UserPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public interface UserMapper extends AbstractMapper<UserPO, Long> {

    @Component
    public static class Mock implements UserMapper {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<UserPO> db = objectMapper.readValue(
                "[\n" +
                        "    {\n" +
                        "        \"id\":1,\n" +
                        "        \"nameEn\":\"用户1\",\n" +
                        "        \"nameCn\":\"YH1\"\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":2,\n" +
                        "        \"nameEn\":\"用户2\",\n" +
                        "        \"nameCn\":\"YH2\"\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":3,\n" +
                        "        \"nameEn\":\"用户3\",\n" +
                        "        \"nameCn\":\"YH3\"\n" +
                        "    }\n" +
                        "]", objectMapper.getTypeFactory().constructParametricType(List.class, UserPO.class));

        public Mock() throws JsonProcessingException {
        }

        @Override
        public List<UserPO> findByIds0(Collection<Long> id) {
            return db.stream().filter(e -> id != null && id.contains(e.getId())).collect(Collectors.toList());
        }

    }
}
