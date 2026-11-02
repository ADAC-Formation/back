package com.adac.portail.service;

import com.adac.portail.dto.request.ActivateAccountRequest;
import com.adac.portail.dto.request.ResetPasswordRequest;
import com.adac.portail.entity.User;
import com.adac.portail.exception.ActivationTokenExpiredException;
import com.adac.portail.exception.ActivationTokenInvalidException;
import com.adac.portail.exception.RateLimitException;

public interface ActivationService {

    void activate(ActivateAccountRequest request);

    void resendActivation(String email);

    void forgotPassword(String email);

    void resetPassword(ResetPasswordRequest request);

    void sendActivationCode(User user);

    boolean hasEverActivated(User user);
}
