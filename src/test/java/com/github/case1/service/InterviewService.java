package com.github.case1.service;

import com.github.case1.dao.InterviewMapper;
import com.github.case1.enumer.Providers;
import com.github.case1.po.InterviewPO;
import org.springframework.stereotype.Service;

@Service(Providers.INTERVIEW)
public class InterviewService extends AbstractService<InterviewMapper, InterviewPO, Long> {

}
