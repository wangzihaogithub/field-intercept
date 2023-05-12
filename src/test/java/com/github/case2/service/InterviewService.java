package com.github.case2.service;

import com.github.case2.dao.InterviewMapper;
import com.github.case2.enumer.Providers;
import com.github.case2.po.InterviewPO;
import org.springframework.stereotype.Service;

@Service(Providers.INTERVIEW)
public class InterviewService extends AbstractService<InterviewMapper, InterviewPO, Long> {

}
