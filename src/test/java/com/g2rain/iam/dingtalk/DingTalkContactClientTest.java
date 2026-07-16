package com.g2rain.iam.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DingTalkContactClientTest {

    private DingTalkIamProperties properties;
    private DingTalkContactClient client;

    @BeforeEach
    void setUp() {
        properties = new DingTalkIamProperties();
        properties.getInternal().setClientId("ding-internal-client");
        properties.getInternal().setClientSecret("secret");
        properties.getInternal().setCorpId("corp-internal");
        client = new DingTalkContactClient(properties, RestClient.builder().build(), new ObjectMapper());
    }

    @Test
    void resolveIdpApplicationCode_shouldDefaultToConfiguredClientId() {
        assertEquals("ding-internal-client",
            client.resolveIdpApplicationCode(IdpBindMode.INTERNAL, null));
    }

    @Test
    void resolveCredentialForContactSync_shouldAcceptMatchingContext() {
        DingTalkIamProperties.Credential credential = client.resolveCredentialForContactSync(
            IdpBindMode.INTERNAL, "ding-internal-client", "corp-internal");
        assertEquals("ding-internal-client", credential.getClientId());
        assertEquals("corp-internal", credential.getCorpId());
    }

    @Test
    void resolveCredentialForContactSync_shouldRejectApplicationMismatch() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> client.resolveCredentialForContactSync(IdpBindMode.INTERNAL, "other-client", "corp-internal")
        );
        assertEquals(IamErrorCode.DINGTALK_CONTACT_APPLICATION_MISMATCH.code(), exception.getErrorCode());
    }

    @Test
    void resolveCredentialForContactSync_shouldRejectCorpMismatch() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> client.resolveCredentialForContactSync(IdpBindMode.INTERNAL, "ding-internal-client", "other-corp")
        );
        assertEquals(IamErrorCode.DINGTALK_CONTACT_CORP_MISMATCH.code(), exception.getErrorCode());
    }

    @Test
    void resolveCredentialForContactSync_thirdParty_shouldNotRequireConfiguredCorpId() {
        properties.getThirdParty().setClientId("ding-isv-client");
        properties.getThirdParty().setClientSecret("isv-secret");

        DingTalkIamProperties.Credential credential = client.resolveCredentialForContactSync(
            IdpBindMode.THIRD_PARTY, "ding-isv-client", "tenant-corp-a");
        assertEquals("ding-isv-client", credential.getClientId());
    }

    @Test
    void parseUserSummary_shouldReadUnionIdAndDeptIds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode item = mapper.createObjectNode();
        item.put("userid", "userid-1");
        item.put("unionid", "union-1");
        item.put("name", "张三");
        item.put("mobile", "13800000000");
        item.put("email", "zhang@example.com");
        item.putArray("dept_id_list").add(2).add(3);

        DingTalkContactClient.UserSummary summary = DingTalkContactClient.parseUserSummary(item);

        assertEquals("userid-1", summary.userId());
        assertEquals("union-1", summary.unionId());
        assertEquals("张三", summary.name());
        assertEquals(List.of(2L, 3L), summary.deptIds());
    }
}
