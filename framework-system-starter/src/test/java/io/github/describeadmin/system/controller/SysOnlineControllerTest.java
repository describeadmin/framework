package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.security.api.ActiveSession;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.system.controller.SysOnlineController.OnlineSessionView;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysOnlineControllerTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void paginatesTiedSessionsInStableOrderAfterFiltering() {
        String currentToken = "current-token";
        String currentId = ActiveSession.sessionIdOf(currentToken);
        ActiveSession current = session(currentId, 1L, "Admin", ISSUED_AT);
        ActiveSession other = session(currentId + "a", 1L, "Admin", ISSUED_AT);
        ActiveSession older = session(currentId + "b", 1L, "Admin", ISSUED_AT.minusSeconds(1));
        ActiveSession excluded = session("excluded", 2L, "Guest", ISSUED_AT.plusSeconds(1));
        TokenStore store = mock(TokenStore.class);
        when(store.listActive())
                .thenReturn(List.of(current, excluded, older, other))
                .thenReturn(List.of(other, older, excluded, current))
                .thenReturn(List.of(older, current, other, excluded));

        SysOnlineController controller = new SysOnlineController(store);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + currentToken);
        PageQuery query = new PageQuery();
        query.setSize(1);

        PageResult<OnlineSessionView> firstPage = controller.list(query, " ADMIN ", request).getData();
        assertThat(firstPage.getTotal()).isEqualTo(3);
        assertThat(firstPage.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getSession()).isSameAs(other);
            assertThat(row.isCurrent()).isFalse();
        });

        query.setCurrent(2);
        assertThat(controller.list(query, " ADMIN ", request).getData().getRecords())
                .singleElement().satisfies(row -> {
                    assertThat(row.getSession()).isSameAs(current);
                    assertThat(row.isCurrent()).isTrue();
                });

        query.setCurrent(3);
        assertThat(controller.list(query, " ADMIN ", request).getData().getRecords())
                .extracting(OnlineSessionView::getSession).containsExactly(older);
    }

    @Test
    void usesDescendingUserIdForLegacySessionsWithoutSessionIds() {
        ActiveSession first = session(null, 1L, "First", ISSUED_AT);
        ActiveSession second = session(null, 2L, "Second", ISSUED_AT);
        TokenStore store = mock(TokenStore.class);
        when(store.listActive()).thenReturn(List.of(first, second));

        assertThat(new SysOnlineController(store).list(new PageQuery(), null, new MockHttpServletRequest())
                .getData().getRecords()).extracting(OnlineSessionView::getSession).containsExactly(second, first);
    }

    private static ActiveSession session(String sessionId, long userId, String username, Instant issuedAt) {
        return new ActiveSession(sessionId, userId, username, username, "password",
                issuedAt, ISSUED_AT.plusSeconds(60), null, null);
    }
}
