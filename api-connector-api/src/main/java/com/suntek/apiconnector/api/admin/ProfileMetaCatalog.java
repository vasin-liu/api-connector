/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.api.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 classpath 加载 Auth Profile UI 元数据（profiles-meta/*.json）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Component
public class ProfileMetaCatalog {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Map<String, Map<String, Object>> byId = new ConcurrentHashMap<>();

    /**
     * 启动时加载元数据。
     *
     * @param objectMapper JSON 解析器
     */
    public ProfileMetaCatalog(ObjectMapper objectMapper) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:profiles-meta/*.json");
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> meta = objectMapper.readValue(in, MAP_TYPE);
                Object profileId = meta.get("profileId");
                if (profileId != null) {
                    byId.put(String.valueOf(profileId), Map.copyOf(meta));
                }
            }
        }
    }

    /**
     * 返回全部 Profile 元数据（按 profileId 排序）。
     *
     * @return 元数据列表
     */
    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("profileId"))));
        return List.copyOf(list);
    }

    /**
     * 按 ID 查询元数据。
     *
     * @param profileId Profile 标识
     * @return 元数据
     */
    public Optional<Map<String, Object>> find(String profileId) {
        return Optional.ofNullable(byId.get(profileId));
    }
}
