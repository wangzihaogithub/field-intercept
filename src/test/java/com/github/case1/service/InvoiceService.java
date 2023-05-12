package com.github.case1.service;

import com.github.case1.dao.InvoiceMapper;
import com.github.case1.enumer.Providers;
import com.github.case1.po.InvoicePO;
import org.springframework.stereotype.Service;

@Service(Providers.INVOICE)
public class InvoiceService extends AbstractService<InvoiceMapper, InvoicePO, Long> {

}
