/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.flow;

import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.core.value.SecretValue;
import com.suntek.apiconnector.runtime.compile.DefinitionNormalizer;
import com.suntek.apiconnector.runtime.plan.CompiledRequest;
import com.suntek.apiconnector.runtime.plan.NamedBinding;
import com.suntek.apiconnector.runtime.plan.SecretSink;
import com.suntek.apiconnector.runtime.plan.UrlTemplate;
import com.suntek.apiconnector.runtime.secret.SecretSinkPolicy;
import com.suntek.apiconnector.runtime.secret.SinkDestination;
import com.suntek.apiconnector.runtime.session.CookieStore;
import com.suntek.apiconnector.runtime.state.VariableRuntime;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;
import com.suntek.apiconnector.transport.RawHttpRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Renders a compiled request template with the current variables and session. Never clones a prior RawHttpRequest.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class RequestRenderer {

    private RequestRenderer() {
    }

    /**
     * @param request compiled template
     * @param variables current variables
     * @param cookies   session cookie store, or null
     * @param body      optional rebuilt body bytes
     * @return new outbound request
     */
    public static RawHttpRequest render(
            CompiledRequest request,
            VariableRuntime variables,
            CookieStore cookies,
            Optional<byte[]> body
    ) {
        return renderInternal(request, variables, cookies, body, null, null);
    }

    /**
     * @param request compiled template
     * @param variables current variables
     * @param cookies   session cookie store, or null
     * @param body      optional rebuilt body bytes
     * @param sinks     sink policy
     * @param destination target api
     * @return new outbound request
     */
    public static RawHttpRequest render(
            CompiledRequest request,
            VariableRuntime variables,
            CookieStore cookies,
            Optional<byte[]> body,
            SecretSinkPolicy sinks,
            SinkDestination destination
    ) {
        return renderInternal(request, variables, cookies, body, sinks, destination);
    }

    /**
     * @param request compiled template
     * @param variables current variables
     * @param cookies   session cookie store, or null
     * @return new outbound request
     */
    public static RawHttpRequest render(CompiledRequest request, VariableRuntime variables, CookieStore cookies) {
        return renderInternal(request, variables, cookies, Optional.empty(), null, null);
    }

    private static RawHttpRequest renderInternal(
            CompiledRequest request,
            VariableRuntime variables,
            CookieStore cookies,
            Optional<byte[]> body,
            SecretSinkPolicy sinks,
            SinkDestination destination
    ) {
        String url = renderUrl(request.url(), variables);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (NamedBinding header : request.headers()) {
            if (!whenAllows(header, variables)) {
                continue;
            }
            Optional<String> value = headerValue(header, variables, sinks, destination);
            value.filter(v -> !v.isBlank()).ifPresent(v -> headers.put(header.name(), List.of(v)));
        }
        if (request.cookies().filter("fromStore"::equals).isPresent() && cookies != null) {
            String cookieHeader = cookies.cookiesFor(URI.create(url));
            if (!cookieHeader.isBlank()) {
                headers.put("Cookie", List.of(cookieHeader));
            }
        }
        String withQuery = appendQuery(url, request.query(), variables, sinks, destination);
        return new RawHttpRequest(request.method(), URI.create(withQuery), headers, body);
    }

    private static String renderUrl(UrlTemplate template, VariableRuntime variables) {
        StringBuilder url = new StringBuilder();
        for (UrlTemplate.UrlPart part : template.parts()) {
            switch (part) {
                case UrlTemplate.UrlPart.Literal(String value) -> url.append(value);
                case UrlTemplate.UrlPart.VarRef(VariableScope scope, String name) ->
                        url.append(stringify(variables.get(scope, name).orElse(new DataValue.NullValue())));
            }
        }
        return url.toString();
    }

    private static String appendQuery(
            String url,
            List<NamedBinding> query,
            VariableRuntime variables,
            SecretSinkPolicy sinks,
            SinkDestination destination
    ) {
        if (query == null || query.isEmpty()) {
            return url;
        }
        List<String> parts = new ArrayList<>();
        for (NamedBinding binding : query) {
            headerValue(binding, variables, sinks, destination).filter(v -> !v.isBlank()).ifPresent(v ->
                    parts.add(binding.name() + "=" + v)
            );
        }
        if (parts.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + String.join("&", parts);
    }

    private static boolean whenAllows(NamedBinding binding, VariableRuntime variables) {
        Map<String, Object> extras = binding.extras() == null ? Map.of() : binding.extras();
        String when = YamlMaps.stringOrNull(extras.get("when"));
        if (when == null || when.isBlank()) {
            return true;
        }
        String rendered = interpolate(when, variables);
        return "true".equalsIgnoreCase(rendered) || "1".equals(rendered);
    }

    private static Optional<String> headerValue(
            NamedBinding binding,
            VariableRuntime variables,
            SecretSinkPolicy sinks,
            SinkDestination destination
    ) {
        Map<String, Object> extras = binding.extras() == null ? Map.of() : binding.extras();
        String secretVar = YamlMaps.stringOrNull(extras.get("secretVar"));
        if (secretVar != null) {
            Optional<DataValue> resolved = resolvePath(secretVar, variables);
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            if (resolved.get() instanceof SecretValue secret && sinks != null && destination != null) {
                SecretSink sink = binding.sink().orElse(SecretSink.AUTHORIZATION);
                sinks.check(secret, sink, destination);
            }
            String prefix = extras.get("prefix") == null ? "" : String.valueOf(extras.get("prefix"));
            return stringifyOptional(resolved.get()).map(prefix::concat);
        }
        String template = YamlMaps.stringOrNull(extras.get("template"));
        if (template != null) {
            return Optional.of(interpolate(template, variables));
        }
        if (binding.literal().isPresent()) {
            return Optional.of(interpolate(String.valueOf(binding.literal().get()), variables));
        }
        return Optional.empty();
    }

    private static Optional<DataValue> resolvePath(String path, VariableRuntime variables) {
        int dot = path.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        VariableScope scope = VariableScope.valueOf(path.substring(0, dot).toUpperCase());
        return variables.get(scope, path.substring(dot + 1));
    }

    private static String interpolate(String template, VariableRuntime variables) {
        List<Map<String, Object>> parts = DefinitionNormalizer.parseTemplate(template);
        if (parts.isEmpty()) {
            return template;
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> part : parts) {
            if ("VAR".equals(part.get("kind"))) {
                VariableScope scope = VariableScope.valueOf(String.valueOf(part.get("scope")));
                String name = String.valueOf(part.get("name"));
                out.append(stringify(variables.get(scope, name).orElse(new DataValue.NullValue())));
            } else {
                out.append(part.get("value"));
            }
        }
        return out.toString();
    }

    private static Optional<String> stringifyOptional(DataValue value) {
        String text = stringify(value);
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    static String stringify(DataValue value) {
        return switch (value) {
            case DataValue.StringValue(String v) -> v;
            case DataValue.NumberValue(Number n) -> String.valueOf(n);
            case DataValue.BooleanValue(boolean b) -> String.valueOf(b);
            case SecretValue secret -> {
                String[] holder = new String[1];
                secret.use(bytes -> holder[0] = new String(bytes, StandardCharsets.UTF_8));
                yield holder[0] == null ? "" : holder[0];
            }
            default -> "";
        };
    }
}
