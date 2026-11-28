package io.github.abdurazaaqmohammed.feature.files.network;

import com.hierynomus.asn1.types.primitive.ASN1ObjectIdentifier;
import com.hierynomus.ntlm.messages.NtlmAuthenticate;
import com.hierynomus.ntlm.messages.NtlmChallenge;
import com.hierynomus.ntlm.messages.NtlmNegotiate;
import com.hierynomus.ntlm.messages.NtlmNegotiateFlag;
import com.hierynomus.protocol.commons.buffer.Buffer;
import com.hierynomus.protocol.commons.buffer.Endian;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticateResponse;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.auth.Authenticator;
import com.hierynomus.smbj.connection.ConnectionContext;
import com.hierynomus.spnego.NegTokenInit;
import com.hierynomus.spnego.NegTokenTarg;
import com.hierynomus.spnego.SpnegoException;

import java.io.IOException;
import java.util.EnumSet;

/** NTLM anonymous authentication does not need the 128-bit session-key capability. */
final class AnonymousNtlmAuthenticator implements Authenticator {
    private static final ASN1ObjectIdentifier NTLMSSP = new ASN1ObjectIdentifier("1.3.6.1.4.1.311.2.2.10");

    private enum State { NEGOTIATE, AUTHENTICATE, COMPLETE }

    private SmbConfig config;
    private State state;
    private EnumSet<NtlmNegotiateFlag> negotiateFlags;

    static final class Factory implements com.hierynomus.protocol.commons.Factory.Named<Authenticator> {
        @Override public String getName() { return NTLMSSP.getValue(); }
        @Override public Authenticator create() { return new AnonymousNtlmAuthenticator(); }
    }

    @Override public void init(SmbConfig config) {
        this.config = config;
        state = State.NEGOTIATE;
        negotiateFlags = EnumSet.noneOf(NtlmNegotiateFlag.class);
    }

    @Override public boolean supports(AuthenticationContext context) {
        return context.getClass().equals(AuthenticationContext.class) && context.isAnonymous();
    }

    @Override public AuthenticateResponse authenticate(AuthenticationContext context, byte[] gssToken,
                                                         ConnectionContext connectionContext) throws IOException {
        if (state == State.COMPLETE) return null;
        if (state == State.NEGOTIATE) return negotiate(context);
        return authenticateAnonymous(context, gssToken);
    }

    private AuthenticateResponse negotiate(AuthenticationContext context) throws IOException {
        com.hierynomus.ntlm.NtlmConfig ntlm = config.getNtlmConfig();
        negotiateFlags = EnumSet.of(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_ANONYMOUS,
                NtlmNegotiateFlag.NTLMSSP_REQUEST_TARGET,
                NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY);
        if (!ntlm.isOmitVersion() && ntlm.getWindowsVersion() != null)
            negotiateFlags.add(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_VERSION);
        if (!negotiateFlags.contains(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_VERSION)) {
            if (hasText(context.getDomain())) negotiateFlags.add(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_OEM_DOMAIN_SUPPLIED);
            if (hasText(ntlm.getWorkstationName()))
                negotiateFlags.add(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_OEM_WORKSTATION_SUPPLIED);
        }

        NtlmNegotiate negotiate = new NtlmNegotiate(negotiateFlags, context.getDomain(), ntlm.getWorkstationName(),
                ntlm.getWindowsVersion(), ntlm.isOmitVersion());
        Buffer.PlainBuffer message = new Buffer.PlainBuffer(Endian.LE);
        negotiate.write(message);

        NegTokenInit token = new NegTokenInit();
        token.addSupportedMech(NTLMSSP);
        token.setMechToken(message.getCompactData());
        AuthenticateResponse response = new AuthenticateResponse(token);
        response.setNegotiateFlags(negotiateFlags);
        state = State.AUTHENTICATE;
        return response;
    }

    private AuthenticateResponse authenticateAnonymous(AuthenticationContext context, byte[] gssToken) throws IOException {
        final NtlmChallenge challenge;
        try {
            NegTokenTarg token = new NegTokenTarg().read(gssToken);
            challenge = new NtlmChallenge();
            challenge.read(new Buffer.PlainBuffer(token.getResponseToken(), Endian.LE));
        } catch (SpnegoException | Buffer.BufferException e) {
            throw new IOException("Invalid SMB anonymous authentication challenge", e);
        }

        negotiateFlags.removeIf(flag -> !challenge.getNegotiateFlags().contains(flag));
        com.hierynomus.ntlm.NtlmConfig ntlm = config.getNtlmConfig();
        NtlmAuthenticate message = new NtlmAuthenticate(null, null, context.getUsername(), context.getDomain(),
                ntlm.getWorkstationName(), null, negotiateFlags, ntlm.getWindowsVersion());
        Buffer.PlainBuffer messageBuffer = new Buffer.PlainBuffer(Endian.LE);
        message.write(messageBuffer);

        NegTokenTarg responseToken = new NegTokenTarg();
        responseToken.setResponseToken(messageBuffer.getCompactData());
        AuthenticateResponse response = new AuthenticateResponse(responseToken);
        response.setWindowsVersion(challenge.getVersion());
        response.setNegotiateFlags(negotiateFlags);
        state = State.COMPLETE;
        return response;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
