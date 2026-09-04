package com.adac.portail.service;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.security.AdacUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void getCurrentUserDelegatesToUserMapper() {
        User user = User.builder()
                .id(1L)
                .email("stagiaire@adac.fr")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build();
        AdacUserDetails principal = new AdacUserDetails(user);
        UserResponse expected = UserResponse.builder().id(1L).email("stagiaire@adac.fr").build();
        when(userMapper.toResponse(user)).thenReturn(expected);

        UserResponse result = authService.getCurrentUser(principal);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getCurrentUserRejectsNullPrincipal() {
        assertThatThrownBy(() -> authService.getCurrentUser(null))
                .isInstanceOf(NullPointerException.class);
    }
}
