package com.github.case2.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.case2.po.InvoicePO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public interface InvoiceMapper extends AbstractMapper<InvoicePO, Long> {

    @Component
    public static class Mock implements InvoiceMapper {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<InvoicePO> db = objectMapper.readValue(
                "[\n" +
                        "    {\n" +
                        "        \"id\":1,\n" +
                        "        \"invoiceTitle\":\"饿了吗\",\n" +
                        "        \"invoiceNum\":\"ELM\",\n" +
                        "        \"invoiceMoney\":100\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":2,\n" +
                        "        \"invoiceTitle\":\"美团\",\n" +
                        "        \"invoiceNum\":\"MLM\",\n" +
                        "        \"invoiceMoney\":200\n" +
                        "    },\n" +
                        "    {\n" +
                        "        \"id\":3,\n" +
                        "        \"invoiceTitle\":\"吃了团\",\n" +
                        "        \"invoiceNum\":\"CLM\",\n" +
                        "        \"invoiceMoney\":300\n" +
                        "    }\n" +
                        "]", objectMapper.getTypeFactory().constructParametricType(List.class, InvoicePO.class));

        public Mock() throws JsonProcessingException {
        }

        @Override
        public List<InvoicePO> findByIds0(Collection<Long> id) {
            return db.stream().filter(e -> id != null && id.contains(e.getId())).collect(Collectors.toList());
        }

    }
}
