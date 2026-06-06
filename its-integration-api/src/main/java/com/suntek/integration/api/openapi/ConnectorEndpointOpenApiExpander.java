/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.integration.engine.ConnectorRegistry;
import com.suntek.integration.spec.model.ConnectorSpec;
import com.suntek.integration.spec.model.EndpointSpec;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 Connector Spec 中每个 endpoint 展开为独立 OpenAPI 操作，提升厂家多 API 场景下的可读性。
 */
public class ConnectorEndpointOpenApiExpander implements OpenApiCustomizer {

    private final ConnectorRegistry registry;
    private final ObjectMapper objectMapper;

    public ConnectorEndpointOpenApiExpander(ConnectorRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Paths paths = openApi.getPaths();
        if (paths == null) {
            paths = new Paths();
            openApi.setPaths(paths);
        }
        List<Map<String, Object>> tagGroups = new ArrayList<>();
        tagGroups.add(Map.of(
                "name", "平台",
                "tags", List.of("Runtime · Invoke", "Admin · Connectors", "Admin · Profiles", "Admin · Operations")));

        for (ConnectorSpec spec : registry.listSpecs()) {
            registerConnectorTags(openApi, spec);
            List<String> vendorTags = new ArrayList<>();
            vendorTags.add(EndpointOpenApiMetadataResolver.connectorTag(spec.code3rd()));
            vendorTags.addAll(EndpointOpenApiMetadataResolver.collectSubgroupTags(spec));
            tagGroups.add(Map.of("name", spec.code3rd(), "tags", vendorTags));

            if (spec.endpoints() == null) {
                continue;
            }
            for (EndpointSpec endpoint : spec.endpoints()) {
                if (endpoint.enabled() != null && !endpoint.enabled()) {
                    continue;
                }
                paths.addPathItem(
                        EndpointOpenApiMetadataResolver.invokePath(spec.code3rd(), endpoint.id()),
                        buildPathItem(spec, endpoint));
            }
        }
        openApi.addExtension("x-tagGroups", tagGroups);
    }

    private void registerConnectorTags(OpenAPI openApi, ConnectorSpec spec) {
        openApi.addTagsItem(new Tag()
                .name(EndpointOpenApiMetadataResolver.connectorTag(spec.code3rd()))
                .description(buildConnectorTagDescription(spec)));
        for (String subgroup : EndpointOpenApiMetadataResolver.collectSubgroupTags(spec)) {
            openApi.addTagsItem(new Tag().name(subgroup).description(spec.code3rd() + " 厂家 API 分组"));
        }
    }

    private PathItem buildPathItem(ConnectorSpec spec, EndpointSpec endpoint) {
        Operation operation = new Operation();
        operation.setOperationId(EndpointOpenApiMetadataResolver.operationId(spec.code3rd(), endpoint.id()));
        operation.setSummary(EndpointOpenApiMetadataResolver.resolveSummary(endpoint));
        operation.setDescription(EndpointOpenApiMetadataResolver.resolveDescription(spec, endpoint));
        operation.addTagsItem(EndpointOpenApiMetadataResolver.connectorTag(spec.code3rd()));
        operation.addTagsItem(EndpointOpenApiMetadataResolver.subgroupTag(
                spec.code3rd(), EndpointOpenApiMetadataResolver.resolveGroup(endpoint)));
        operation.setRequestBody(buildRequestBody(endpoint));

        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", new ApiResponse()
                .description("调用完成（含厂家业务失败时 success=false）"));
        responses.addApiResponse("404", new ApiResponse().description("未知连接器或端点"));
        operation.setResponses(responses);

        PathItem pathItem = new PathItem();
        pathItem.post(operation);
        return pathItem;
    }

    private RequestBody buildRequestBody(EndpointSpec endpoint) {
        Schema<Object> schema = new Schema<>();
        schema.setType("object");
        schema.setDescription("仅需业务 query / headers / body；method 与厂家 path 由 Spec 决定。");

        Map<String, Object> example = EndpointOpenApiMetadataResolver.buildExampleRequest(endpoint);
        MediaType mediaType = new MediaType().schema(schema);
        if (!example.isEmpty()) {
            try {
                mediaType.setExample(objectMapper.writeValueAsString(example));
            } catch (Exception ignored) {
                mediaType.setExample(example);
            }
        }
        return new RequestBody()
                .required(false)
                .description(buildParameterHint(endpoint))
                .content(new Content().addMediaType("application/json", mediaType));
    }

    private String buildParameterHint(EndpointSpec endpoint) {
        if (endpoint.doc() == null || endpoint.doc().parameters().isEmpty()) {
            return "Query 参数放入 body.query；POST 厂家 body 放入 body.body 字符串。";
        }
        StringBuilder sb = new StringBuilder("常用参数（写入 body.query）：\n");
        endpoint.doc().parameters().forEach(p -> {
            sb.append("- `").append(p.name()).append("`");
            if (Boolean.TRUE.equals(p.required())) {
                sb.append(" **必填**");
            }
            if (p.description() != null && !p.description().isBlank()) {
                sb.append(" — ").append(p.description());
            }
            if (p.example() != null && !p.example().isBlank()) {
                sb.append("，例：`").append(p.example()).append('`');
            }
            sb.append('\n');
        });
        return sb.toString();
    }

    private static String buildConnectorTagDescription(ConnectorSpec spec) {
        Set<String> groups = new LinkedHashSet<>();
        if (spec.endpoints() != null) {
            for (EndpointSpec endpoint : spec.endpoints()) {
                if (endpoint.enabled() != null && !endpoint.enabled()) {
                    continue;
                }
                groups.add(EndpointOpenApiMetadataResolver.resolveGroup(endpoint));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("厂家 **").append(spec.code3rd()).append("**");
        if (spec.baseUrl() != null) {
            sb.append(" — `").append(spec.baseUrl()).append('`');
        }
        sb.append("\n\n本标签下每个操作为 Spec 中登记的一个厂家 API。");
        if (!groups.isEmpty()) {
            sb.append("\n\n分组：").append(String.join("、", groups));
        }
        return sb.toString();
    }
}
