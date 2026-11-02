package com.equity.user.exception;

/**
 * Thrown when an email-verification request cannot be honoured.
 *
 * <p>Covers every "the request was understood but cannot proceed" case in the flow: the account
 * carries no email address, a verification link has expired or does not exist, or a resend was
 * asked for inside the cooldown window.
 *
 * <p>WHY one exception rather than four: every case maps to the same HTTP 400 and the message is
 * what actually differs. Four types would mean four handler methods that all do the same thing.
 * The message is written for the person reading it on screen, not for a developer.
 */
public class EmailVerificationException extends RuntimeException {

    public EmailVerificationException(String message) {
        super(message);
    }
}
