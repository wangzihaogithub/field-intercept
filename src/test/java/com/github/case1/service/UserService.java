package com.github.case1.service;

import com.github.case1.dao.UserMapper;
import com.github.case1.enumer.Providers;
import com.github.case1.po.UserPO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service(Providers.USER)
public class UserService extends AbstractService<UserMapper, UserPO, Long> {

    @Override
    protected Map<Long, String> convertNames(List<UserPO> list) {
        return list.stream().collect(Collectors.toMap(
                UserPO::getId,
                po -> po.getNameCn() + "/" + po.getNameEn()));
    }

}
