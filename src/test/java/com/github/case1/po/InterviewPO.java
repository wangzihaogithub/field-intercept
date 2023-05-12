package com.github.case1.po;

import java.util.Date;

public class InterviewPO extends AbstractPO<Long> {
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

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public Integer getInterviewDuration() {
        return interviewDuration;
    }

    public void setInterviewDuration(Integer interviewDuration) {
        this.interviewDuration = interviewDuration;
    }

    public Date getInterviewTime() {
        return interviewTime;
    }

    public void setInterviewTime(Date interviewTime) {
        this.interviewTime = interviewTime;
    }

    public Integer getInterType() {
        return interType;
    }

    public void setInterType(Integer interType) {
        this.interType = interType;
    }

    public Integer getInterRoundKey() {
        return interRoundKey;
    }

    public void setInterRoundKey(Integer interRoundKey) {
        this.interRoundKey = interRoundKey;
    }
}
