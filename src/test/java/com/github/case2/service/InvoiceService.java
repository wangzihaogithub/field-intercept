package com.github.case2.service;

import com.github.case2.dao.InvoiceMapper;
import com.github.case2.enumer.Providers;
import com.github.case2.po.InvoicePO;
import org.springframework.stereotype.Service;

@Service(Providers.INVOICE)
public class InvoiceService extends AbstractService<InvoiceMapper, InvoicePO, Long> {

}
