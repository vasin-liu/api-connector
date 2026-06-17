/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.catalog;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointDocSpec;
import com.suntek.apiconnector.spec.model.EndpointParamSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 {@link CatalogConnector} 接口编译为 {@link ConnectorSpec}。
 */
public final class CatalogConnectorScanner {

    private CatalogConnectorScanner() {
    }

    public static ConnectorSpec scan(Class<?> catalogInterface) {
        if (!catalogInterface.isInterface()) {
            throw new IllegalArgumentException("Catalog must be an interface: " + catalogInterface.getName());
        }
        CatalogConnector connector = catalogInterface.getAnnotation(CatalogConnector.class);
        if (connector == null) {
            throw new IllegalArgumentException("Missing @CatalogConnector on " + catalogInterface.getName());
        }
        List<EndpointSpec> endpoints = new ArrayList<>();
        for (Method method : catalogInterface.getDeclaredMethods()) {
            if (method.isSynthetic() || method.getDeclaringClass() == Object.class) {
                continue;
            }
            EndpointSpec endpoint = toEndpoint(method);
            if (endpoint != null) {
                endpoints.add(endpoint);
            }
        }
        return new ConnectorSpec(
                connector.code3rd(),
                connector.version(),
                connector.baseUrl(),
                connector.protocol(),
                authMap(catalogInterface.getAnnotation(CatalogAuth.class)),
                endpoints,
                responseSpec(catalogInterface.getAnnotation(CatalogResponse.class)),
                Map.of(),
                null,
                List.of());
    }

    public static List<ConnectorSpec> scanAll(Class<?>... catalogs) {
        return Arrays.stream(catalogs).map(CatalogConnectorScanner::scan).toList();
    }

    private static EndpointSpec toEndpoint(Method method) {
        HttpMapping mapping = resolveMapping(method);
        if (mapping == null) {
            return null;
        }
        CatalogEndpoint meta = method.getAnnotation(CatalogEndpoint.class);
        String id = meta != null && !meta.id().isBlank() ? meta.id() : method.getName();
        EndpointDocSpec doc = buildDoc(method, meta, mapping.path());
        return new EndpointSpec(id, mapping.method(), mapping.path(), null, true, doc);
    }

    private static EndpointDocSpec buildDoc(Method method, CatalogEndpoint meta, String path) {
        String summary = meta != null && !meta.summary().isBlank()
                ? meta.summary()
                : EndpointDocumentation.humanizeId(method.getName());
        String description = meta != null ? nullToEmpty(meta.description()) : "";
        String group = meta != null && !meta.group().isBlank()
                ? meta.group()
                : EndpointDocumentation.inferGroupFromPath(path);
        List<EndpointParamSpec> parameters = queryParams(method, path);
        if (description.isBlank()) {
            description = null;
        }
        return new EndpointDocSpec(summary, description, group, parameters);
    }

    private static List<EndpointParamSpec> queryParams(Method method, String path) {
        List<EndpointParamSpec> params = new ArrayList<>(EndpointDocumentation.inferPathParams(path));
        QueryNames queryNames = method.getAnnotation(QueryNames.class);
        if (queryNames != null) {
            Set<String> required = Arrays.stream(queryNames.required()).collect(Collectors.toSet());
            for (String name : queryNames.value()) {
                params.add(new EndpointParamSpec(name, null, required.contains(name), "", "query"));
            }
        }
        return params;
    }

    private static HttpMapping resolveMapping(Method method) {
        HttpGet get = method.getAnnotation(HttpGet.class);
        if (get != null) {
            return new HttpMapping("GET", get.value());
        }
        HttpPost post = method.getAnnotation(HttpPost.class);
        if (post != null) {
            return new HttpMapping("POST", post.value());
        }
        return null;
    }

    private static Map<String, Object> authMap(CatalogAuth auth) {
        if (auth == null) {
            return Map.of("type", "none");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", auth.type());
        putIfPresent(map, "accessKeyRef", auth.accessKeyRef());
        putIfPresent(map, "secretKeyRef", auth.secretKeyRef());
        putIfPresent(map, "keyRef", auth.keyRef());
        putIfPresent(map, "paramName", auth.paramName());
        putIfPresent(map, "clientKeyRef", auth.clientKeyRef());
        putIfPresent(map, "tokenUrl", auth.tokenUrl());
        putIfPresent(map, "clientIdRef", auth.clientIdRef());
        putIfPresent(map, "clientSecretRef", auth.clientSecretRef());
        putIfPresent(map, "tokenParam", auth.tokenParam());
        putIfPresent(map, "script", auth.script());
        return map;
    }

    private static ResponseSpec responseSpec(CatalogResponse response) {
        if (response == null) {
            return null;
        }
        if (response.successWhen().isBlank()
                && response.dataPath().isBlank()
                && response.messagePath().isBlank()
                && response.codePath().isBlank()) {
            return null;
        }
        return new ResponseSpec(
                nullToNull(response.successWhen()),
                nullToNull(response.dataPath()),
                nullToNull(response.messagePath()),
                nullToNull(response.codePath()));
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record HttpMapping(String method, String path) {
    }
}
