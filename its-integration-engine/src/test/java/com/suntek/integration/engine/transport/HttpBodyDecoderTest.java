package com.suntek.integration.engine.transport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpBodyDecoderTest {

    @Test
    void decodesGzipJson() throws Exception {
        String json = "{\"success\":true}";
        byte[] gzip = gzip(json.getBytes(StandardCharsets.UTF_8));
        HttpBodyDecoder.DecodedBody decoded = HttpBodyDecoder.decode(
                gzip, Map.of("Content-Encoding", "gzip", "Content-Type", "application/json"));
        assertEquals(json, decoded.text());
        assertNull(decoded.encoding());
    }

    @Test
    void binaryContentTypeUsesBase64() {
        byte[] bytes = new byte[] {0x00, 0x01, 0x02};
        HttpBodyDecoder.DecodedBody decoded = HttpBodyDecoder.decode(
                bytes, Map.of("Content-Type", "application/octet-stream"));
        assertEquals("base64", decoded.encoding());
    }

    private static byte[] gzip(byte[] input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(input);
        }
        return out.toByteArray();
    }
}
