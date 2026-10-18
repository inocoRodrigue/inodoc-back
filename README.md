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
rester dans le meme navigateur. Par defaut, `SESSION_COOKIE_SAME_SITE=lax`.
Preferer front et back sur le meme site, par exemple deux sous-domaines d'un
meme domaine sous HTTPS. Pour des sites distincts, configurer
`SESSION_COOKIE_SAME_SITE=none` avec `SESSION_COOKIE_SECURE=true` ; CORS seul ne
suffit pas et le blocage des cookies tiers peut encore empecher le parcours.
En multi-instance, conserver l'affinite de session pendant OAuth.

## Precautions

- Utiliser deux cles Base64 aleatoires distinctes d'au moins 32 octets pour JWT et mail. Changer la cle mail necessite de reconnecter les comptes Google existants.
- Garder `SESSION_COOKIE_SECURE=true` sous HTTPS ; utiliser `false` uniquement pour le developpement local HTTP.
- Les donnees de demonstration et leur option d'activation ont ete supprimees ; un redemarrage ne supprime ni ne regenere les donnees.
- Garder `AI_TLS_SKIP_CERTIFICATE_VERIFICATION=false` ; ne transmettre a l'IA que des donnees autorisees.
- Autoriser le backend a lire, ecrire et supprimer les objets S3 ; sauvegarder la base avant les migrations.

## Sauvegarde, restauration et reset

### Initialisation

Au demarrage, les referentiels ne sont crees que si toutes les tables applicatives
sont vides (hors historique Flyway). Une base contenant deja des donnees, meme
partielles, ne recoit pas de nouveaux referentiels, sauf le compte administrateur
configure et son role ADMIN s'ils sont absents. Les nouveaux roles d'une version future devront
etre ajoutes par une migration explicite, pas par un script de demarrage.

Seuls les roles, catalogues de competences, parametres d'entreprise, activites CRA,
documents de reference et modeles de carnet/entretiens utilises sont initialises.
Aucun faux utilisateur, profil, dossier ou CRA n'est genere. A chaque demarrage,
le compte administrateur configure est cree si son email n'existe pas encore,
meme sur une base non vide. Un compte existant n'est ni modifie, ni reactive,
et son mot de passe n'est pas remplace. La creation exige un email et un mot de passe d'au moins 8 caracteres ;
sinon toute l'initialisation est annulee. Les initialisations PostgreSQL concurrentes
sont serialisees par un verrou transactionnel. Un reset recree ces referentiels,
mais conserve uniquement le compte administrateur appelant.

### Actions administrateur

Dans Parametres > Donnees, seul le role ADMIN peut exporter, importer ou
reinitialiser l'application. Les endpoints `/api/admin/application-data`
(`/export` en GET, `/import`, `/reset/request-code` et `/reset` en POST) sont
controles cote serveur.

- Le ZIP contient toutes les tables applicatives : comptes, roles, parametres,
	catalogues, profils, evaluations, parcours, declarations CRA de tous statuts
	et toutes annees, projets archives, historiques et relances. Pour les dossiers
	de competences, seuls les masters et leurs sous-elements/liens sont conserves.
- Les entretiens annuels incluent les documents de reference, les modeles
	personnalises, les campagnes (y compris archivees), leur schema de formulaire
	fige a la publication, les reponses et les partages. Le carnet de carriere
	inclut les modeles modifies, toutes les entrees enregistrees et leurs partages.
	Un import restaure les modeles et enregistrements tels qu'ils etaient lors de
	l'export, meme si les modeles ont ete modifies ou des entrees supprimees depuis.
- Les images S3 referencees, y compris dans les champs JSON, sont incluses.
	Une image inaccessible fait echouer l'export : aucun reset n'est alors effectue.
	Les fichiers externes uniquement lies par une URL ne sont pas telecharges.
- L'import attend le ZIP brut (`Content-Type: application/zip`) et remplace les
	donnees, sans fusion. Il exige le meme schema de base et au moins un compte
	administrateur actif. Toute erreur annule la transaction. Les medias sont
	recrees avec de nouvelles cles S3 et leurs URLs sont adaptees ; les originaux
	ne sont pas ecrases. Les nouveaux objets sont nettoyes en cas d'annulation.
- Le reset exige deux validations : une confirmation de la suppression puis
	un code recu par mail. `POST /reset/request-code` utilise un compte Google
	actif appartenant a l'administrateur connecte et envoie un code a six chiffres
	a l'adresse email de son compte inoDoc. Un compte Google doit donc etre
	connecte dans Mon compte ; sans expediteur disponible ou si l'envoi echoue,
	aucune reinitialisation n'est autorisee. La reponse contient `challengeId`,
	`expiresAt` et `email`, jamais le code.
- Le code expire apres 10 minutes, est limite a cinq essais et ne fonctionne
	qu'une fois, uniquement pour l'administrateur destinataire. Un renvoi est
	possible apres une minute et invalide le code precedent. Seule une empreinte
	du code est conservee en base. Les demandes temporaires sont exclues des ZIP
	et invalidees par un import/reset, pour ne jamais reactiver un ancien code.
- `POST /reset` exige `{ "confirmation": "REINITIALISER", "challengeId": "UUID",
	"code": "123456" }`. Le code est consomme avant le reset ; si celui-ci echoue,
	un nouveau code est necessaire. Le reset renvoie le ZIP d'avant
	reset, supprime les donnees puis recree les referentiels/projets par defaut.
	Seul le compte de l'administrateur appelant est conserve, sans profil ni
	autres roles. Aucune donnee de demonstration n'est recreee. Les anciennes
	images S3 ne sont pas supprimees par le reset.
- Apres import/reset, l'interface demande une reconnexion. Les sauvegardes
	comprennent les empreintes de mots de passe, jetons et donnees personnelles :
	le ZIP n'est pas chiffre et doit etre conserve dans un emplacement securise.
- Les secrets d'environnement, la configuration d'infrastructure et l'historique
	Flyway ne sont pas exportes. Conserver `INODOC_MAIL_SECRET_KEY` pour dechiffrer
	les comptes Google restaures ; sans cette cle, les reconnecter.
- Limite : 256 Mo pour le contenu JSON decompresse (medias encodes inclus) et
	pour le ZIP importe. Adapter aussi la limite du reverse proxy si necessaire.
	Les operations verrouillent les ecritures sur les tables pendant la transaction ;
	privilegier une fenetre de maintenance. Verifier la reception du ZIP : une
	coupure reseau apres validation du reset ne peut pas annuler celui-ci.

## Exploitation des CRA

- Les migrations V21 a V28 couvrent les demi-journees, les calendriers, l'historique, les relances et le client des projets. V25 retire les anciens reglages d'ouverture/cloture et d'echeance, sans supprimer l'historique des actions. V26 permet de retirer les projets sans supprimer leurs references historiques. V27 supprime la table des plannings individuels devenus inutilises. V28 ajoute les regles par date. Elles sont appliquees par Flyway au prochain demarrage du backend ; sauvegarder la base auparavant.
- Les activites ACCESS_ALL par defaut peuvent etre supprimees depuis les projets CRA, avec confirmation. Elles disparaissent des choix mais restent lisibles dans les anciens CRA et ne sont pas regenerees au demarrage.
- La page CRA n'ouvre aucune declaration automatiquement. Les onglets disposent des ancres `#ma-declaration`, `#a-valider`, `#valides`, `#suivi-mensuel`, `#exports`, `#statistiques` et `#regles-declaration`, utilisables directement dans un lien. Les exports et statistiques sont accessibles a Admin, Finance et Business Manager ; les regles de declaration sont reservees a l'Admin.
- Une activite peut couvrir une matinee, un apres-midi ou une journee sur deux projets. Les donnees existantes restent des journees entieres.
- La declaration couvre le mois entier, y compris les dates a venir, du lundi au vendredi, matin et apres-midi, hors jours feries et fermetures configures. Ce calendrier est commun a tous les collaborateurs, sans planning individuel. La modale n'affiche pas de compteur de jours attendus ; la soumission et la validation verifient la completude mensuelle.
- La creation d'un CRA est possible uniquement pour le mois courant, a raison d'une declaration par utilisateur et par mois. Il n'y a pas de periode a ouvrir ou fermer ni d'echeance de saisie. Un brouillon existant reste corrigeable ; Admin et Finance peuvent traiter les declarations soumises, y compris celles des mois precedents.
- Dans CRA > Suivi mensuel, Admin et Finance peuvent filtrer les collaborateurs et programmer des relances ciblees. L'onglet Statistiques presente l'etat des CRA, la completude, les jours saisis au fil du mois et la valorisation des CRA valides par client et projet. L'onglet Exports dispose de son propre selecteur de mois et du choix de recapitulatif ; les jours feries et fermetures se configurent dans l'onglet Regles de declaration.
- L'Admin peut configurer les jours feries/fermetures par dates et autoriser ou non l'auto-validation (desactivee par defaut).
- Dans Regles de declaration, chaque date peut definir une activite ACCESS_ALL imposee (ex. RTT employeur), un jour ferie ou un jour non travaille. Les anciennes dates de jours feries sont reprises dans l'editeur. Une seule regle est autorisee par date et chaque regle peut avoir un libelle.
- Une activite imposee est automatiquement renseignee en journee entiere pour les CRA non valides et verrouillee, y compris lors d'une copie de semaine. Un jour ferie, ferme ou non travaille est desactive et exclu des jours a renseigner et du decompte des activites. Le serveur refuse toute tentative de modification d'une date controlee.
- Les CRA deja valides conservent leurs activites enregistrees ; les regles courantes s'appliquent a nouveau apres une devalidation explicite. Les changements de regles sont historises avec leurs valeurs avant/apres.
- Un refus motive remet le CRA en brouillon pour correction, sans delai de reouverture.
- Les changements de statut et d'activites conservent un instantane avant/apres avec acteur, date et motif. L'historique d'un CRA est visible par son proprietaire et par Admin/Finance.
- Les relances sont persistantes, limitees a une par destinataire/periode/jour et traitees toutes les minutes. L'identite de l'expediteur est reverifiee ; un CRA deja soumis n'est plus relance. Les echecs sont repris au maximum trois fois et restent visibles dans le suivi.
- Les exports CSV, Excel et PDF couvrent les CRA valides d'un mois, en detail ou regroupes par client, projet ou collaborateur. Les exports de gestion ajoutent le TJM et le montant valorise (TJM x 0,5 jour par demi-journee) ; ces colonnes ne contiennent aucun taux pour un export personnel d'un role non habilite. Le CSV est UTF-8 avec BOM, separateur point-virgule et dates ISO ; l'Excel garde jours, TJM et montants en cellules numeriques. Le champ Client d'un projet client est facultatif ; renseigner les anciens projets pour obtenir un recapitulatif client complet.
- Ces fichiers sont des exports de rapprochement et de preparation, pas des imports natifs garantis. L'API PayFit documente le temps travaille par `contractId`, periode `YYYYMM`, unite jours/heures et scopes partenaires ; l'acces d'integration est soumis a validation PayFit. Boond publie une API externe, dont les objets et identifiants doivent etre relies a ceux du tenant. inoDoc n'a pas encore les identifiants de contrats/salaries ni les codes de variables specifiques a votre configuration : un import direct necessite ce mapping. References : [API Boond](https://doc.boondmanager.com/api-externe/), [temps travaille PayFit](https://developers.payfit.io/reference/get_contracts_worked_time.md), [absences PayFit](https://developers.payfit.io/reference/get_absences.md) et [conditions d'integration PayFit](https://developers.payfit.io/docs/integrations-requirements.md).

Verification : `mvn test` pour le backend ; `npm run test:cra`, `npm run check` et `npm run build` dans le frontend.
