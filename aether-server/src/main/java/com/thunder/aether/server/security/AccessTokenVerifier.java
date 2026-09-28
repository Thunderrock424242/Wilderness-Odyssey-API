package com.thunder.aether.server.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

/** Verifies Cloudflare's signature and application/service identity at the origin, independent of proxy headers. */
public final class AccessTokenVerifier implements AutoCloseable {
    private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    private final JWKSource<SecurityContext> keys;

    /** Keys are fetched only from the operator's pinned issuer with bounded transport and cached/rate-limited refresh. */
    public AccessTokenVerifier(String issuer, String audience) throws Exception {
        this(issuer, audience, JWKSourceBuilder.<SecurityContext>create(
                URI.create(issuer + "/cdn-cgi/access/certs").toURL(),
                new DefaultResourceRetriever(2000, 2000, 262144)).rateLimited(30_000L).build());
    }

    /** Composes the verifier with a trusted key source; token-provided URLs are never used. */
    public AccessTokenVerifier(String issuer, String audience, JWKSource<SecurityContext> keys) {
        this.keys = keys;
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        DefaultJWTClaimsVerifier<SecurityContext> claims = new DefaultJWTClaimsVerifier<>(Set.of(audience),
                new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of("exp", "iat", "common_name"), Set.of());
        claims.setMaxClockSkew(0);
        processor.setJWTClaimsSetVerifier(claims);
    }

    /** Missing, forged, expired, wrong-audience and wrong-service assertions all fail closed. */
    public boolean accepts(String assertion, String serviceId, Instant now) {
        if (assertion == null || assertion.length() > 8192 || serviceId == null || serviceId.isBlank()) { return false; }
        try {
            JWTClaimsSet claims = processor.process(assertion, null);
            return serviceId.equals(claims.getStringClaim("common_name"))
                    && claims.getIssueTime() != null && claims.getExpirationTime() != null
                    && !claims.getIssueTime().toInstant().isAfter(now.plusSeconds(30))
                    && now.isBefore(claims.getExpirationTime().toInstant())
                    && claims.getIssueTime().before(claims.getExpirationTime());
        } catch (Exception invalid) {
            // Authentication failures are categorical; never log signed assertions or parser details.
            return false;
        }
    }

    @Override public void close() {
        if (keys instanceof java.io.Closeable resource) {
            try { resource.close(); } catch (java.io.IOException ignored) { /* No authority remains after close. */ }
        }
    }
}
