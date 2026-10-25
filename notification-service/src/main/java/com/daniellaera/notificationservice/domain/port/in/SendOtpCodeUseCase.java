package com.daniellaera.notificationservice.domain.port.in;

import com.daniellaera.notificationservice.domain.model.OtpNotification;

/** Emails a verification code. Never throws. */
public interface SendOtpCodeUseCase {

    void sendOtpCode(OtpNotification notification);
}
