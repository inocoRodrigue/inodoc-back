# inoDoc - Backend

## Technologies

- Java 17, Maven et Spring Boot 3.5.
- Spring Web, Spring Security et JWT pour l'API REST et l'authentification.
- Spring Data JPA / Hibernate, PostgreSQL et Flyway pour les donnees et migrations.
- AWS SDK S3 pour les fichiers, OpenPDF pour les exports PDF, PDFBox et Apache POI pour la lecture PDF/Word.
- Docker multi-stage, execution Java sans root.

## Communications

| Service | Usage |
| --- | --- |
| [Front Angular](https://github.com/inocoRodrigue/inodoc-front) | Appels directs a l'API REST du backend, sous `/api`, avec authentification JWT. |
| PostgreSQL | Stockage des donnees applicatives ; migrations Flyway au demarrage. |
| Google | SSO via OpenID Connect / OAuth 2.0 ; autorisation des comptes mail et envoi via l'API Gmail. |
| S3 / Clever Cloud Cellar | Stockage des fichiers et images ; URL signees pour la lecture des objets prives. |
| API IA compatible OpenAI | Extraction de CV, adaptation aux missions et generation de supports. |

## Configuration et lancement

La liste complete des variables est dans [.env.example](.env.example).
Renseigner un fichier local nomme `.env` a la racine, au format `CLE=valeur`,
sans guillemets de shell. Spring le charge automatiquement ; les variables du
processus sont prioritaires. Ce fichier ne doit jamais etre versionne.

Toutes les variables referencees par le YAML doivent etre definies : aucune
valeur de repli n'y est prevue. Les options facultatives peuvent rester vides.

| Groupe | Variables principales |
| --- | --- |
| Serveur | `PORT`, `SESSION_COOKIE_SECURE` |
| PostgreSQL | `POSTGRESQL_ADDON_HOST`, `POSTGRESQL_ADDON_PORT`, `POSTGRESQL_ADDON_DB`, `POSTGRESQL_ADDON_USER`, `POSTGRESQL_ADDON_PASSWORD` |
| S3 / Cellar | `S3_BUCKET`, `AWS_REGION`, `CELLAR_ADDON_HOST`, `CELLAR_ADDON_KEY_ID`, `CELLAR_ADDON_KEY_SECRET` |
| Google | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` et les quatre URLs de retour detaillees ci-dessous |
| Securite | `INODOC_JWT_SECRET`, `INODOC_MAIL_SECRET_KEY` |
| Administrateur initial | `INODOC_ADMIN_EMAIL`, `INODOC_ADMIN_PASSWORD`, `INODOC_ADMIN_FIRST_NAME`, `INODOC_ADMIN_LAST_NAME` |
| Front et acces | `INODOC_CORS_ALLOWED_ORIGINS`, `INODOC_SSO_ALLOWED_DOMAINS`, `INODOC_SHARE_BASE_URL` |
| IA | `AI_MODEL_BASE_URL`, `AI_MODEL_API_KEY`, `AI_MODEL_NAME` ; options TLS dans le modele d'environnement |

L'URL JDBC est construite avec l'hote, le port et le nom de base.
`POSTGRESQL_ADDON_URI` et `POSTGRESQL_ADDON_VERSION` sont des informations de
l'addon, presentes dans le modele mais non utilisees pour cette connexion.
Pour Cellar, `AWS_REGION` sert a la signature S3 ; `us-east-1` convient.

Avec Java 17, Maven et une base PostgreSQL accessible, depuis la racine :

```bash
mvn spring-boot:run
```

Avec `PORT=8080`, l'API est accessible sur `http://localhost:8080/api`.

Pour Docker :

```bash
docker build -t inodoc-back .
docker run --rm --env-file .env -p 8080:8080 inodoc-back
```

Utiliser `PORT=8080` pour cette commande. En production, fournir les variables
au conteneur ou via la plateforme ; aucun secret n'est integre a l'image.
Dans Docker, `localhost` designe le conteneur, pas la machine ni un autre service.

## Domaines et Google

### Domaines email admis au SSO

```dotenv
INODOC_SSO_ALLOWED_DOMAINS=entreprise.fr,partenaire.fr
```

Liste separee par des virgules, sans `@`, schema URL ni joker. La comparaison
du domaine email est exacte et insensible a la casse. Les sous-domaines doivent
etre ajoutes explicitement ; une liste vide refuse toute connexion Google SSO.
Cette liste ne filtre pas les connexions locales par email et mot de passe.

### Origines autorisees pour le front

```dotenv
INODOC_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://front.example.com
INODOC_SHARE_BASE_URL=https://front.example.com/share
```

CORS attend les origines exactes du front, separees par des virgules : schema,
hote et port eventuel, sans chemin ni slash final. Adapter le port si le front
local n'utilise pas 4200. Ne pas utiliser `*` avec les requetes authentifiees
incluant des cookies. CORS et domaines email SSO sont deux controles distincts.

### Retours OAuth

Configurer ces URLs et enregistrer les deux callbacks backend comme URI de
redirection autorisees dans le client OAuth de Google Cloud :

| Variable | Destination |
| --- | --- |
| `GOOGLE_LOGIN_REDIRECT_URI` | `https://back.example.com/api/auth/google/callback` |
| `GOOGLE_REDIRECT_URI` | `https://back.example.com/api/mail-accounts/oauth/callback` |
| `GOOGLE_LOGIN_SUCCESS_REDIRECT` | `https://front.example.com/auth/callback` |
| `GOOGLE_SUCCESS_REDIRECT` | `https://front.example.com/account` |

Activer l'API Gmail pour l'envoi de mail et configurer l'ecran de consentement
OAuth, y compris les utilisateurs de test si l'application Google est en test.
Le SSO demande aussi l'autorisation `gmail.send` hors ligne afin de connecter
automatiquement le compte Gmail personnel. Le jeton de renouvellement est
stocke chiffre cote serveur; si Google ne le fournit pas, l'utilisateur peut
connecter son compte depuis la page Mon compte.

Le parcours OAuth utilise un cookie de session : depart et callback doivent
rester dans le meme navigateur. La configuration actuelle impose `SameSite=Lax`.
Preferer front et back sur le meme site, par exemple deux sous-domaines d'un
meme domaine sous HTTPS. Pour des sites distincts, une adaptation du cookie
backend a `SameSite=None; Secure` est necessaire ; CORS seul ne suffit pas et
le blocage des cookies tiers peut encore empecher le parcours.
En multi-instance, conserver l'affinite de session pendant OAuth.

## Precautions

- Utiliser deux cles Base64 aleatoires distinctes d'au moins 32 octets pour JWT et mail. Changer la cle mail necessite de reconnecter les comptes Google existants.
- Garder `SESSION_COOKIE_SECURE=true` sous HTTPS ; utiliser `false` uniquement pour le developpement local HTTP.
- Garder `INODOC_DEMO_DATA=false` en production : le mode demo efface les utilisateurs et profils avant leur initialisation.
- Garder `AI_TLS_SKIP_CERTIFICATE_VERIFICATION=false` ; ne transmettre a l'IA que des donnees autorisees.
- Autoriser le backend a lire, ecrire et supprimer les objets S3 ; sauvegarder la base avant les migrations.
