/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.api.ExecuteException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RsaPkcs1CipherTest {

    public static final String S2 = "cb2cb0b9f11b040b2a169e768d266db8";
    public static final String SPKI =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxHBkVG6VHkLn9MZyY+/2agpxbCtJpyCo9K1tVVNXLGDS1Wtx6UyR66nsCUyJiC4pYxu4JDWm4/LsccBhupazJbIA78NV2tO1201dfJaRBfYekyQHjDONEKku6ns4c8pN4qIdUU/GwKlffWA2T3L3znAvJWozMXTy+ojiTNTrgWZDLGPr2Tg/AH6NiGGjFpQIaF/iozoU09YqNDz1/LHnPgtJsFOYI3eswvB0J6CCudjWA5jTxQ1khSqvlO5ZRI5EUogkgmb5FedMT9EJQwu7L5c99jGo2yvPwVKNVYe1Q9YslRZEpLD4W4VQNICNPt3JojQ+wMqhpWKu1uCNHZTquQIDAQAB";
    public static final String PKCS8 =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDEcGRUbpUeQuf0xnJj7/ZqCnFsK0mnIKj0rW1VU1csYNLVa3HpTJHrqewJTImILiljG7gkNabj8uxxwGG6lrMlsgDvw1Xa07XbTV18lpEF9h6TJAeMM40QqS7qezhzyk3ioh1RT8bAqV99YDZPcvfOcC8lajMxdPL6iOJM1OuBZkMsY+vZOD8Afo2IYaMWlAhoX+KjOhTT1io0PPX8sec+C0mwU5gjd6zC8HQnoIK52NYDmNPFDWSFKq+U7llEjkRSiCSCZvkV50xP0QlDC7svlz32MajbK8/BUo1Vh7VD1iyVFkSksPhbhVA0gI0+3cmiND7AyqGlYq7W4I0dlOq5AgMBAAECggEAQW+LDFEV3VG+QNyGKGI7/FDkPXsh+sk3XuIShJTRehf6w1saQ/Pcu9sLpIFZYwK1iK0oJu+0W0SFnNLP+z7OCGWGSFgSVmWls5rO5HynbIvy3WLoHLzjZcRlu3g2cuPWFAOaroZbl7wT8gjFrquGfr8CBX2d9gCWG/zTHXLjzGN135ldMrxboDGp3iG2njHBOdX0/OJGAk8aj5n2xHx5CtTr27lCnp1s+Pxhzmjr+2BE1G+2afaem++V2gMTAgnWleuterL1q/UOQkseqlN+lxU7byhHlollnFNv/M+LIJnEDtTV9MvzSQCw78z69fOoJawuxKyPGYqqD4rKxi3XPQKBgQDhIlm8xCdNN+aZLZwdthZRn/FG5Rn7KRKYIRDB2eTk/oUlmDDpnHdAtjhXL6HcpLvWsWTE16aKDUJQT0eiFhr32NKveCwXZ6Mfqk4yZgO1IPjEuUaBlNkQHSTn11wXt7jjy2PyQSxNDn9JhdiRbpYS635MdsevmEOewEzexQbJewKBgQDfXum6PSQB5qUiYh4C/d4Hs0CAhwgP82JuJgXLk+NIZ2TyctyQuF6UGjZQylLLCm1zN430ee5bguqjSki3VSNCKLMPcAiqIi92hN84gRgaAjztTT7BFLV1x5pz1BV0ArSGNzkJp2daIYfKqRlMkAwSZh9rrtTl/stdOOnj4KIkWwKBgDyDzzs+PpIzBHkIGX6jAzzNkT1NN76bO7RIrwhsWWp0P7XrQrqIpEVwtZQ56Xg1j4gWBtAiZ1GUXs7XgJnA917HmVoxIyjnYqGQVZSWd0iopDW814bdS7tZGiJ3cPmvGLOiaCsl+ZJ84xoQMDZnU0+inUBQbTx14Tc62W7H5xshAoGAGg2b/Vj8Rix8vlHrZjXuG7/Qmi0lx5Z/uJE+w8vwCpXktFKpX0fh4CxegXXXyk7hDTtBl25RWuMeMwWLDbckcacFm5tZp15tYsmEr7Z+9SmlqZgE5chgkehCH5hxGCLoo271fCbGa0DV8dFfXgq/HbZDm++8bUcWuTzWKvocb1kCgYEAkFxsYjhpIYpWX6uO9xK5Hu40Ra8mYd28OA2xjh2e4tRRmIAngnDkCTnb82tI+OQwbH1ze5mDbGvLu3DQ6JwBVDNToVphkFBtRFO1g239nZ1X0RNbSJ8ns1mK2JvAxrkCEDFunlxnTqV0FRJE05bPGwaH8DwGq2MvX3xg2IDA3+Q=";

    private static final byte[] PLAIN = S2.getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY = SPKI.getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptDecryptsBackToS2() throws Exception {
        String first = RsaPkcs1Cipher.encode(KEY, PLAIN, CipherEncoding.BASE64);
        String second = RsaPkcs1Cipher.encode(KEY, PLAIN, null);
        assertThat(decrypt(first)).isEqualTo(S2);
        assertThat(decrypt(second)).isEqualTo(S2);
    }

    @Test
    void hexEncodingIsRejected() {
        assertThatThrownBy(() -> CipherEncoding.parse("hex"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown cipher encoding");
    }

    @Test
    void invalidSpkiFailsWithoutKeyInMessage() {
        String bogus = "not-a-valid-spki-key-material-XXXX";
        byte[] badKey = bogus.getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RsaPkcs1Cipher.encode(badKey, PLAIN, CipherEncoding.BASE64))
                .isInstanceOf(ExecuteException.class)
                .satisfies(ex -> {
                    assertThat(((ExecuteException) ex).code()).isEqualTo(RsaPkcs1Cipher.RSA_KEY_INVALID);
                    assertThat(ex.getMessage()).doesNotContain(bogus);
                    assertThat(ex.getMessage()).doesNotContain(SPKI);
                });
    }

    public static String decrypt(String ciphertextBase64) throws Exception {
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PKCS8)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64)), StandardCharsets.UTF_8);
    }
}
