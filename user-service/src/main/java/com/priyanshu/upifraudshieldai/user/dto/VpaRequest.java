package com.priyanshu.upifraudshieldai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VpaRequest
{

    @NotBlank(message = "VPA is required")
    @Pattern(
            regexp  = "^[a-zA-Z0-9._-]+@[a-zA-Z]+$",
            message = "Invalid VPA format. Expected format: handle@bankcode (e.g. john@okaxis)"
    )
    private String vpa;

    @Size(max = 100)
    private String bankName;

    @Pattern(regexp = "^\\d{4}$", message = "Account last 4 digits must be exactly 4 digits")
    private String accountLast4;

    private boolean isPrimary = false;
}