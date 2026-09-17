/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.http;

import java.nio.channels.ReadableByteChannel;
import java.util.Optional;

/**
 * Vendor response body. Stream bodies skip challenge classification.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public sealed interface ResponseBody permits ResponseBody.BytesBody, ResponseBody.StreamBody, ResponseBody.EmptyBody {

    record BytesBody(byte[] bytes, Optional<String> contentType) implements ResponseBody {}

    record StreamBody(ReadableByteChannel channel, Optional<String> contentType) implements ResponseBody {}

    record EmptyBody() implements ResponseBody {}
}
