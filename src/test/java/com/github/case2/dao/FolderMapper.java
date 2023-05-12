package com.github.case2.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.case2.po.FolderPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public interface FolderMapper extends AbstractMapper<FolderPO, Long> {

    @Component
    public static class Mock implements FolderMapper {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<FolderPO> db = objectMapper.readValue(
                "[\n" +
                        "    {\n" +
                        "        \"id\":1,\n" +
                        "        \"name\":\"文件夹1\",\n" +
                        "        \"parentId\":null\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":2,\n" +
                        "        \"name\":\"文件夹1-1\",\n" +
                        "        \"parentId\":1\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":3,\n" +
                        "        \"name\":\"文件夹1-2\",\n" +
                        "        \"parentId\":1\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":4,\n" +
                        "        \"name\":\"文件夹2\",\n" +
                        "        \"parentId\":null\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":5,\n" +
                        "        \"name\":\"文件夹2-1\",\n" +
                        "        \"parentId\":4\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":6,\n" +
                        "        \"name\":\"文件夹2-1-1\",\n" +
                        "        \"parentId\":5\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":7,\n" +
                        "        \"name\":\"文件夹2-1-1-1\",\n" +
                        "        \"parentId\":6\n" +
                        "    }\n" +
                        "]", objectMapper.getTypeFactory().constructParametricType(List.class, FolderPO.class));

        public Mock() throws JsonProcessingException {
        }

        @Override
        public List<FolderPO> findByIds0(Collection<Long> id) {
            return db.stream().filter(e -> id != null && id.contains(e.getId())).collect(Collectors.toList());
        }

    }
}
