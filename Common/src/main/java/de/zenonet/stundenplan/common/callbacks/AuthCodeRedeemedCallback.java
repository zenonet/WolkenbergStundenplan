package de.zenonet.stundenplan.common.callbacks;

public interface AuthCodeRedeemedCallback {
    void authCodeRedeemed();
    void errorOccurred(String message);
}
