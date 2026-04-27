package com.team6.domain.member.entity;

public enum Role {
    GUEST, GUIDE, ADMIN;

    public String getKey() {
        return "ROLE_" + this.name();
    }
}
