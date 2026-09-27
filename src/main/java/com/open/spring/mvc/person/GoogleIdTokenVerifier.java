package com.open.spring.mvc.person;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.open.spring.mvc.person.HttpRequest.HttpSender;

import io.github.cdimascio.dotenv.Dotenv;

// Verifies a Google Identity Services ID token server-side via Google's tokeninfo
// endpoint, so callers never trust an email a client merely claims to have signed in with.
public class GoogleIdTokenVerifier {
    private static final Logger logger = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    // Same public client ID hardcoded in navigation/authentication/login.md's GOOGLE_CLIENT_ID.
    // Client IDs are not secret; this is only used to check the token's "aud" claim.
    private static final String DEFAULT_CLIENT_ID = "65827797404-ccjleg7jg4g2an8ddpmhnlca4ii2gk8q.apps.googleusercontent.com";

    private static String resolveClientId() {
        String value = System.getenv("GOOGLE_CLIENT_ID");
        if (value != null && !value.isBlank()) {
            return value;
        }
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            value = dotenv.get("GOOGLE_CLIENT_ID");
            if (value != null && !value.isBlank()) {
                return value;
            }
        } catch (Exception e) {
            // fall through to default
        }
        return DEFAULT_CLIENT_ID;
    }

    // Returns the verified email address, or null if the token is missing, expired,
    // mis-signed, issued for a different client, or not marked email_verified by Google.
    public static String verifyAndGetEmail(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return null;
        }

        try {
            String encoded = URLEncoder.encode(idToken, StandardCharsets.UTF_8);
            Map<String, String> response = HttpSender.sendRequest(
                "https://oauth2.googleapis.com/tokeninfo?id_token=" + encoded,
                "GET",
                new HashMap<>()
            );

            if (!"200".equals(response.get("responseCode"))) {
                logger.warn("AUDIT google_token_verify_failed reason=non_200_response code={}", response.get("responseCode"));
                return null;
            }

            JSONObject claims = new JSONObject(response.get("content"));
            String aud = claims.optString("aud", null);
            String issuer = claims.optString("iss", null);
            boolean emailVerified = "true".equals(claims.optString("email_verified", null));
            String email = claims.optString("email", null);

            boolean issuerOk = "accounts.google.com".equals(issuer) || "https://accounts.google.com".equals(issuer);
            boolean audOk = aud != null && aud.equals(resolveClientId());

            if (!audOk || !issuerOk || !emailVerified || email == null || email.isBlank()) {
                logger.warn("AUDIT google_token_verify_failed reason=claim_check_failed");
                return null;
            }

            return email;
        } catch (Exception e) {
            logger.warn("AUDIT google_token_verify_failed reason=exception msg={}", e.getMessage());
            return null;
        }
    }
}
