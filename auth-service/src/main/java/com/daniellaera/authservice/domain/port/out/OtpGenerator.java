package com.daniellaera.authservice.domain.port.out;

import com.daniellaera.authservice.domain.model.OtpCode;
public interface OtpGenerator {
    OtpCode generate();
}
