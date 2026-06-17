package com.suntek.apiconnector.auth.profile;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GaodeTrafficHmacAuthProviderTest {

    private final GaodeTrafficHmacAuthProvider provider = new GaodeTrafficHmacAuthProvider();

    @Test
    void getRequestAddsDigestFromSortedValues() {
        Map<String, String> credentials = Map.of(
                "publicKey", "client-key-1",
                "appSecret", "secret-1");
        Map<String, Object> auth = Map.of("type", "gaode_traffic_hmac_v1");
        Map<String, String> query = new HashMap<>();
        query.put("adcode", "440100");

        AuthContext context = new AuthContext(
                "GAODE_TRAFFIC",
                "https://et-api.amap.com",
                "GET",
                "/index/roadRanking",
                query,
                null,
                auth,
                credentials,
                Map.of());

        AuthOutcome outcome = provider.apply(context);

        assertEquals("client-key-1", outcome.query().get("clientKey"));
        assertNotNull(outcome.query().get("timestamp"));
        assertNotNull(outcome.query().get("digest"));
        assertEquals("440100", outcome.query().get("adcode"));
    }

    @Test
    void postRequestUsesClientKeyPlusTimestampForDigest() {
        Map<String, String> credentials = Map.of(
                "publicKey", "ck",
                "appSecret", "sec");
        AuthContext context = new AuthContext(
                "GAODE_TRAFFIC",
                "https://et-api.amap.com",
                "POST",
                "/congestion/realtime",
                Map.of(),
                "{}",
                Map.of("type", "gaode_traffic_hmac_v1"),
                credentials,
                Map.of());

        AuthOutcome outcome = provider.apply(context);

        String timestamp = outcome.query().get("timestamp");
        assertNotNull(timestamp);
        assertEquals(hmacHex("sec", "ck" + timestamp), outcome.query().get("digest"));
    }

    private static String hmacHex(String secret, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
