package com.adac.portail.controller;

import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.dto.response.UnreadNotificationsResponse;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.WithMockAdacUser;
import com.adac.portail.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link NotificationController} — TICKET-033. Same {@code addFilters = false} +
 * {@link WithMockAdacUser} pattern as the other controllers here — every notification is scoped
 * to the caller, no {@code @PreAuthorize} needed (there's no cross-user access at all to gate).
 *
 * <p>Branch-wide review: every stub/verify below matches the principal with {@code
 * eq(currentPrincipal())}, not {@code any()} — see {@code WithMockAdacUser}'s Javadoc for why
 * {@code any()} would let a broken {@code @AuthenticationPrincipal} binding (e.g. {@code null})
 * pass silently on endpoints that have no other authorization layer at all.</p>
 */
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static AdacUserDetails currentPrincipal() {
        return (AdacUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // --- GET /api/notifications --------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getNotificationsReturnsFullHistoryFromService() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(notificationService.getNotifications(eq(principal), isNull()))
                .thenReturn(List.of(NotificationResponse.builder().id(1L).build()));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getNotificationsWithReadFilterPassesItToTheService() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(notificationService.getNotifications(eq(principal), eq(false)))
                .thenReturn(List.of(NotificationResponse.builder().id(2L).build()));

        mockMvc.perform(get("/api/notifications").param("read", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getNotificationsWithInvalidReadValueReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/notifications").param("read", "abc"))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/notifications/unread ---------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getUnreadReturnsCountAndNotificationsFromService() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(notificationService.getUnread(eq(principal))).thenReturn(UnreadNotificationsResponse.builder()
                .count(1)
                .notifications(List.of(NotificationResponse.builder().id(3L).build()))
                .build());

        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(3));
    }

    // --- PATCH /api/notifications/{id}/read ----------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void markAsReadReturnsTheUpdatedNotification() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(notificationService.markAsRead(eq(principal), eq(10L)))
                .thenReturn(NotificationResponse.builder().id(10L).build());

        mockMvc.perform(patch("/api/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(notificationService).markAsRead(eq(principal), eq(10L));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void markAsReadWithUnknownOrForeignNotificationReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Notification introuvable"))
                .when(notificationService).markAsRead(eq(currentPrincipal()), eq(404L));

        mockMvc.perform(patch("/api/notifications/404/read"))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /api/notifications/read-all -----------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void markAllAsReadCallsServiceWithTheRealPrincipal() throws Exception {
        AdacUserDetails principal = currentPrincipal();

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk());

        verify(notificationService).markAllAsRead(eq(principal));
    }

    // --- DELETE /api/notifications/{id} (docs/tech.md — no /bell suffix) -----------------------

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void deleteFromBellReturnsNoContent() throws Exception {
        AdacUserDetails principal = currentPrincipal();

        mockMvc.perform(delete("/api/notifications/10"))
                .andExpect(status().isNoContent());

        verify(notificationService).deleteFromBell(eq(principal), eq(10L));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void deleteFromBellWithUnknownOrForeignNotificationReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Notification introuvable"))
                .when(notificationService).deleteFromBell(eq(currentPrincipal()), eq(404L));

        mockMvc.perform(delete("/api/notifications/404"))
                .andExpect(status().isNotFound());
    }
}
