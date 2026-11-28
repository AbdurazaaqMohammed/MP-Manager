package io.github.abdurazaaqmohammed.feature.files.network;

import com.hierynomus.ntlm.messages.NtlmNegotiateFlag;
import com.hierynomus.protocol.commons.Factory;
import com.hierynomus.protocol.commons.buffer.Buffer;
import com.hierynomus.protocol.commons.buffer.Endian;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticateResponse;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.auth.Authenticator;
import com.hierynomus.smbj.auth.NtlmAuthenticator;
import com.hierynomus.spnego.NegTokenTarg;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AnonymousNtlmAuthenticatorTest {
    @Test public void anonymousHandshakeAcceptsChallengeWithout128BitFlag() throws Exception {
        AnonymousNtlmAuthenticator authenticator = new AnonymousNtlmAuthenticator();
        authenticator.init(SmbConfig.createDefaultConfig());
        AuthenticationContext anonymous = AuthenticationContext.anonymous();

        AuthenticateResponse negotiate = authenticator.authenticate(anonymous, new byte[0], null);
        assertFalse(negotiate.getNegotiateFlags().contains(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_128));

        NegTokenTarg serverToken = new NegTokenTarg();
        serverToken.setResponseToken(challengeWithout128BitFlag());
        Buffer.PlainBuffer spnego = new Buffer.PlainBuffer(Endian.LE);
        serverToken.write(spnego);

        AuthenticateResponse authenticate = authenticator.authenticate(anonymous, spnego.getCompactData(), null);
        assertNotNull(authenticate.getNegToken());
        assertNull(authenticate.getSessionKey());
        assertFalse(authenticate.getNegotiateFlags().contains(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_128));
    }

    @Test public void configKeepsStandardAuthenticatorForAuthenticatedSessions() {
        List<Factory.Named<Authenticator>> authenticators = SmbClient.createConfig().getSupportedAuthenticators();

        assertTrue(authenticators.get(0) instanceof AnonymousNtlmAuthenticator.Factory);
        assertTrue(authenticators.stream().anyMatch(factory -> factory instanceof NtlmAuthenticator.Factory));
        assertTrue(new AnonymousNtlmAuthenticator().supports(AuthenticationContext.anonymous()));
        assertFalse(new AnonymousNtlmAuthenticator().supports(
                new AuthenticationContext("user", "password".toCharArray(), "domain")));
    }

    private static byte[] challengeWithout128BitFlag() {
        // Fixed header: signature(8) type(4) targetName fields(8) flags(4) serverChallenge(8)
        // targetInfo fields(8) version(8) = 56 bytes.
        byte[] challenge = new byte[56];
        byte[] signature = "NTLMSSP\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, challenge, 0, signature.length);
        putIntLittleEndian(challenge, 8, 2); // NTLM CHALLENGE message
        putIntLittleEndian(challenge, 16, challenge.length); // empty target name at end of fixed header
        int flags = (int) (NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_ANONYMOUS.getValue()
                | NtlmNegotiateFlag.NTLMSSP_REQUEST_TARGET.getValue()
                | NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY.getValue());
        putIntLittleEndian(challenge, 20, flags);
        putIntLittleEndian(challenge, 44, challenge.length); // empty target info at end of fixed header
        return challenge;
    }

    private static void putIntLittleEndian(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }
}
