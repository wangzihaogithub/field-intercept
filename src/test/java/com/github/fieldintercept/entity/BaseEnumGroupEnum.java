package com.github.fieldintercept.entity;


/**
 * 基础枚举分组
 * 注: 是base_enum表的group字段
 * @author acer01
 */
public enum BaseEnumGroupEnum {
    /**/
    USER_LEVEL("user_level","员工级别"),
    TALENT_REPORT_HIDDEN_COMPANY("talent_report_hidden_company",""),
    POSITION_STATUS("position_status", ""),
    PROJECT_STATUS("project_status", ""),
    CORP_UPDATE("corp_update", ""),
    SCALE("scale", ""),
    PROJECT_USER_TYPE("project_user_type", ""),
    PROJECT_UPDATE_TYPE("project_update_type", ""),
    HOT("hot", ""),
    MESSAGE_RECORD_MESSAGE_TEMPLATE("message_record_message_template", ""),
    POS("pos", ""),
    APPLY_LOG_STATUS("apply_log_status", ""),
    SHARE("share", ""),
    APPLY_SAME_NAME("apply_same_name", ""),
    POSITION_DEV_LANGUAGE("position_dev_language", ""),
    POSITION_INTERVIEWINVITE("position_interviewinvite", ""),
    POSITION_UPDATE("position_update", ""),
    POSITION_UTEAT_SCALE("position_uteat_scale", ""),
    COMP("comp", ""),
    MESSAGE_RECORD_ANNOUNCEMENT_MESSAGE("message_record_announcement_message", ""),
    CORP_STATUS("corp_status", ""),
    FINANCE_NODE("finance_node", ""),
    APPLY("apply", ""),
    POSITION_EDUCATION("position_education", ""),
    POSITION_SALARY("position_salary", ""),
    PROJ("proj", ""),
    APPLY_STATUS("apply_status", "审批状态"),
    MESSAGE_RECORD_APPLICATION_APPROVAL_RESULT("message_record_application_approval_result", ""),
    POSITION_SCHOOL("position_school", ""),
    MESSAGE_RECORD_CANDIDATE_BEHAVIOR_NOTICE("message_record_candidate_behavior_notice", ""),
    POSITION_JOB("position_job", ""),
    MESSAGE_RECORD_READ_OR_UNREAD("message_record_read_or_unread", ""),
    POSITION_WORKYEAR("position_workyear", ""),
    POSITIONT_JOB_VACANCY("positiont_job_vacancy", ""),
    PROCESS_STATUS("process_status", "审核状态"),
    OPER_SUBJECT("oper_subject", ""),
    FINANCE_ROUND("finance_round", ""),
    PROJECT_SERVICE_USER("project_service_user", ""),
    POSITION_HOT("position_hot", ""),
    PROJECT_SERVICE_OBJECT("project_service_object", ""),

    TALENT_JOB_STATUS("biz_remark_latest_status", "求职状态"),
    BASE_AREA_CONTINENT("base_area_continent", "大洲"),
    BIZ_REMARK_LATEST_STATUS("biz_remark_latest_status", "最新状态"),
	BIZ_REMARK_LATEST_STATUS_COLOR("biz_remark_latest_status_color", "最新状态颜色"),
	SNAPSHOT_BUSINESS_TITLE_COLOR("snapshot_business_title_color","pipline快照标题颜色"),
	SNAPSHOT_BUSINESS_STATUS_COLOR("snapshot_business_status_color","pipline快照状态颜色"),

	BIZ_TASK_TYPE("biz_task_type", "任务类型"),
    BIZ_TASK_REPEAT_TIME("biz_task_repeat_time", "任务时间"),
    BIZ_REMARK_TYPE("biz_remark_type", "备注事件类型"),
    TALENT_COMPANY_VERIFY("talent_company_verify", "企业查重"),
    COLLEGE_LABEL("college_label", "学校标签"),
    COLLEGE_NATURE("college_nature", "大学性质"),
    BASE_AREA_TYPE("base_area_type", "区域分类"),
    TALENT_SEARCH_FOCUS("talent_search_focus", "我关注的候选人"),
    TALENT_SEX("talent_sex","性别"),
    TALENT_RESUME_TYPE("talent_resume_type","人才类型"),
    TALENT_EDUCATION("talent_education","人才学历"),
    TALENT_RECRUITMENT("talent_recruitment","是否统招"),
    POSITION_TYPE("position_type","岗位类型"),
    BIZTASKREPEATTYPE("biz_task_repeat_type","任务重复类型"),
    TIMEUNIT("time_unit","时间单位"),
    PIPELINE_REMARK_STATUS("pipeline_remark_status","pipeline备注状态类型"),
    REPORT_SEX("report_sex","性别"),

    FINANCE_ACHIEVE_ENUM("finance_achieve_enum","业绩分配角色类型"),
    FINANCE_CHARGE_ENUM("finance_charge_enum","收费类型"),
    GULU_FINANCE_CHARGE_ENUM("gulu_finance_charge_enum","谷露收费类型"),
    FINANCE_INVOICE_DATE_TYPE_ENUM("finance_invoice_date_type_enum","发票日期类型"),
    FINANCE_INVOICE_STATUS_ENUM("finance_invoice_status_enum","发票状态"),
    FINANCE_INVOICE_TYPE_ENUM("finance_invoice_type_enum","发票类型"),
    INVOICE_INVALID_REASON_ENUM("invoice_invalid_reason_enum","发票作废原因"),

    MY_COLLECT_SHARE_CHMOD("my_collect_share_chmod","我收藏的文件夹共享权限"),
    MY_COLLECT_SHARE_OBJECT_TYPE("my_collect_share_object_type","我收藏的文件夹共享对象"),

    TALENT_POSITION_REPORT_BETA_P_USER_ID("talent_position_report_beta_user","推荐报告内测员工id"),
    DATE_WEEK_TYPE("date_week_type","周度定义"),
    BASE_ENUM_MARITAL_STATUS("base_enum_marital_status","婚姻状况"),
    TALENT_SALARY_TYPE("talent_salary_type","人才薪资类型"),
    BASE_ENUM_MANAGE_APPEAL("base_enum_manage_appeal","管理诉求"),
    BASE_ENUM_WORK_STRENGTH("base_enum_work_strength","人才工作强度"),
    TALENT_LOOK_AGAIN_AT("talent_look_again_at","看机会意向"),
    TALENT_EDU_GRADUATION_STATUS("talent_edu_graduation_status","教育背景-毕业状态"),
    TALENT_LANGUAGE_REQUIRE("talent_language_require","语言要求"),
    TALENT_DOUBLE_CERT_FLAG_ENUM("talent_double_cert_flag_enum","是否双证齐全"),
    INVOICE_SUBJECT_ENUM("invoice-subject-enum","发票主题"),
    BIZ_CORP_BU_STATUS_ENUM("biz_corp_bu_status","bu状态"),
    BIZ_BU_OPER_TYPE_ENUM("biz_bu_oper_type_enum","bu申请单类型"),
    GIFT_ADDRESS_TYPE("gift_address_type","礼品地址类型"),
    POSITION_REC_INDEX_SEARCH("position_rec_index_search_enum","推荐星数"),
    ;

    private String group;
    private String remark;

    BaseEnumGroupEnum(String group, String remark) {
        this.group = group;
        this.remark = remark;
    }

    public String getGroup() {
        return group;
    }

    public String getRemark() {
        return remark;
    }
}
