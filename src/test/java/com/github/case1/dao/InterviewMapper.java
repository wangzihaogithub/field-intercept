package com.github.case1.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.case1.po.InterviewPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public interface InterviewMapper extends AbstractMapper<InterviewPO, Long> {

    @Component
    public static class Mock implements InterviewMapper {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<InterviewPO> db = objectMapper.readValue(
                "[\n" +
                        "    {\n" +
                        "        \"id\":1,\n" +
                        "        \"interviewDuration\":10,\n" +
                        "        \"interviewTime\":\"2023-01-01\",\n" +
                        "        \"interType\":1,\n" +
                        "        \"interRoundKey\":1\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":2,\n" +
                        "        \"interviewDuration\":20,\n" +
                        "        \"interviewTime\":\"2023-01-02\",\n" +
                        "        \"interType\":2,\n" +
                        "        \"interRoundKey\":2\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":3,\n" +
                        "        \"interviewDuration\":30,\n" +
                        "        \"interviewTime\":\"2023-01-03\",\n" +
                        "        \"interType\":3,\n" +
                        "        \"interRoundKey\":3\n" +
                        "    }\n" +
                        "]", objectMapper.getTypeFactory().constructParametricType(List.class, InterviewPO.class));

        public Mock() throws JsonProcessingException {
        }

        @Override
        public List<InterviewPO> findByIds0(Collection<Long> id) {
            return db.stream().filter(e -> id != null && id.contains(e.getId())).collect(Collectors.toList());
        }

    }
}
