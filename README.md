# inoDoc - Backend

API des dossiers de competences, entretiens et carnets de carriere.
Java 17, Spring Boot 3.5, PostgreSQL, Flyway et JWT.

## Developpement local

Le fichier `.env` a la racine du backend est ignore par Git et charge par Spring
avec `optional:file:.env[.properties]`. Utiliser le format `CLE=valeur`, sans
guillemets de shell. Ne jamais versionner ce fichier. `.env.example` contient
les variables et des valeurs locales non secretes, sans identifiant reel.
Toutes les variables referencees dans `application.yml` figurent dans les deux
fichiers. Le YAML ne fournit aucune valeur de repli : definir chaque variable,
meme avec une valeur vide pour les options facultatives.

Avec une base PostgreSQL accessible et les variables renseignees :

```bash
mvn spring-boot:run
```

L'API est servie sur `/api`, port 8080 par defaut. La documentation API publique
a ete retiree. Le mode demo est desactive par defaut ; l'activer reinitialise les
donnees et ne doit jamais etre utilise en production.

## Image Docker

Depuis la racine du backend :

```bash
docker build -t inodoc-back .
```

Le Dockerfile est autonome, multi-stage et execute Java sans root sur le port
8080. Il ne copie ni `.env` ni secrets dans l'image. Utiliser `JAVA_TOOL_OPTIONS`
pour les options JVM. Aucun script de publication ou d'orchestration n'est requis.

## Variables Clever Cloud

Les variables sont fournies a l'execution par la plateforme. Les variables de
l'environnement ont priorite sur le `.env` local.

- Base : `POSTGRESQL_ADDON_HOST`, `POSTGRESQL_ADDON_PORT`, `POSTGRESQL_ADDON_DB`,
  `POSTGRESQL_ADDON_USER` et `POSTGRESQL_ADDON_PASSWORD`. L'URL JDBC est construite
  avec l'hote, le port et le nom de base. `POSTGRESQL_ADDON_URI` et
  `POSTGRESQL_ADDON_VERSION` sont conserves dans les fichiers d'environnement
  pour les informations de l'addon, mais ne sont pas utilises par le pilote JDBC.
- Obligatoires : `INODOC_JWT_SECRET` et `INODOC_MAIL_SECRET_KEY`, chacune une cle
  Base64 aleatoire d'au moins 32 octets. Une cle mail existante chiffre les tokens
  Google deja stockes : son changement necessite leur reconnexion.
- Initialisation admin : `INODOC_ADMIN_EMAIL`, `INODOC_ADMIN_PASSWORD`.
- IA : `AI_MODEL_API_KEY`, et eventuellement `AI_MODEL_BASE_URL`, `AI_MODEL_NAME`.
- Images : `S3_BUCKET`, `CELLAR_ADDON_HOST`, `CELLAR_ADDON_KEY_ID`, `CELLAR_ADDON_KEY_SECRET`.
- URLs : `INODOC_CORS_ALLOWED_ORIGINS`, `INODOC_SHARE_BASE_URL`,
  `GOOGLE_REDIRECT_URI`, `GOOGLE_LOGIN_REDIRECT_URI`,
  `GOOGLE_SUCCESS_REDIRECT`, `GOOGLE_LOGIN_SUCCESS_REDIRECT`.

Les callbacks OAuth doivent utiliser l'origine publique du front suivie du chemin
`/api/...`, afin que le proxy transmette aussi le cookie de session OAuth. Les URLs
de retour doivent pointer vers le front. Garder `SESSION_COOKIE_SECURE=true`,
`INODOC_DEMO_DATA=false` et `AI_TLS_SKIP_CERTIFICATE_VERIFICATION=false` en production.
Le port expose est 8080 ; si `PORT` est defini, il doit correspondre au port
HTTP expose dans les reglages de l'application Clever Cloud.

Les secrets deja presents dans d'anciens commits doivent etre renouveles : les
retirer du code actuel ne les efface pas de l'historique Git.

## Modules MVP

- Authentification JWT + refresh tokens (rotation).
- Gestion des utilisateurs et RBAC (Admin / Business Manager / Interne / Externe).
- Dossiers de competences : profils, competences, experiences, langues, certifications.
- Generation PDF : CV synthetique et dossier detaille.
- Partage securise par lien (lecture seule ou edition, mot de passe, expiration).
- Historique / audit des actions.

## Evolution utilisateurs, partage, CV et images

### Organisation front / back

Le front Angular utilise des composants standalone, des signals et des formulaires
reactifs. Les services HTTP de `src/app/core` correspondent aux controleurs REST
Spring, qui deleguent aux services transactionnels puis aux repositories JPA.
L'identite RH (`Profile`) est distincte des versions de competences
(`CompetenceDossier`) : l'import d'un CV respecte cette separation.

- Administration : recherche et filtres locaux, confirmation native accessible,
  modification individuelle via `PUT /api/users/{id}/role` avec `{"role":"INTERNAL"}`.
  Les routes administratives exigent `ROLE_ADMIN` ; `/users/me` reste accessible
  au titulaire du compte. Le dernier administrateur actif ne peut etre retrograde,
  desactive ou supprime. Un verrou sur le role ADMIN serialise ces operations.
- Suppression : tokens, connexions mail, liens de partage et profil associe avec
  ses versions/enfants sont supprimes dans la meme transaction. Le schema actuel
  ne contient pas de table autonome d'historique/audit.
- JWT : les droits et l'etat du compte sont relus en base a chaque requete.
- Partage public : 404 pour un token inconnu, 410 pour un lien expire, revoque ou
  ayant atteint sa limite d'ouvertures. La demande de mot de passe conserve le 400.
- Signature : `PUT /api/users/me` accepte `signature` (texte brut, 10 000 caracteres).
  Une chaine vide efface la signature ; un champ absent la conserve. Le serveur
  ajoute la signature sauvegardee, jamais une substitution du formulaire de partage.
- CV : `POST /api/profile/ingest-resume`, multipart `file`, renvoie
  `{profile, dossier}` sans enregistrer le CV ou modifier un profil.
- Image : `POST /api/media/upload`, multipart `file` et `profileId`, renvoie
  `{url}` et enregistre `Profile.imageUrl`. Seul le proprietaire du profil ou un
  administrateur/ingenieur d'affaires peut le modifier. Le contenu reel est decode
  et controle : JPEG, PNG ou WebP, 5 Mo et 16 millions de pixels maximum.

### Configuration IA

| Variable | Usage |
| --- | --- |
| `AI_MODEL_BASE_URL` | URL compatible OpenAI, exemple local `https://api.openai.com/v1` |
| `AI_MODEL_API_KEY` | Cle du fournisseur, vide dans le fichier exemple |
| `AI_MODEL_NAME` | Modele choisi, exemple local `gpt-5.4-mini` |

Le fournisseur doit supporter `/chat/completions` et le mode JSON
`response_format: {"type":"json_object"}`. HTTPS est requis, sauf pour une API
locale. Sans configuration, l'API renvoie 503 explicitement. Une API locale qui
n'exige pas de cle peut utiliser une valeur de test non secrete.

Les PDF, DOC et DOCX sont limites a 10 Mo, 100 000 caracteres extraits et, pour les
PDF, 100 pages. Les PDF scannes sans texte ne sont pas pris en charge (OCR non
inclus). Les archives DOCX sont limitees a 25 Mo decompresses et 1 000 entrees.
Les donnees extraites restent a verifier : dates et informations
professionnelles peuvent etre incompletes ou incorrectes.

Le front demande le consentement avant l'analyse et une confirmation distincte
avant de remplir les champs. N'utiliser que des donnees autorisees pour le
fournisseur configure ; les tests automatises utilisent des documents synthetiques
et des services HTTP simules.

L'extraction IA conserve les champs et elements valides lorsqu'une partie de la
reponse est invalide. Les valeurs rejetees sont signalees par `incomplete: true`
et `warnings`. Le front affiche un avertissement avant et apres l'application ;
un import incomplet ne vide pas les champs ou collections non recuperes du dossier.
Une reponse non JSON reste une erreur, sans modification du formulaire.

### Adaptation a une mission

Depuis les actions d'un dossier, les gestionnaires peuvent coller un descriptif
de mission (30 000 caracteres maximum) et une transcription client facultative
(60 000 caracteres maximum). L'envoi au fournisseur IA exige un consentement explicite.
`POST /api/profiles/{profileId}/dossiers/{dossierId}/adapt-to-mission` verifie les
droits d'acces et retourne un brouillon, sans ecriture en base.

Le prompt impose de ne pas inventer de faits. Seules les reformulations du titre,
du resume et des contextes/taches existants sont acceptees ; les entreprises,
dates, competences, formations et certifications restent celles du dossier source.
Les identifiants, images signees, liens et coordonnees des references ne sont pas
transmis. Les exigences non couvertes et resultats incomplets sont signales.
Une relecture humaine reste indispensable pour verifier les formulations.

Le front compare la proposition au dossier source, puis ouvre le formulaire de
nouvelle version. Rien n'est sauvegarde avant l'enregistrement explicite et le
brouillon n'est pas master par defaut. Modifier les textes d'entree invalide la
proposition precedente. Les textes de mission/transcription ne sont pas stockes.

### Modeles d'entretien et carnet de carriere

Chaque campagne copie le titre et le schema du modele disponible a sa creation.
Modifier ce modele ne change ni les campagnes existantes ni l'affichage de leurs
reponses, y compris partagees. Les campagnes suivantes utilisent le modele mis a jour.
La migration V10 fige les campagnes existantes avec le modele actuellement en base ;
elle ne reconstitue pas les anciennes versions qui n'ont pas ete conservees.

Le carnet utilise au contraire le modele courant pour tous les collaborateurs,
sans copie du schema par entree. Modifier le modele ne reecrit pas les donnees
enregistrees. Un champ supprime n'est plus affiche mais sa valeur reste stockee ;
conserver les identifiants des champs permet de retrouver leurs reponses.

### Presentation des dossiers

Les rendus de dossier suivent la presentation du document INOCO de reference :
logo fourni, bandeau de contact bleu, initiales centrees, titres soulignes en
jaune et tableau de competences borde. Le logo configure pour l'entreprise reste
prioritaire sur le logo de reference. Les justificatifs restent sous leur element
et sont agrandissables dans le web.

L'export utilise le format Letter (612 x 792 points), comme la reference, avec
un bandeau sur chaque page et une pagination page/total. Les tests techniques,
references et experiences s'enchainent dans l'espace disponible. La pagination reste
adaptee au contenu ; aucune information n'est supprimee pour reproduire un nombre
fixe de pages. Les noms sont presentes sous forme d'initiales dans les deux rendus.

### Configuration S3

Pour Clever Cloud Cellar, definir les variables suivantes :

- `S3_BUCKET` : nom du bucket cree dans Cellar.
- `CELLAR_ADDON_HOST` : endpoint fourni par l'addon (avec ou sans `https://`).
- `CELLAR_ADDON_KEY_ID` : cle d'acces fournie par l'addon.
- `CELLAR_ADDON_KEY_SECRET` : cle secrete fournie par l'addon.
- `AWS_REGION` : optionnelle, region technique de signature, `us-east-1` par defaut,
  meme si la valeur est vide. Aucune region n'est a renseigner pour Cellar.

Le client utilise cet endpoint en mode path-style et les identifiants explicites.
Les secrets restent dans les variables d'environnement, jamais dans le YAML.
`CELLAR_ADDON_KEY_ID` doit contenir la cle d'acces, et `CELLAR_ADDON_KEY_SECRET`
la cle secrete : ne pas inverser les deux. Pour un lancement local Java/Maven,
renseigner le `.env` charge par `spring.config.import`, ou definir les variables
dans l'environnement du processus ou de la configuration de lancement VS Code.

Un refus HTTP 401/403 affiche une erreur d'identifiants ou de droits, distincte
d'un probleme de connexion. Les logs indiquent le statut et le code d'erreur S3,
sans exposer les secrets. Renouveler les cles qui ont ete diffusees ou versionnees.

Sans endpoint Cellar, `S3_BUCKET` (ou `AWS_S3_BUCKET_NAME`) et `AWS_REGION`
permettent de conserver le stockage AWS. Sans cles Cellar, le SDK utilise sa chaine standard
d'identifiants, dont `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` et, si necessaire,
`AWS_SESSION_TOKEN`. Un role IAM peut aussi etre utilise, sans cle dans le code.

Pour les images des dossiers, autoriser `s3:PutObject`, `s3:GetObject` et
`s3:DeleteObject` sur le prefixe `dossier-assets/`. Les photos utilisent aussi
les prefixes `profiles/` et `users/`.

Les images de formation, certification et test technique peuvent rester privees.
Le backend renvoie des URL GET signees valables une heure, renouvelees a chaque
lecture autorisee du dossier ou de son lien de partage. La signature temporaire
est retiree avant l'enregistrement ; seule l'URL stable est conservee en base.
Le PDF telecharge et integre ces images avec une signature fraiche, sans dependance
a une URL externe apres sa generation. La comparaison ignore les renouvellements
de signature pour un meme objet. Les URL hors du bucket configure ne sont pas signees.
L'application ne modifie pas la politique du bucket et n'applique aucune ACL publique.

Un echec de transaction declenche la suppression de l'objet nouvellement envoye.
Si ce nettoyage echoue, une erreur est journalisee. Les anciennes photos remplacees
restent dans S3 ; definir une politique de retention/nettoyage adaptee.

### Google et migrations

Definir `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`,
`GOOGLE_SUCCESS_REDIRECT` et une cle de chiffrement `INODOC_MAIL_SECRET_KEY`
appropriee. Les valeurs OAuth embarquees precedemment ont ete retirees ; renouveler
tout secret qui aurait ete diffuse ou versionne.

Chaque connexion Google est personnelle. L'adresse du token Google est verifiee
avant chaque envoi et doit correspondre au compte expediteur. Le callback OAuth
exige le meme navigateur que le depart, avec un etat signe, expirant et a usage
unique. En production HTTPS, definir `SESSION_COOKIE_SECURE=true`. Le frontend et
le backend doivent etre sur un meme site (de preference via le proxy `/api`) ;
en multi-instance, assurer l'affinite de session pendant le parcours OAuth.

Sauvegarder la base avant de deployer les migrations Flyway :

- V2 ajoute la signature, rattache les connexions mail aux utilisateurs et limite
  chaque utilisateur a une connexion active. Les connexions orphelines et les
  connexions actives surnumeraires sont desactivees, pas supprimees.
- V3 ajoute l'URL de photo du profil.
- V4 garantit qu'un profil ne peut etre rattache a plusieurs comptes.
- V5 ajoute la photo du compte, y compris sans dossier, et reprend la photo du
  profil associe. `POST /api/media/me/photo` ne modifie que le compte authentifie
  et synchronise son profil associe.
- V6 ajoute le logo de l'entreprise (`company_settings.logo_url`), affiche dans
  l'en-tete des dossiers et du PDF.

Les images des dossiers (diplomes, badges de certification, captures de tests) et
le logo de l'entreprise acceptent au choix une URL ou un fichier televerse. Le
televersement passe par `POST /api/media/image` (autorite `PROFILE_WRITE`) : le
fichier est valide (JPEG/PNG/WebP, 5 Mo, 16 Mpx max) puis stocke sur S3 sous
`dossier-assets/`, et l'URL retournee est enregistree avec le dossier ou les
parametres entreprise. Un objet orphelin est tolere si l'enregistrement n'a pas
lieu. Le PDF telecharge le logo configure (http/https) avec repli sur le logo
inoDoc embarque.

### Suppression des profils externes

`DELETE /api/profiles/{id}` (autorite `PROFILE_WRITE`, reserve aux administrateurs
et ingenieurs d'affaires) ne supprime qu'un profil **externe** non lie a un compte.
Un profil interne (lie a un compte) est refuse avec un message invitant a supprimer
le compte depuis Utilisateurs. Les versions du dossier sont supprimees en cascade
(JPA) et leurs liens de partage via la cascade `ON DELETE` en base. La suppression
d'une version individuelle reste disponible (sauf la derniere version).

### Module Entretiens (annuels / semi-annuels)

Tables ajoutees par la migration V7 : `reference_documents` (documents consultatifs),
`review_forms` (modeles de formulaire), `review_campaigns` (campagnes) et
`review_submissions` (reponses). Les schemas de formulaire et le contenu des
documents sont stockes en JSON.

- Les ingenieurs d'affaires et administrateurs creent des **modeles de formulaire**
  (sections + champs : question ouverte, choix unique/multiple, case a cocher,
  tableau de cases, ancre vers un document consultatif) et des **documents de
  reference** en lecture seule, puis lancent des **campagnes** (`OPEN`/`CLOSED`).
- Chaque interne remplit le formulaire d'une campagne ouverte : **une soumission
  par (campagne, utilisateur)** (contrainte d'unicite). Une reponse validee
  (`SUBMITTED`) devient definitive et passe en lecture seule.
- `POST /api/reviews/generate` (multipart, reserve aux gestionnaires) genere, via
  l'IA configuree, un schema de formulaire (`kind=FORM`) ou un document consultatif
  (`kind=REFERENCE`) a partir d'un PDF/Word, pour pre-remplir l'editeur.
- Les regles (gestionnaire vs interne, campagne ouverte, soumission unique et
  verrouillee) sont appliquees cote service ; l'API `/reviews/**` exige une
  authentification et des `@PreAuthorize` par operation.

### Module Carnet de carriere

Migration V8 : `career_templates` (modele partage) et `career_entries` (entrees
personnelles). Les ingenieurs d'affaires definissent **un modele** (schema JSON
d'une entree, reutilisant les memes types de champs que les entretiens) ; chaque
employe tient **son propre carnet** et y ajoute des entrees au fil des annees.
Les entrees sont strictement limitees a leur proprietaire (lecture et ecriture) ;
la modification du modele est reservee aux gestionnaires. API `/career/**`.

La signature personnelle est reservee au role `BUSINESS_MANAGER` : les autres
roles ne peuvent pas la modifier, ne la recoivent pas dans leur DTO et ne
l'ajoutent pas aux emails, meme si une ancienne signature existe en base.

### Certificats TLS du fournisseur IA

`AI_TLS_SKIP_CERTIFICATE_VERIFICATION` est a `true` par defaut, conformement au
parametrage demande. Cette option desactive la verification de la chaine du
certificat **uniquement pour le client IA**. Le controle du nom d'hote reste
actif. Un avertissement est journalise au demarrage : la cle API et les CV
peuvent etre interceptes. Ne pas utiliser ce reglage pour des donnees sensibles.

Pour reactiver la verification, definir `AI_TLS_SKIP_CERTIFICATE_VERIFICATION=false`.
Le magasin Java est utilise par defaut (`AI_TLS_TRUST_STORE_TYPE=JVM`).
Si le certificat d'entreprise est approuve dans Windows, utiliser
`AI_TLS_TRUST_STORE_TYPE=Windows-ROOT`. Sinon, fournir un magasin approuve avec
`AI_TLS_TRUST_STORE_TYPE=PKCS12` (ou `JKS`), `AI_TLS_TRUST_STORE` et
`AI_TLS_TRUST_STORE_PASSWORD`. Redemarrer le backend apres modification.
Ne pas ajouter de cle API ni de mot de passe de magasin au depot.

Les doublons historiques d'email Google (insensibles a la casse) ou de rattachement
de profil bloquent volontairement les contraintes uniques : les regulariser avant
deploiement, sans suppression automatique des donnees.

### Verification

Depuis un terminal autorise disposant de Java 17 et Maven, dans `inodoc-back` :

```powershell
mvn -B "-Dtest=AiHttpClientConfigTest,ShareLinkAccessTest,PublicShareControllerTest,UserAdministrationTest,UserDeletionIntegrationTest,UserAccessControlTest,MailIdentityTest,MailOAuthSessionTest,ResumeIngestionTest,ImageUploadTest" test
```

Puis, dans `inodoc-front` :

```powershell
npm.cmd run build
```

Les tests couvrent droits administratifs, protection du dernier admin, suppression
des dependances, partage 404/410, rattachement Google/session, signature envoyee,
extraction DOCX, appel IA simule, validation des images et rollback S3 simule.
Ils ne prouvent pas une connexion a un compte Google, un fournisseur IA ou un
bucket reels. Les scenarios manuels sont detailles dans le README du front.

Dans l'environnement d'implementation, Maven n'etait pas disponible dans le PATH
et l'execution npm etait bloquee par une strategie de groupe Windows. Aucun
resultat de tests Java ou de compilation de production n'est revendique.
Le serveur Angular de developpement a compile les changements et a permis des
controles navigateur avec reponses API simulees ; ils ne remplacent pas les
tests du backend ni les essais reels S3, Gmail ou IA.
