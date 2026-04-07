package com.team6.domain.member.excpetion;

import com.team6.module.common.global.exception.CustomException;

import java.util.List;

public class MemberNotFoundException extends CustomException {
    public MemberNotFoundException() {
        super(MemberExceptionCode.MEMBER_NOT_FOUND);
    }
}
