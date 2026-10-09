package com.esn.inodoc.controller;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.GlobalExceptionHandler;
import com.esn.inodoc.controller.error.GoneException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicShareControllerTest {
    private ShareService shares;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        shares = mock(ShareService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PublicShareController(shares))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unknownLinkReturns404WithoutInternalDetails() throws Exception {
        when(shares.access("unknown", null))
                .thenThrow(new NotFoundException("Lien de partage introuvable"));
        mvc.perform(get("/public/share/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void expiredLinkReturns410ForFrontendRedirect() throws Exception {
        when(shares.access("expired", null)).thenThrow(new GoneException("Lien expire"));
        mvc.perform(get("/public/share/expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void passwordChallengeDoesNotRedirectToInvalidLink() throws Exception {
        when(shares.access("protected", "incorrect"))
                .thenThrow(new BadRequestException("Mot de passe invalide"));
        mvc.perform(get("/public/share/protected").param("password", "incorrect"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mot de passe invalide"));
    }
}
