/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.persistence.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.engine.ConnectorSpecStatus;
import com.suntek.apiconnector.engine.store.ConnectorConfigStore;
import com.suntek.apiconnector.engine.store.StoredConnectorConfig;
import com.suntek.apiconnector.persistence.ConnectorRegistryKey;
import com.suntek.apiconnector.spec.ConnectorSpecParser;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC 实现：连接器客户端 + Spec 落库。
 */
@Repository
@ConditionalOnExpression("'${integration.persistence.source:composite}'.equals('jdbc') "
        + "or '${integration.persistence.source:composite}'.equals('composite')")
public class JdbcConnectorConfigStore implements ConnectorConfigStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {};

    private static final String SQL_LIST_PUBLISHED =
            "SELECT c.CODE3RD, c.HOST3RD, c.APP_ID, c.APP_SECRET, c.PUBLIC_KEY, c.PARAM_JSON, "
                    + "s.SPEC_JSON, s.PUBLISH_STATUS "
                    + "FROM IT_CONNECTOR_CLIENT c "
                    + "JOIN IT_CONNECTOR_SPEC s ON s.CLIENT_ID = c.ID "
                    + "WHERE s.PUBLISH_STATUS = 'PUBLISHED'";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcConnectorConfigStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(ConnectorSpec spec, Map<String, String> credentials, ConnectorSpecStatus status) {
        String code3rd = spec.code3rd();
        String scope = ConnectorRegistryKey.normalizeScope(null);
        long clientId = upsertClient(code3rd, scope, spec, credentials);
        upsertSpec(clientId, spec, status);
    }

    @Override
    @Transactional
    public boolean delete(String code3rd) {
        int rows = jdbc.update(
                "DELETE FROM IT_CONNECTOR_CLIENT WHERE CODE3RD = ?",
                code3rd);
        return rows > 0;
    }

    @Override
    public List<StoredConnectorConfig> listPublished() {
        return jdbc.query(SQL_LIST_PUBLISHED, (rs, rowNum) -> {
            try {
                Map<String, Object> specMap = objectMapper.readValue(rs.getString("SPEC_JSON"), MAP_TYPE);
                ConnectorSpec spec = ConnectorSpecParser.parse(specMap);
                if (isBlank(spec.baseUrl())) {
                    String host = rs.getString("HOST3RD");
                    if (!isBlank(host)) {
                        specMap.put("baseUrl", host);
                        spec = ConnectorSpecParser.parse(specMap);
                    }
                }
                Map<String, String> creds = credentialsFromRow(rs);
                return new StoredConnectorConfig(spec, creds, ConnectorSpecStatus.PUBLISHED);
            } catch (Exception ex) {
                throw new IllegalStateException("Invalid stored spec for " + rs.getString("CODE3RD"), ex);
            }
        });
    }

    @Override
    public Optional<StoredConnectorConfig> find(String code3rd) {
        String sql = "SELECT c.CODE3RD, c.HOST3RD, c.APP_ID, c.APP_SECRET, c.PUBLIC_KEY, c.PARAM_JSON, "
                + "s.SPEC_JSON, s.PUBLISH_STATUS "
                + "FROM IT_CONNECTOR_CLIENT c "
                + "JOIN IT_CONNECTOR_SPEC s ON s.CLIENT_ID = c.ID "
                + "WHERE c.CODE3RD = ? "
                + "ORDER BY s.UPDATED_AT DESC "
                + "LIMIT 1";
        List<StoredConnectorConfig> list = jdbc.query(sql, (rs, rowNum) -> {
            try {
                Map<String, Object> specMap = objectMapper.readValue(rs.getString("SPEC_JSON"), MAP_TYPE);
                ConnectorSpec spec = ConnectorSpecParser.parse(specMap);
                Map<String, String> creds = credentialsFromRow(rs);
                ConnectorSpecStatus status = "PUBLISHED".equals(rs.getString("PUBLISH_STATUS"))
                        ? ConnectorSpecStatus.PUBLISHED
                        : ConnectorSpecStatus.DRAFT;
                return new StoredConnectorConfig(spec, creds, status);
            } catch (Exception ex) {
                throw new IllegalStateException("Invalid stored spec for " + code3rd, ex);
            }
        }, code3rd);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private long upsertClient(String code3rd, String scope, ConnectorSpec spec, Map<String, String> credentials) {
        Optional<Long> existing = jdbc.query(
                "SELECT ID FROM IT_CONNECTOR_CLIENT WHERE CODE3RD = ? AND SCOPE = ?",
                rs -> rs.next() ? Optional.of(rs.getLong("ID")) : Optional.empty(),
                code3rd,
                scope);
        String host = spec.baseUrl();
        String appId = credOrEmpty(credentials, "appId");
        String appSecret = credOrEmpty(credentials, "appSecret");
        String publicKey = credOrEmpty(credentials, "publicKey");
        String protocol = spec.protocol() != null ? spec.protocol() : "HTTP";

        if (existing.isPresent()) {
            jdbc.update(
                    "UPDATE IT_CONNECTOR_CLIENT "
                            + "SET HOST3RD = ?, PROTOCOL = ?, APP_ID = ?, APP_SECRET = ?, PUBLIC_KEY = ?, UPDATED_AT = ? "
                            + "WHERE ID = ?",
                    host, protocol, appId, appSecret, publicKey, Timestamp.from(Instant.now()), existing.get());
            return existing.get();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO IT_CONNECTOR_CLIENT "
                            + "(CODE3RD, SCOPE, NAME3RD, HOST3RD, PROTOCOL, APP_ID, APP_SECRET, PUBLIC_KEY, STATUS) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, code3rd);
            ps.setString(2, scope);
            ps.setString(3, code3rd);
            ps.setString(4, host);
            ps.setString(5, protocol);
            ps.setString(6, appId);
            ps.setString(7, appSecret);
            ps.setString(8, publicKey);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to insert client for " + code3rd);
        }
        return key.longValue();
    }

    private void upsertSpec(long clientId, ConnectorSpec spec, ConnectorSpecStatus status) {
        String specJson;
        try {
            Map<String, Object> root = new HashMap<>();
            root.put("connector", ConnectorSpecParser.toConnectorMap(spec));
            specJson = objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize connector spec", ex);
        }
        String authProfile = authTypeFromSpec(spec);
        String publishStatus = status == ConnectorSpecStatus.PUBLISHED ? "PUBLISHED" : "DRAFT";
        Timestamp publishedAt = status == ConnectorSpecStatus.PUBLISHED ? Timestamp.from(Instant.now()) : null;

        Optional<Long> specId = jdbc.query(
                "SELECT ID FROM IT_CONNECTOR_SPEC WHERE CLIENT_ID = ? ORDER BY ID DESC LIMIT 1",
                rs -> rs.next() ? Optional.of(rs.getLong("ID")) : Optional.empty(),
                clientId);

        if (specId.isPresent()) {
            jdbc.update(
                    "UPDATE IT_CONNECTOR_SPEC "
                            + "SET VERSION = ?, SPEC_JSON = ?, AUTH_PROFILE = ?, PUBLISH_STATUS = ?, "
                            + "PUBLISHED_AT = ?, UPDATED_AT = ? WHERE ID = ?",
                    spec.version() != null ? spec.version() : "1.0.0",
                    specJson,
                    authProfile,
                    publishStatus,
                    publishedAt,
                    Timestamp.from(Instant.now()),
                    specId.get());
        } else {
            jdbc.update(
                    "INSERT INTO IT_CONNECTOR_SPEC "
                            + "(CLIENT_ID, VERSION, SPEC_JSON, AUTH_PROFILE, PUBLISH_STATUS, PUBLISHED_AT) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    clientId,
                    spec.version() != null ? spec.version() : "1.0.0",
                    specJson,
                    authProfile,
                    publishStatus,
                    publishedAt);
        }
    }

    private static String authTypeFromSpec(ConnectorSpec spec) {
        if (spec.auth() == null) {
            return "none";
        }
        Object type = spec.auth().get("type");
        return type != null ? String.valueOf(type) : "none";
    }

    private static Map<String, String> credentialsFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, String> map = new HashMap<>();
        putIfPresent(map, "appId", rs.getString("APP_ID"));
        putIfPresent(map, "appSecret", rs.getString("APP_SECRET"));
        putIfPresent(map, "publicKey", rs.getString("PUBLIC_KEY"));
        return map;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (!isBlank(value)) {
            map.put(key, value);
        }
    }

    private static String credOrEmpty(Map<String, String> credentials, String key) {
        if (credentials == null) {
            return "";
        }
        String value = credentials.get(key);
        return value != null ? value : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
