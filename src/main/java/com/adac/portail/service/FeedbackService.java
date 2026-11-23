package com.adac.portail.service;

import com.adac.portail.dto.request.FeedbackForm;
import com.adac.portail.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface FeedbackService {

    /**
     * Valide le retour puis l'envoie par email au développeur, sans rien stocker.
     *
     * @param author     utilisateur de session (jamais une identité fournie par le client)
     * @param screenshot capture facultative, peut être {@code null}
     */
    void send(User author, FeedbackForm form, MultipartFile screenshot);
}
