package com.adac.portail.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailTemplateBuilder {

    private static final String PRIMARY = "#cc3d34";
    private static final String TEXT = "#1f2629";
    private static final String MUTED = "#949598";
    private static final String BACKGROUND = "#faf8f5";
    private static final String SURFACE = "#ffffff";

    public String buildActivationEmail(String prenom, String code) {
        return buildCodeEmail(
                "Bienvenue sur le portail ADAC",
                "Bonjour " + HtmlUtils.htmlEscape(prenom) + ",",
                "Voici votre code d'activation :",
                code,
                "Il expire dans 30 minutes.");
    }

    public String buildPasswordResetEmail(String prenom, String code) {
        return buildCodeEmail(
                "Réinitialisation de votre mot de passe",
                "Bonjour " + HtmlUtils.htmlEscape(prenom) + ",",
                "Voici votre code de réinitialisation :",
                code,
                "Il expire dans 30 minutes. Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.");
    }

    public String buildNotificationEmail(String content) {
        String escapedContent = HtmlUtils.htmlEscape(content);
        return shell("""
                <h1 style="color:%s;font-size:20px;margin:0 0 16px;">Portail de Formation ADAC</h1>
                <p style="color:%s;font-size:14px;line-height:1.5;margin:0;">%s</p>
                """.formatted(PRIMARY, TEXT, escapedContent)
                + footer("Vous recevez cet email suite à une notification sur le portail ADAC."));
    }

    private String buildCodeEmail(String title, String greeting, String lead, String code, String footerText) {
        return shell("""
                <h1 style="color:%s;font-size:20px;margin:0 0 16px;">%s</h1>
                <p style="color:%s;font-size:14px;margin:0 0 16px;">%s</p>
                <p style="color:%s;font-size:14px;margin:0 0 8px;">%s</p>
                <p style="color:%s;font-size:28px;font-weight:700;letter-spacing:4px;margin:0 0 16px;">%s</p>
                """.formatted(PRIMARY, HtmlUtils.htmlEscape(title), TEXT, greeting, TEXT,
                        HtmlUtils.htmlEscape(lead), PRIMARY, code)
                + footer(footerText));
    }

    private String footer(String text) {
        return "<p style=\"color:%s;font-size:12px;margin:0;\">%s</p>".formatted(MUTED, HtmlUtils.htmlEscape(text));
    }

    private String shell(String innerHtml) {
        return """
                <!doctype html>
                <html>
                  <body style="margin:0;padding:0;background:%s;font-family:Manrope,Arial,sans-serif;">
                    <div style="max-width:480px;margin:0 auto;padding:32px 24px;">
                      <div style="background:%s;border-radius:8px;padding:24px;">
                        %s
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(BACKGROUND, SURFACE, innerHtml);
    }
}
