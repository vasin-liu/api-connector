package com.suntek.integration.api.admin;

import com.suntek.integration.connectors.BuiltinConnectorCatalogs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorSpecJsonSupportTest {

    @Test
    void toMap_serializesResponseSpecFields() {
        Map<String, Object> map = ConnectorSpecJsonSupport.toMap(
                BuiltinConnectorCatalogs.catalogSpec("IDPS"));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) map.get("response");
        assertThat(response).isNotNull();
        assertThat(response).containsKeys("successWhen", "dataPath", "messagePath", "codePath");
    }
}
