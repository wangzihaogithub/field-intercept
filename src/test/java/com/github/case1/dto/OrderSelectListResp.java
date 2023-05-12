package com.github.case1.dto;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.case1.annotation.EnumDBFieldConsumer;
import com.github.case1.enumer.BizEnumGroupEnum;
import com.github.case1.enumer.InterTypeEnum;
import com.github.case1.enumer.Providers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class OrderSelectListResp {
    private String invoiceIds;
    private Long currentInterviewId;
    private String partUserIds;
    private Long createUserId;

    public OrderSelectListResp(String invoiceIds, Long currentInterviewId, String partUserIds, Long createUserId) {
        this.invoiceIds = invoiceIds;
        this.currentInterviewId = currentInterviewId;
        this.partUserIds = partUserIds;
        this.createUserId = createUserId;
    }

    @FieldConsumer(value = Providers.INVOICE, keyField = "invoiceIds")
    private List<Invoice> invoiceList;
    @FieldConsumer(value = Providers.INTERVIEW, keyField = "currentInterviewId")
    private CurrentInterview currentInterview;

    @FieldConsumer(value = Providers.USER, keyField = "partUserIds", joinDelimiter = ",")
    private String partUserNames;
    @FieldConsumer(value = Providers.USER, keyField = "partUserIds")
    private List<String> partUserNameList;

    @FieldConsumer(value = Providers.USER, keyField = "createUserId", valueField = "${nameEn}/${nameCn}")
    private String createUserName;

    public static class Invoice {
        private Long id;
        private String invoiceTitle;
        private String invoiceNum;
        private Long invoiceMoney;

        public Long getId() {
            return id;
        }

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

    public static class CurrentInterview {
        private Long id;
        /**
         * 面试时长：单位分钟
         */
        private Integer interviewDuration;
        /**
         * 几点面试
         */
        private Date interviewTime;

        /**
         * 面试类型
         */
        private Integer interType;
        /**
         * 面试轮次
         */
        private Integer interRoundKey;

        @EnumFieldConsumer(value = InterTypeEnum.class, keyField = "interType")
        private String interTypeName;
        @EnumFieldConsumer(value = InterTypeEnum.class, keyField = "interType", valueField = "${color}")
        private String interTypeColor;
        @EnumDBFieldConsumer(value = BizEnumGroupEnum.INTER_ROUND, keyField = "interRoundKey")
        private String interRoundName;

        public String getTitle() {
            // 2021-11-30 10:00 初试 现场面试 时长：30分钟
            return String.format("%s %s %s 时长：%d分钟",
                    new SimpleDateFormat("yyyy-M-dd HH:mm").format(interviewTime),
                    interRoundName,
                    interTypeName,
                    interviewDuration);
        }

        public Long getId() {
            return id;
        }
    }

    public List<String> getPartUserNameList() {
        return partUserNameList;
    }

    public List<Invoice> getInvoiceList() {
        return invoiceList;
    }

    public String getPartUserNames() {
        return partUserNames;
    }

    public String getCreateUserName() {
        return createUserName;
    }

    public CurrentInterview getCurrentInterview() {
        return currentInterview;
    }

    public String getInvoiceIds() {
        return invoiceIds;
    }

    public Long getCurrentInterviewId() {
        return currentInterviewId;
    }

    public String getPartUserIds() {
        return partUserIds;
    }

    public Long getCreateUserId() {
        return createUserId;
    }
}
