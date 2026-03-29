package com.priyanshu.upifraudshieldai.user.dto;

import com.priyanshu.upifraudshieldai.user.entity.UpiVpa;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VpaResponse
{

    private UUID id;
    private String vpa;
    private String bankName;
    private String accountLast4;
    private UpiVpa.VpaStatus status;
    private Boolean isPrimary;
    private LocalDateTime createdAt;

    public static VpaResponse from(UpiVpa vpa)
    {
        return VpaResponse.builder()
                .id(vpa.getId())
                .vpa(vpa.getVpa())
                .bankName(vpa.getBankName())
                .accountLast4(vpa.getAccountLast4())
                .status(vpa.getStatus())
                .isPrimary(vpa.getIsPrimary())
                .createdAt(vpa.getCreatedAt())
                .build();
    }
}