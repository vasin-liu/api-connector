/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.catalog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CatalogAuth {

    String type();

    String accessKeyRef() default "";

    String secretKeyRef() default "";

    String keyRef() default "";

    String paramName() default "";

    String clientKeyRef() default "";

    String tokenUrl() default "";

    String clientIdRef() default "";

    String clientSecretRef() default "";

    String tokenParam() default "";

    String script() default "";
}
