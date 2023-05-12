package com.github.case2.po;

public class InvoicePO extends AbstractPO<Long> {
    private Long id;
    private String invoiceTitle;
    private String invoiceNum;
    private Long invoiceMoney;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceTitle() {
        return invoiceTitle;
    }

    public void setInvoiceTitle(String invoiceTitle) {
        this.invoiceTitle = invoiceTitle;
    }

    public String getInvoiceNum() {
        return invoiceNum;
    }

    public void setInvoiceNum(String invoiceNum) {
        this.invoiceNum = invoiceNum;
    }

    public Long getInvoiceMoney() {
        return invoiceMoney;
    }

    public void setInvoiceMoney(Long invoiceMoney) {
        this.invoiceMoney = invoiceMoney;
    }
}
