package com.thunder.aether.server.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokenVerifierTest {
    @Test void verifiesSignatureIssuerAudienceServiceAndTimeUsingRealSignedTokens() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("trusted").generate();
        RSAKey other = new RSAKeyGenerator(2048).keyID("trusted").generate();
        Instant now = Instant.now();
        try (var verifier = new AccessTokenVerifier("https://team.cloudflareaccess.com", "gateway",
                new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())))) {
            assertTrue(verifier.accepts(token(key,"gateway","admin",now.minusSeconds(1),now.plusSeconds(60)),"admin",now));
            assertFalse(verifier.accepts(token(other,"gateway","admin",now.minusSeconds(1),now.plusSeconds(60)),"admin",now));
            assertFalse(verifier.accepts(token(key,"other","admin",now.minusSeconds(1),now.plusSeconds(60)),"admin",now));
            assertFalse(verifier.accepts(token(key,"gateway","monitor",now.minusSeconds(1),now.plusSeconds(60)),"admin",now));
            assertFalse(verifier.accepts(token(key,"gateway","admin",now.minusSeconds(60),now.minusSeconds(1)),"admin",now));
            assertFalse(verifier.accepts(token(key,"gateway","admin",now.plusSeconds(120),now.plusSeconds(180)),"admin",now));
            assertFalse(verifier.accepts("eyJhbGciOiJub25lIn0.e30.","admin",now));
        }
    }

    public static String token(RSAKey key, String audience, String service, Instant issued, Instant expires) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer("https://team.cloudflareaccess.com").audience(audience)
                        .claim("common_name",service).issueTime(Date.from(issued)).expirationTime(Date.from(expires)).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
