package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.AuthPolicySnapshot;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.AuthorizationMode;
import com.g2rain.iam.enums.AuthorizationTransactionStatus;
import com.g2rain.iam.enums.RedisKeyRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationTransactionServiceTest {

    @Mock
    private GenericRedisHelper genericRedisHelper;

    @Mock
    private IamAccessProperties iamAccessProperties;

    @Mock
    private AuthPolicyResolver authPolicyResolver;

    private AuthorizationTransactionService service;

    private final Map<String, Object> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        IamAccessProperties.AuthorizationTransaction cfg = new IamAccessProperties.AuthorizationTransaction();
        cfg.setTtlSeconds(600);
        when(iamAccessProperties.getAuthorizationTransaction()).thenReturn(cfg);
        AuthPolicySnapshot defaultPolicy = new AuthPolicySnapshot();
        defaultPolicy.setLoginMethods(java.util.EnumSet.of(com.g2rain.iam.enums.LoginMethod.PASSWORD));
        defaultPolicy.setAllowRegister(true);
        defaultPolicy.setSource(com.g2rain.iam.enums.AuthPolicySource.PLATFORM_DEFAULT);
        lenient().when(authPolicyResolver.resolve(any())).thenReturn(defaultPolicy);
        service = new AuthorizationTransactionService(genericRedisHelper, iamAccessProperties, authPolicyResolver);

        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            store.put(key, value);
            return null;
        }).when(genericRedisHelper).set(anyString(), any(), any(Duration.class));

        lenient().when(genericRedisHelper.get(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return store.get(key);
        });
    }

    @Test
    void createOrReusePersistsCreatedTransaction() {
        AuthorizationTransactionDto txn = service.createOrReuse(
            "client-1", "https://app/callback", "state-1", null, "flow-hash");

        assertEquals(AuthorizationMode.USER, txn.getAuthorizationMode());
        assertEquals(AuthorizationTransactionStatus.CREATED, txn.getStatus());
        assertEquals("flow-hash", txn.getFlowCookieHash());
        verify(genericRedisHelper).set(
            eq(RedisKeyRule.AUTHORIZATION_TRANSACTION.format(txn.getTid())),
            any(AuthorizationTransactionDto.class),
            any(Duration.class));
    }

    @Test
    void requireActiveRejectsMismatchedFlowCookie() {
        AuthorizationTransactionDto txn = service.createOrReuse(
            "client-1", "https://app/callback", "state-2", null, "flow-a");
        store.put(
            RedisKeyRule.AUTHORIZATION_TRANSACTION.format(txn.getTid()),
            txn);

        assertThrows(
            BusinessException.class,
            () -> service.requireActive(txn.getTid(), "flow-b"));
    }

    @Test
    void createOrReuseReturnsExistingWhenFlowMatches() {
        AuthorizationTransactionDto first = service.createOrReuse(
            "client-1", "https://app/callback", "state-3", "app-a", "flow-hash");
        store.put(RedisKeyRule.AUTHORIZATION_TRANSACTION.format(first.getTid()), first);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(genericRedisHelper, org.mockito.Mockito.atLeastOnce())
            .set(keyCaptor.capture(), any(), any(Duration.class));

        AuthorizationTransactionDto second = service.createOrReuse(
            "client-1", "https://app/callback", "state-3", "app-a", "flow-hash");
        assertEquals(first.getTid(), second.getTid());
    }
}
