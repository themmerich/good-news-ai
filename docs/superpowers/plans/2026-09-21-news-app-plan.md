# News-App — Umsetzungsplan

Stand: 2026-09-21. Grundlage: [Entwurf](../specs/2026-09-21-news-app-design.md).

Vier Schritte, jeder für sich lauffähig, testbar und mergefähig. Gearbeitet wird auf
`feature/next01`; committet und gepusht wird erst auf Ansage, gemergt von Hand über GitHub.
Vor jedem Commit läuft `node scripts/verify.mjs` durch.

## Inhalt

- [Was für alle Schritte gilt](#was-für-alle-schritte-gilt)
- [Schritt 1 — Katalog und Auswahl](#schritt-1--katalog-und-auswahl) ✅
- [Schritt 2 — Quellen finden](#schritt-2--quellen-finden)
- [Schritt 3 — Quellen holen und anzeigen](#schritt-3--quellen-holen-und-anzeigen)
- [Schritt 4 — KI-Auswertung](#schritt-4--ki-auswertung)
- [Reihenfolge und Abhängigkeiten](#reihenfolge-und-abhängigkeiten)

## Was für alle Schritte gilt

**Migrationen.** `V1__create_schema.sql` ist die Baseline, `V2` kam mit Schritt 1. Schritt 2
bringt `V3` (die Spalte `type` an den Quellen), Schritt 3 bringt `V4` (Artikel und Läufe). Schritt
4 braucht keine: Die Spalten `positive_summary`, `ranking` und `processed_at` legt schon `V4` an,
nullable, und sie bleiben bis dahin leer. Eine spätere `ALTER TABLE` wäre unnötiger Verschleiß.

**Sheriff.** Die neue Domäne `news` passt ohne Konfigurationsänderung in
`src/app/domains/<domain>/<type>`. Nichts an `sheriff.config.ts` anzufassen ist das Ziel; wenn doch
etwas klemmt, ist das ein Hinweis auf einen falschen Schnitt, nicht auf eine zu enge Regel.

**Übersetzungen.** Jeder Schritt bringt seine Schlüssel in `public/i18n/de.json` und `en.json`
gleichzeitig ein. Deutsch ist die Vorgabesprache und das, wogegen die e2e-Tests prüfen.

**Testabdeckung.** Kein Schritt gilt als fertig ohne Komponententests je Seite, Controller-Tests je
Endpunktgruppe, einen Test auf Mandantentrennung und einen e2e-Test je neuer Seite.

## Schritt 1 — Katalog und Auswahl

**Erledigt**, gemergt als PR #1.

Ziel: Der Admin pflegt Kategorien und Quellen, der Benutzer wählt daraus. Noch kein Abruf, keine
Artikel, keine KI. Die Startseite bleibt vorerst die leere Testseite.

### Backend

1. **Migration `V2__create_news_catalog.sql`** — `categories`, `feeds`, `user_feeds` samt Indizes
   und Fremdschlüsseln mit `ON DELETE CASCADE` auf `tenants` beziehungsweise `users`. Eindeutig:
   `(tenant_id, parent_id, lower(name))` bei Kategorien, `(tenant_id, lower(url))` bei Quellen,
   `(user_id, feed_id)` bei der Auswahl.

2. **Paket `catalog`** — `Category` mit Selbstbezug über `parent`, `Feed` mit Bezug auf `Category`
   und `Tenant`, dazu die beiden Repositories. Die Regel „höchstens zwei Ebenen" und „Quellen nur an
   Blättern" sitzt in den Entities, nicht in den Controllern, damit sie nicht an einem zweiten
   Einstiegspunkt umgangen wird.

3. **`CategoryController`** — Baum lesen, anlegen, ändern, löschen. Löschen antwortet 409 mit
   `ConflictResponse`, solange Quellen daran hängen; die Klasse gibt es schon aus der
   Mandantenverwaltung. Antwortform: flache Liste mit `parentId` und `feedCount`, den Baum baut das
   Frontend. Eine flache Liste ist in JSON und in Tests leichter zu prüfen als verschachtelte
   Knoten.

4. **`FeedController`** — lesen, anlegen, ändern, löschen. `probe` kommt erst in Schritt 2, weil es
   einen Feed-Leser braucht; bis dahin trägt der Admin die Feed-URL selbst ein.

5. **Paket `subscriptions`** — `UserFeed`, Repository, `SubscriptionController` mit
   `GET /api/news/catalog` und `PUT /api/news/picks`. Der Katalog-Endpunkt liefert Kategorien und
   Quellen mitsamt einem `selected`-Kennzeichen je Quelle, damit die Seite mit einer Anfrage
   auskommt. `PUT` nimmt die vollständige Liste von Feed-Ids entgegen und ersetzt die bisherige
   Auswahl; Ids fremder Mandanten führen zu 400.

6. **`SecurityConfig`** — `/api/categories/**` und `/api/feeds/**` für `ADMIN` und `SUPERUSER`,
   `/api/news/**` für jeden angemeldeten Benutzer.

7. **Tests** — `CategoryControllerTest`, `FeedControllerTest`, `SubscriptionControllerTest` im Stil
   von `UserControllerTest`: MockMvc, Testcontainers, `@AsUser`. Je ein Fall für die
   Mandantentrennung (404 auf fremde Objekte), einer für die 409 beim Löschen einer belegten
   Kategorie, einer für die Tiefenbegrenzung des Baums.

### Frontend

8. **Domänengerüst** `src/app/domains/news` mit `api`, `feat-catalog`, `feat-picks`, `ui`, `data`,
   `model`. `feat-board` kommt in Schritt 2.

9. **`model/`** — `Category`, `Feed`, `CatalogNode`; dazu `buildTree()`, das aus der flachen Liste
   den Baum macht. Frameworkfrei, mit eigenen Tests. Hier gehören auch die Regeln hin, die die
   Oberfläche braucht: Ist das ein Blatt? Dürfen hier Quellen hängen?

10. **`data/`** — `CategoriesService`, `FeedsService`, `CatalogService` über `httpResource`, wie es
    `UsersService` und `TenantsService` vormachen.

11. **Seite Kategorien** (`feat-catalog`) — PrimeNG `p-tree` mit Drag & Drop für die Reihenfolge,
    Dialog zum Anlegen und Umbenennen, Löschen über `ConfirmationService`. An jedem Blatt steht die
    Zahl der Quellen.

12. **Seite Quellen** — `p-table` mit Name, Kategorie, URL. Anlegen und Ändern im Dialog, Kategorie
    als `p-select` über die Blätter. Signal Forms wie auf der Firmenseite.

13. **Seite Meine Auswahl** (`feat-picks`) — derselbe Baum mit Kästchen an den Quellen, ein Kästchen
    an der Kategorie wählt alle ihre Quellen. Speichern über einen Knopf; bei ungespeicherten
    Änderungen fragt ein `CanDeactivate`-Guard beim Verlassen nach.

14. **Routen und Navigation** — die drei Seiten in `app.routes.ts` (`/categories` und `/feeds` hinter
    `adminGuard`, `/picks` hinter `tenantGuard`), Sidebar-Gruppe von *Vorgänge* auf **Nachrichten**
    umbenennen, Einträge ergänzen.

15. **Tests** — je Seite ein Komponententest, `buildTree()` und die Auswahllogik als
    Modelltests, drei e2e-Specs mit gemockter API.

### Fertig, wenn

Ein Admin legt „Sport" mit den Unterkategorien „Fußball" und „Football" an, hängt an jede eine
Quelle, und ein normaler Benutzer wählt davon eine aus und findet sie nach dem Neuladen wieder.
`node scripts/verify.mjs` läuft durch.

## Schritt 2 — Quellen finden

Ziel: Der Admin fügt eine gewöhnliche Seiten-URL ein und bekommt die Feeds gezeigt, die es dazu
gibt. Findet sich keiner, kann er die Seite als Quelle vom Typ `PAGE` anlegen. Noch kein Abruf von
Artikeln — nur das Anlegen wird erwachsen.

### Backend

1. **Abhängigkeit** `com.rometools:rome`. Die Suche muss jeden Kandidaten parsen, um ihn zu
   bestätigen, und der Parser ist derselbe, den Schritt 3 zum Abrufen braucht.

2. **Migration `V3__add_source_type.sql`** — Spalte `type` an `feeds`, `NOT NULL DEFAULT 'FEED'`
   mit `CHECK (type IN ('FEED', 'PAGE'))`. Was schon im Katalog steht, ist ein Feed.

3. **`FeedReader`** (Paket `news`) — nimmt eine URL, liefert Titel und die Einträge mit `guid`,
   Titel, Link, Datum und Teaser. Zeitlimit zehn Sekunden, höchstens fünfzig Einträge. Wirft
   sprechende Ausnahmen, die als `last_error` taugen.

4. **`FeedFinder`** — die vier Schritte aus dem Entwurf: die URL selbst, `<link rel="alternate">`,
   ein Scan des geholten HTML nach Feed-Adressen, und eine Liste gängiger Pfade samt der Subdomain
   `rss.<domain>`. Jeder Kandidat wird über den `FeedReader` bestätigt, Dubletten fallen raus. Die
   Kandidaten werden parallel geprüft, sonst dauert die Suche so lange wie die Summe ihrer
   Zeitlimits.

5. **`POST /api/feeds/probe`** — nimmt eine URL, antwortet mit der Liste der bestätigten Feeds (je
   Adresse, Titel, Anzahl Einträge) oder der Angabe, dass keiner gefunden wurde.

6. **`FeedController` erweitern** — `type` in Anfrage und Antwort. Eine Quelle vom Typ `PAGE`
   nimmt jede http(s)-Adresse; eine vom Typ `FEED` wird beim Anlegen einmal geprüft und abgelehnt,
   wenn sich dahinter kein Feed parsen lässt.

7. **Tests** — `FeedReaderTest` gegen abgelegte XML-Dateien (RSS 2.0, Atom, kaputt).
   `FeedFinderTest` gegen abgelegte HTML-Dateien, eine je Fundweg, mit einem eingesetzten
   Abrufer statt echtem Netz: die vier Wege sind die Zusagen dieses Schrittes und gehören
   einzeln geprüft. Dazu der Fall „nichts gefunden". `FeedControllerTest` um `type` und die
   Prüfung beim Anlegen ergänzen.

### Frontend

8. **Dialog auf der Quellen-Seite umbauen** — statt eines Feld-URL-Feldes ein Suchfeld mit Knopf
   **Suchen**. Danach eine Liste der gefundenen Feeds zum Auswählen; der Titel wird als Name
   vorgeschlagen. Ohne Fund ein Hinweis und der Knopf **Seite direkt auslesen**.

9. **Typ in der Tabelle** — eine Spalte, die `FEED` und `PAGE` unterscheidet, damit man sieht,
   welche Quelle auf welchem Weg gelesen wird.

10. **Tests** — Komponententest für die drei Zustände des Dialogs (nichts gesucht, Feeds gefunden,
    nichts gefunden), e2e für den Weg von der eingefügten URL bis zur angelegten Quelle.

### Fertig, wenn

Der Admin fügt `heise.de` ein, bekommt zwei Feeds angeboten, wählt einen und findet ihn danach in
der Tabelle. Für `nfl.com` meldet die Suche nichts und bietet an, die Seite direkt auszulesen.

## Schritt 3 — Quellen holen und anzeigen

Ziel: Der Knopf holt die gewählten Quellen — Feeds wie Webseiten — und die Übersicht zeigt die
Meldungen in Tabs. Noch ohne KI: Die Karte zeigt den Originaltitel und den Teaser.

### Backend

1. **Migration `V4__create_articles_and_runs.sql`** — `articles` und `news_runs`. Die Spalten
   `positive_summary`, `ranking` und `processed_at` sind dabei und bleiben leer.

2. **`PageReader`** (Paket `news`) — das Gegenstück zum `FeedReader` aus Schritt 2, mit derselben
   Schnittstelle: URL rein, Einträge raus. Zwei Züge, wie im Entwurf beschrieben — Links
   einsammeln und filtern, dann je Kandidat den Kopf der Artikelseite lesen und `og:title`,
   `og:description` und `article:published_time` mitnehmen. Höchstens dreißig Kandidaten je Seite,
   die Abrufe nacheinander mit kurzer Pause. Findet der Filter nichts, ist das ein Fehler mit
   Ansage („keine Artikel gefunden"), kein stilles Nichts.

3. **`SourceReader` als gemeinsame Schnittstelle** — `FeedReader` und `PageReader` dahinter, die
   Auswahl trifft der `type` der Quelle. Ab dieser Stelle weiß der Rest der Anwendung nicht mehr,
   woher ein Artikel kam, und muss es auch nicht wissen.

4. **`ArticleStore`** — gleicht die gelesenen Einträge gegen die Tabelle ab: neue anlegen, bekannte
   stehen lassen, verschwundene löschen. Das ist die Aufbewahrungsregel aus dem Entwurf, an genau
   einer Stelle.

5. **`NewsRunner`** — legt den Lauf an, arbeitet die Quellen des auslösenden Benutzers ab, zählt
   mit. Läuft über einen `TaskExecutor`; `@EnableAsync` kommt an `GoodNewsApplication`. Ein Lauf
   älter als fünfzehn Minuten auf `RUNNING` wird beim nächsten Start auf `FAILED` gesetzt. Die
   KI-Stufe ist hier noch eine leere Methode, die Schritt 4 füllt.

6. **`NewsController`** — `POST /api/news/runs`, `GET /api/news/runs/{id}`, `GET /api/news/articles`
   (gruppiert nach Blatt-Kategorie; `minRanking` wird schon entgegengenommen und wirkt noch nicht,
   weil kein Artikel ein Ranking hat).

7. **Tests** — `PageReaderTest` gegen abgelegte HTML-Dateien: eine Übersichtsseite mit echten und
   unechten Links, eine Artikelseite mit Open-Graph-Angaben, eine ohne, und eine leere Hülle, wie
   sie eine per JavaScript gefüllte Seite hinterlässt. `ArticleStoreTest` für Anlegen, Behalten und
   Löschen. `NewsRunnerTest` für die Zähler, für eine kaputte Quelle mitten im Lauf, für den
   zweiten Start und dafür, dass beide Quellentypen im selben Lauf nebeneinander laufen.
   `NewsControllerTest` für die Endpunkte.

### Frontend

8. **Seite Übersicht** (`feat-board`) — `p-tabs`, ein Tab je Blatt-Kategorie mit Auswahl. Darüber
   der Knopf **Aktualisieren**; während eines Laufs an seiner Stelle der Fortschritt.

9. **`NewsRunStore`** — startet den Lauf, fragt alle zwei Sekunden nach und lädt die Artikel nach,
   solange sich der Zähler bewegt. NgRx Signals Store, wie `AuthStore` es vormacht.

10. **Artikelkarte** (`ui/`) — Titel als Link, Quelle, Zeitpunkt. Die positive Kernaussage und das
    Ranking kommen in Schritt 4 dazu; die Karte wird dafür erweitert, nicht ersetzt.

11. **Startseite umhängen** — die Übersicht wird die Route `''`, `domains/playground` fliegt raus,
    samt Testseite, Übersetzungsschlüsseln und e2e-Spec.

12. **Tests** — Komponententest für die Übersicht, Modelltest für die Gruppierung, e2e mit einem
    Lauf, der über zwei Abfragen von *läuft* auf *fertig* springt.

### Fertig, wenn

Ein Benutzer mit einem Feed und einer Webseite unter seinen Quellen drückt Aktualisieren, sieht den
Fortschritt und danach in den Tabs die aktuellen Meldungen aus beiden, jede mit Link auf das
Original. Eine Quelle mit falscher URL markiert sich auf der Quellen-Seite mit ihrem Fehler, ohne
den Lauf zu stoppen.

## Schritt 4 — KI-Auswertung

Ziel: Jede Meldung bekommt eine positiv formulierte Kernaussage und ein Ranking, und die Liste
lässt sich darüber eindampfen. Keine Migration.

### Backend

1. **`ArticleProcessor`** — nimmt bis zu zehn unverarbeitete Artikel, baut daraus einen Auftrag,
   ruft `ChatClients.forTenant(tenant)` und liest die Antwort über `.entity(...)` in einen Record.
   Kommen weniger Einträge zurück als hineingingen oder passt eine Nummer nicht, bleiben die
   betroffenen Artikel unverarbeitet.

2. **Der Auftrag an das Modell** — Kernaussage benennen, den konstruktiven Teil nach vorn, nichts
   hinzuerfinden, nichts beschönigen. Für das Ranking der Maßstab aus dem Entwurf: 0–3 Randnotiz,
   4–6 Alltägliches, 7–8 Bemerkenswertes, 9–10 Einschneidendes, gemessen innerhalb des Gebiets.
   Der Text gehört in eine eigene Datei unter `resources`, nicht in einen Java-String, damit man ihn
   ändern kann, ohne den Code zu lesen.

3. **`NewsRunner` erweitern** — die leere Stufe aus Schritt 2 ruft jetzt den Prozessor, Bündel für
   Bündel, und zählt nach jedem Bündel hoch.

4. **`minRanking` wirksam machen** — Artikel ohne Auswertung kommen bei jeder Schwelle mit.

5. **Kein KI-Zugang** — der Lauf endet als `FAILED` mit einem Text, der auf die Seite KI-Zugang
   verweist. Die Artikel sind dann geholt und stehen unbewertet da.

6. **Tests** — `ArticleProcessorTest` gegen `StubChatClients` aus den bestehenden Testquellen:
   Bündelung, Auslesen des Rankings, unvollständige Antwort, scheiternder Aufruf. Dazu der Fall
   ohne Zugang im `NewsRunnerTest`.

### Frontend

7. **Artikelkarte erweitern** — die Kernaussage wird die Überschrift, der Originaltitel rutscht
   darunter als Link, das Ranking steht als Zahl daneben. Artikel ohne Auswertung stehen am Ende
   mit dem Hinweis *noch nicht bewertet*.

8. **Ranking-Schwelle** — Auswahl über den Tabs, gilt für alle. Die gewählte Schwelle merkt sich
   der Browser, wie `ThemeService` und `UserColumnsService` es vormachen.

9. **Sortierung** — Ranking absteigend, bei Gleichstand nach Datum. Gehört in `model/` und bekommt
   dort seinen Test.

10. **Tests** — Modelltests für Sortierung und Filter, e2e für das Setzen der Schwelle.

### Fertig, wenn

Nach einem Lauf steht in jedem Tab die positive Kernaussage je Meldung mit ihrem Ranking, und eine
Schwelle von 7 lässt nur noch das Wesentliche stehen.

## Reihenfolge und Abhängigkeiten

Die Schritte bauen strikt aufeinander auf:

- Schritt 2 braucht den Katalog aus Schritt 1, um die gefundene Quelle irgendwo abzulegen.
- Schritt 3 braucht den Feed-Leser aus Schritt 2 und das Typ-Feld, das dort dazukommt.
- Schritt 4 braucht die Artikel aus Schritt 3, um sie zu bewerten.

Innerhalb eines Schrittes geht das Backend voran: Steht der Endpunkt, kann die Seite dagegen
gebaut werden, und die e2e-Tests mocken ohnehin. Was sich parallelisieren lässt, ist das
Modell-Paket im Frontend — `buildTree()` und die Filter hängen an keinem Endpunkt.

Nach jedem Schritt ein Commit-Satz und ein PR, den du von Hand mergst. `main` ist geschützt, ein
direkter Push geht ohnehin nicht.
