/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 旧 system-thirdpart URL 前缀 → code3rd 映射。
 */
@ConfigurationProperties(prefix = "integration.legacy")
public class IntegrationLegacyProperties {

    private boolean enabled = true;
    private List<RouteMapping> routes = defaultRoutes();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<RouteMapping> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteMapping> routes) {
        this.routes = routes != null ? routes : new ArrayList<>();
    }

    private static List<RouteMapping> defaultRoutes() {
        return List.of(
                route("/idps", "IDPS", LegacyResponseStyle.VENDOR_RAW),
                route("/gaode/traffic", "GAODE_TRAFFIC", LegacyResponseStyle.SUNTEK_RESULT),
                route("/gaode", "GAODE_OPEN_PLATFORM", LegacyResponseStyle.SUNTEK_RESULT),
                route("/baiduJiaotong", "BAIDU_MAP", LegacyResponseStyle.SUNTEK_RESULT),
                route("/BaiduGpt", "BAIDU_WENXIN", LegacyResponseStyle.SUNTEK_RESULT));
    }

    private static RouteMapping route(String prefix, String code3rd, LegacyResponseStyle style) {
        RouteMapping mapping = new RouteMapping();
        mapping.setPathPrefix(prefix);
        mapping.setCode3rd(code3rd);
        mapping.setResponseStyle(style);
        return mapping;
    }

    public static class RouteMapping {
        private String pathPrefix;
        private String code3rd;
        private LegacyResponseStyle responseStyle = LegacyResponseStyle.VENDOR_RAW;

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        public String getCode3rd() {
            return code3rd;
        }

        public void setCode3rd(String code3rd) {
            this.code3rd = code3rd;
        }

        public LegacyResponseStyle getResponseStyle() {
            return responseStyle;
        }

        public void setResponseStyle(LegacyResponseStyle responseStyle) {
            this.responseStyle = responseStyle;
        }
    }
}
