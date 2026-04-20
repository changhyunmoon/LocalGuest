package com.team6.domain.member.dto.request;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GuestProfileUpdateRequest {

    private String profileImageUrl;
    private String bio;
}