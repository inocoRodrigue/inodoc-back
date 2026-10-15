package com.esn.inodoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres applicatifs divers (prefixe inodoc).
 */
@ConfigurationProperties(prefix = "inodoc")
public class AppProperties {

    private App app = new App();
    private Cors cors = new Cors();
    private Share share = new Share();
    private Mail mail = new Mail();
    private Admin admin = new Admin();
    private Demo demo = new Demo();
    private Sso sso = new Sso();

    public App getApp() {
        return app;
    }

    public void setApp(App app) {
        this.app = app;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Demo getDemo() {
        return demo;
    }

    public void setDemo(Demo demo) {
        this.demo = demo;
    }

    public Sso getSso() {
        return sso;
    }

    public void setSso(Sso sso) {
        this.sso = sso;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Share getShare() {
        return share;
    }

    public void setShare(Share share) {
        this.share = share;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public static class App {
        /** Commercial name of the application shown in the interface. */
        private String name = "inoDoc";
        /** Application version publicly exposed. */
        private String version = "1.0.0";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    public static class Cors {
        /** Allowed origins, separated by commas. */
        private String allowedOrigins = "http://localhost:4200";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /**
     * Bootstrap administrator, created on startup if it does not exist yet.
     * Its credentials come from environment variables in production.
     */
    public static class Admin {
        private String email = "admin@inodoc.fr";
        private String password = "";
        private String firstName = "Admin";
        private String lastName = "inoDoc";

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    /**
     * Demo data seeding. When enabled the database is wiped and re-seeded with a
     * demonstration data set at every startup. Must be disabled in production.
     */
    public static class Demo {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Single sign-on settings (Google). Reuses the Google OAuth client of the
     * mail integration but with its own login redirect URI.
     */
    public static class Sso {
        /** Comma-separated list of email domains allowed to sign in via SSO. */
        private String allowedDomains = "inodoc.fr";
        private Google google = new Google();

        public String getAllowedDomains() {
            return allowedDomains;
        }

        public void setAllowedDomains(String allowedDomains) {
            this.allowedDomains = allowedDomains;
        }

        public Google getGoogle() {
            return google;
        }

        public void setGoogle(Google google) {
            this.google = google;
        }

        public static class Google {
            /** Backend callback URI registered in Google Cloud for SSO login. */
            private String loginRedirectUri = "http://localhost:8080/api/auth/google/callback";
            /** Frontend URL the callback redirects to with the issued tokens. */
            private String successRedirect = "http://localhost:4200/auth/callback";

            public String getLoginRedirectUri() {
                return loginRedirectUri;
            }

            public void setLoginRedirectUri(String loginRedirectUri) {
                this.loginRedirectUri = loginRedirectUri;
            }

            public String getSuccessRedirect() {
                return successRedirect;
            }

            public void setSuccessRedirect(String successRedirect) {
                this.successRedirect = successRedirect;
            }
        }
    }

    public static class Share {
        private String baseUrl = "http://localhost:4200/share";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Mail {
        /**
         * Base64 key used to encrypt Google refresh tokens at rest
         * (AES-GCM) and to sign the OAuth state (HMAC). Override in production.
         */
        private String secretKey = "";

        private Google google = new Google();

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public Google getGoogle() {
            return google;
        }

        public void setGoogle(Google google) {
            this.google = google;
        }

        /** Google OAuth2 application settings (Gmail API). */
        public static class Google {
            /** OAuth2 client id issued by Google Cloud. */
            private String clientId = "";
            /** OAuth2 client secret issued by Google Cloud. */
            private String clientSecret = "";
            /** Redirect URI registered in Google Cloud (backend callback). */
            private String redirectUri = "http://localhost:8080/api/mail-accounts/oauth/callback";
            /** Frontend URL to redirect to after the OAuth callback. */
            private String successRedirect = "http://localhost:4200/account";

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String clientId) {
                this.clientId = clientId;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
            }

            public String getRedirectUri() {
                return redirectUri;
            }

            public void setRedirectUri(String redirectUri) {
                this.redirectUri = redirectUri;
            }

            public String getSuccessRedirect() {
                return successRedirect;
            }

            public void setSuccessRedirect(String successRedirect) {
                this.successRedirect = successRedirect;
            }
        }
    }
}
