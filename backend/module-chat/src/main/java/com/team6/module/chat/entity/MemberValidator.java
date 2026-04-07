package com.team6.module.chat.entity;

import java.util.List;
import java.util.Map;

public interface MemberValidator {

    void validateMemberExist(List<Long> memberIds);

    String getNickname(Long MemberId);

    Map<Long, String> getValidatedNicknameMap(List<Long> memberIds);

}
