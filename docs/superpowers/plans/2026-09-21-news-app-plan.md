# News-App — Umsetzungsplan

Stand: 2026-09-21. Grundlage: [Entwurf](../specs/2026-09-21-news-app-design.md).

Fünf Schritte, jeder für sich lauffähig, testbar und mergefähig. Gearbeitet wird auf einem
Feature-Branch je Schritt; committet und gepusht wird erst auf Ansage, gemergt von Hand über
GitHub. Vor jedem Commit läuft `node scripts/verify.mjs` durch.

Schritt 3 kam nach der Umstellung dazu, die Kategorie an die einzelne Meldung zu hängen statt an
die Quelle. Er baut zurück, was Schritt 1 an Baum und Quellen-Kategorie angelegt hat, und richtet
die Auswahl neu aus. Die alten Schritte 3 und 4 sind dadurch zu 4 und 5 geworden.

## Inhalt

- [Was für alle Schritte gilt](#was-für-alle-schritte-gilt)
- [Schritt 1 — Katalog und Auswahl](#schritt-1--katalog-und-auswahl) ✅
- [Schritt 2 — Quellen finden](#schritt-2--quellen-finden) ✅
- [Schritt 3 — Kategorien an die Meldung](#schritt-3--kategorien-an-die-meldung)
- [Schritt 4 — Quellen holen und anzeigen](#schritt-4--quellen-holen-und-anzeigen)
- [Schritt 5 — KI-Auswertung](#schritt-5--ki-auswertung)
- [Reihenfolge und Abhängigkeiten](#reihenfolge-und-abhängigkeiten)

## Was für alle Schritte gilt

**Migrationen.** `V1__create_schema.sql` ist die Baseline, `V2` kam mit Schritt 1, `V3` mit
Schritt 2 (die Spalte `type` an den Quellen). Schritt 3 bringt `V4` (Kategorien flach, Quellen
ohne Kategorie, Auswahl auf Kategorien umgestellt), Schritt 4 bringt `V5` (Artikel und Läufe).
Schritt 5 braucht keine: Die Spalten `positive_summary`, `ranking`, `category_id` und
`processed_at` legt schon `V5` an, nullable, und sie bleiben bis dahin leer. Eine spätere
`ALTER TABLE` wäre unnötiger Verschleiß.

**Sheriff.** Die neue Domäne `news` passt ohne Konfigurationsänderung in
`src/app/domains/<domain>/<type>`. Nichts an `sheriff.config.ts` anzufassen ist das Ziel; wenn doch
etwas klemmt, ist das ein Hinweis auf einen falschen Schnitt, nicht auf eine zu enge Regel.

**Übersetzungen.** Jeder Schritt bringt seine Schlüssel in `public/i18n/de.json` und `en.json`
gleichzeitig ein. Deutsch ist die Vorgabesprache und das, wogegen die e2e-Tests prüfen.

**Testabdeckung.** Kein Schritt gilt als fertig ohne Komponententests je Seite, Controller-Tests je
Endpunktgruppe, einen Test auf Mandantentrennung und einen e2e-Test je neuer Seite.

## Schritt 1 — Katalog und Auswahl

**Erledigt**, gemergt als PR #1. Was hier über den Kategorienbaum und die Kategorie an der Quelle
steht, beschreibt den damaligen Stand; Schritt 3 räumt beides wieder ab.

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
   `model`. `feat-board` kommt in Schritt 4.

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
    `adminGuard`, `/picks` hinter `tenantGuard`), Sidebar-Gruppe von _Vorgänge_ auf **Nachrichten**
    umbenennen, Einträge ergänzen.

15. **Tests** — je Seite ein Komponententest, `buildTree()` und die Auswahllogik als
    Modelltests, drei e2e-Specs mit gemockter API.

### Fertig, wenn

Ein Admin legt „Sport" mit den Unterkategorien „Fußball" und „Football" an, hängt an jede eine
Quelle, und ein normaler Benutzer wählt davon eine aus und findet sie nach dem Neuladen wieder.
`node scripts/verify.mjs` läuft durch.

## Schritt 2 — Quellen finden

**Erledigt**, gemergt als PR #2.

Ziel: Der Admin fügt eine gewöhnliche Seiten-URL ein und bekommt die Feeds gezeigt, die es dazu
gibt. Findet sich keiner, kann er die Seite als Quelle vom Typ `PAGE` anlegen. Noch kein Abruf von
Artikeln — nur das Anlegen wird erwachsen.

### Backend

1. **Abhängigkeit** `com.rometools:rome`. Die Suche muss jeden Kandidaten parsen, um ihn zu
   bestätigen, und der Parser ist derselbe, den Schritt 4 zum Abrufen braucht.

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

## Schritt 3 — Kategorien an die Meldung

Ziel: Eine Quelle hat keine Kategorie mehr, die Kategorienliste wird flach, und der Benutzer kreuzt
Kategorien an statt Quellen. Noch kein Abruf und keine Artikel — dieser Schritt richtet nur das
Modell aus, damit Schritt 4 darauf aufsetzen kann, statt es später auseinanderzunehmen.

### Backend

1. **Migration `V4__categories_per_article.sql`** — in dieser Reihenfolge:

   - Die Kinder auf die oberste Ebene heben. Wo dadurch zwei Namen kollidieren würden, bekommt das
     gehobene Kind den Namen seiner alten Oberkategorie vorangestellt. Eine Zeile SQL, die man
     wahrscheinlich nie braucht — der Demo-Seeder legt keine Kategorien an —, aber eine
     fehlschlagende Migration auf einer gewachsenen Entwicklerdatenbank ist teurer.
   - `categories.parent_id` samt Index löschen, die beiden Teilindizes auf den Namen durch einen
     gewöhnlichen eindeutigen Index auf `(tenant_id, lower(name))` ersetzen.
   - `feeds.category_id` samt Index löschen.
   - `user_feeds` löschen, `user_categories` anlegen: `id`, `user_id`, `category_id`, eindeutig als
     Paar, beide Fremdschlüssel mit `ON DELETE CASCADE`.

2. **`Category` entkernen** — `parent`, `isTopLevel()`, `moveTo(parent, sortOrder)` und
   `requireAssignableParent` fallen weg. Übrig bleiben Name und Reihenfolge. Der Klassenkommentar
   erklärt künftig nicht mehr den Baum, sondern wofür die Liste da ist: Sie ist der Wortschatz, aus
   dem die KI jede Meldung einsortiert.

3. **`Feed` entkernen** — `category` raus, aus dem Konstruktor und aus `update`.

4. **`CategoryController`** — `parentId` und `feedCount` fallen aus `CategoryRequest` und
   `CategoryResponse`, `CategoryFeedCount` wird gelöscht. Die Tiefenprüfung entfällt. Das Löschen
   antwortet nie mehr 409, weil keine Quelle mehr an einer Kategorie hängt; die Eindeutigkeit des
   Namens gilt jetzt je Mandant statt je Elternkategorie.

5. **`FeedController`** — `categoryId` aus Anfrage und Antwort. Damit fällt auch die Vorbedingung
   weg, dass es erst eine Kategorie geben muss, bevor man eine Quelle anlegen kann.

6. **Paket `subscriptions` umbauen** — `UserFeed` wird `UserCategory`, das Repository wandert mit.
   `CatalogResponse` weicht einem `PickedCategoryResponse` aus `id`, `name`, `sortOrder` und
   `selected`; `PicksRequest` nimmt `categoryIds` statt `feedIds`. `GET /api/news/catalog` wird
   `GET /api/news/categories`. Die Regel „fremde Id ergibt 400" bleibt, wie sie ist.

7. **Tests** — `CategoryControllerTest` verliert den Fall mit der Tiefenbegrenzung und den mit der
   409 beim Löschen einer belegten Kategorie und bekommt dafür einen auf den doppelten Namen
   innerhalb eines Mandanten und einen darauf, dass Löschen jetzt durchgeht.
   `FeedControllerTest` verliert alles rund um die Kategorie. `SubscriptionControllerTest` wird auf
   Kategorien umgeschrieben, samt dem Fall mit der fremden Kategorie-Id. Die Tests auf
   Mandantentrennung bleiben überall stehen.

### Frontend

8. **`model/category.ts` kürzen** — `Category` verliert `parentId` und `feedCount`,
   `CategoryInput` verliert `parentId`, `Feed` verliert `categoryId` und `categoryName`,
   `FeedInput` verliert `categoryId`. `CatalogCategory` und `CatalogFeed` weichen einem
   `PickedCategory` aus `id`, `name`, `sortOrder` und `selected`.

9. **`model/category-tree.ts` löschen**, samt Spec. `buildTree`, `isLeaf`, `leaves` und
   `siblingPosition` haben nichts mehr zu ordnen. An ihre Stelle tritt ein `sortCategories()` nach
   Reihenfolge und bei Gleichstand nach Namen, das die Sortierregel und ihren Test erbt.

10. **Seite Kategorien umbauen** — `p-table` statt `p-tree`: Name, Reihenfolge, die Knöpfe hoch und
    runter, Dialog zum Anlegen und Umbenennen. Kein Elternfeld, kein „Unterkategorie anlegen", kein
    Drag & Drop über Ebenen. Unter der Tabelle der Satz dazu, dass die Liste der Wortschatz der KI
    ist. Löschen fragt weiterhin nach, kann aber nicht mehr abgelehnt werden.

11. **Seite Quellen** — Spalte Kategorie und das Auswahlfeld im Dialog raus.

12. **Seite Meine Auswahl umbauen** — eine flache Liste mit einem Kästchen je Kategorie. Die
    verschachtelte Anzeige, die Kästchen an den Quellen und die halb angehakten Kategorien
    entfallen; Speichern-Knopf und der `CanDeactivate`-Guard bleiben unverändert. Dazu der Hinweis,
    dass die Auswahl nur die Anzeige ordnet und nichts einspart.

13. **Übersetzungen** — raus: `categories.createChild`, `.parent`, `.parentHint`,
    `.parentPinned`, `.feedCount`, `.hasFeeds` sowie `feeds.category`, `.categoryRequired`,
    `.noCategories`. Neu: der Hinweistext unter der Kategorientabelle, der Hinweis auf der
    Auswahlseite und `picks.noCategories`. Deutsch und Englisch gleichzeitig.

14. **Tests** — die drei Komponententests und die drei e2e-Specs `categories`, `feeds` und `picks`
    auf die neue Oberfläche ziehen. `sortCategories()` bekommt den Modelltest, den
    `category-tree.spec.ts` abgibt.

### Fertig, wenn

Ein Admin pflegt eine flache Liste aus Politik, Soziales und Sport, legt eine Quelle ohne Kategorie
an und löscht eine Kategorie, ohne dass ihn etwas daran hindert. Ein normaler Benutzer kreuzt zwei
Kategorien an und findet sie nach dem Neuladen wieder. `node scripts/verify.mjs` läuft durch.

## Schritt 4 — Quellen holen und anzeigen

Ziel: Der Knopf holt die gewählten Quellen — Feeds wie Webseiten — und die Übersicht zeigt die
Meldungen in Tabs. Noch ohne KI: Die Karte zeigt den Originaltitel und den Teaser.

### Backend

1. **Migration `V5__create_articles_and_runs.sql`** — `articles` und `news_runs`. Die Spalten
   `positive_summary`, `ranking`, `category_id` und `processed_at` sind dabei und bleiben leer.
   `articles.category_id` zeigt auf `categories` mit `ON DELETE SET NULL`. `news_runs` hat kein
   `user_id`: Ein Lauf gehört dem Mandanten. Der Index auf die laufenden Läufe ist eindeutig und
   partiell — je Mandant höchstens ein `RUNNING` —, weil zwei gleichzeitige Knopfdrücke sonst ein
   Wettrennen sind, das keine Prüfung vorher schließt.

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

5. **`NewsRunner`** — legt den Lauf an, arbeitet **alle Quellen des Mandanten** ab, zählt mit.
   Läuft über den `applicationTaskExecutor`, ausdrücklich benannt, weil der Kontext zwei
   `TaskExecutor` hält. Kein `@EnableAsync`: Ohne `@Async`-Annotationen täte es nichts, und der
   Lauf wird direkt auf den Executor gelegt. Ein zweiter
   Start liefert den laufenden Lauf zurück, gleich wer drückt. Ein Lauf älter als fünfzehn Minuten
   auf `RUNNING` wird beim nächsten Start auf `FAILED` gesetzt. Die KI-Stufe ist hier noch eine
   leere Methode, die Schritt 5 füllt.

6. **`Feed` um den letzten Abruf erweitern** — `last_fetched_at` und `last_error` stehen seit
   `V2` in der Tabelle und waren nie abgebildet; ab hier tragen sie etwas. Die Quellen-Seite
   markiert damit eine Quelle, deren letzter Versuch scheiterte.

7. **`NewsController`** — `POST /api/news/runs`, `GET /api/news/runs/{id}`, `GET /api/news/articles`
   (mit der Kategorie je Artikel; `minRanking` wird schon entgegengenommen). Beides wirkt in diesem
   Schritt noch nicht: Ohne KI hat kein Artikel ein Ranking und keine Kategorie, also liegt alles
   unter _Sonstiges_. Das ist der ehrliche Zwischenstand und genau das, was der Tab abdecken soll.

8. **Tests** — `JsoupPageReaderTest` gegen abgelegte HTML-Dateien: eine Übersichtsseite mit echten und
   unechten Links, eine Artikelseite mit Open-Graph-Angaben, eine ohne, und eine leere Hülle, wie
   sie eine per JavaScript gefüllte Seite hinterlässt. `ArticleStoreTest` für Anlegen, Behalten und
   Löschen. `NewsRunnerTest` für die Zähler, für eine kaputte Quelle mitten im Lauf, für den
   zweiten Start und dafür, dass beide Quellentypen im selben Lauf nebeneinander laufen.
   `NewsControllerTest` für die Endpunkte, darunter der zweite Start durch einen anderen Benutzer
   desselben Mandanten.

### Frontend

9. **Seite Übersicht** (`feat-board`) — `p-tabs`, ein Tab je angekreuzter Kategorie, in der etwas
   liegt, und dahinter _Sonstiges_ für die Meldungen ohne Zuordnung. Wer nichts angekreuzt hat,
   bekommt alle. Darüber der Knopf **Aktualisieren**; während eines Laufs an seiner Stelle der
   Fortschritt. Die Gruppierung samt dem Eimer _Sonstiges_ sitzt in `model/` und wird dort
   frameworkfrei geprüft.

10. **`NewsRunStore`** — startet den Lauf, fragt alle zwei Sekunden nach und lädt die Artikel nach,
    solange sich der Zähler bewegt. NgRx Signals Store, wie `AuthStore` es vormacht.

11. **Artikelkarte** (`ui/`) — Titel als Link, Quelle, Zeitpunkt. Die positive Kernaussage und das
    Ranking kommen in Schritt 5 dazu; die Karte wird dafür erweitert, nicht ersetzt.

12. **Startseite umhängen** — die Übersicht wird die Route `''` und löst die Weiterleitung auf
    `/picks` ab, die Schritt 3 dort hinterlassen hat. Die Testseite ist schon in Schritt 3
    geflogen. Jede e2e-Spec, deren Weg über die Wurzel läuft, mockt ab hier auch
    `/api/news/articles`.

13. **Tests** — Komponententest für die Übersicht, Modelltest für die Gruppierung samt dem Eimer
    _Sonstiges_ und der Sortierung, e2e mit einem Lauf, der über zwei Abfragen von _läuft_ auf
    _fertig_ springt.

### Fertig, wenn

Ein Mandant mit einem Feed und einer Webseite unter seinen Quellen: Ein Benutzer drückt
Aktualisieren, sieht den Fortschritt und danach unter _Sonstiges_ die aktuellen Meldungen aus
beiden, jede mit Link auf das Original. Drückt währenddessen ein zweiter Benutzer, sieht er
denselben Fortschritt. Eine Quelle mit falscher URL markiert sich auf der Quellen-Seite mit ihrem
Fehler, ohne den Lauf zu stoppen.

## Schritt 5 — KI-Auswertung

Ziel: Jede Meldung bekommt eine positiv formulierte Kernaussage, eine Kategorie und ein Ranking,
und die Liste lässt sich darüber eindampfen. Erst hier füllen sich die Tabs. Keine Migration.

### Backend

1. **`ArticleProcessor`** — nimmt bis zu zehn unverarbeitete Artikel, baut daraus einen Auftrag,
   ruft `ChatClients.forTenant(tenant)` und liest die Antwort über `.entity(...)` in einen Record.
   In den Auftrag gehen zusätzlich die Kategorienamen des Mandanten, zurück kommt je Artikel einer
   davon. Der Name wird ohne Rücksicht auf Groß- und Kleinschreibung gegen die Liste abgeglichen;
   was nicht trifft, lässt `category_id` leer. Kommen weniger Einträge zurück als hineingingen oder
   passt eine Nummer nicht, bleiben die betroffenen Artikel unverarbeitet.

2. **Der Auftrag an das Modell** — Kernaussage benennen, den konstruktiven Teil nach vorn, nichts
   hinzuerfinden, nichts beschönigen. Aus der mitgegebenen Liste die **engste** passende Kategorie
   wählen, ersatzweise die nächstweitere, und ausdrücklich keine, wenn nichts passt. Damit
   erledigt sich „Miami Dolphins, sonst NFL, sonst Sport" ohne Hierarchie im Datenmodell: Dass die
   Dolphins ein NFL-Team sind, weiß das Modell selbst. Für das Ranking der Maßstab aus dem Entwurf: 0–3
   Randnotiz, 4–6 Alltägliches, 7–8 Bemerkenswertes, 9–10 Einschneidendes, gemessen innerhalb der
   gerade vergebenen Kategorie. Der Text gehört in eine eigene Datei unter `resources`, nicht in
   einen Java-String, damit man ihn ändern kann, ohne den Code zu lesen. Die Platzhalter werden
   von Hand ersetzt statt über eine Template-Maschine: Titel und Teaser kommen aus dem offenen
   Internet und bringen beliebige Zeichen mit, und ein Renderer, der geschweifte oder spitze
   Klammern für Syntax hält, scheitert an der ersten Meldung über Quelltext.

3. **`NewsRunner` erweitern** — die leere Stufe aus Schritt 4 ruft jetzt den Prozessor, Bündel für
   Bündel, und zählt nach jedem Bündel hoch.

4. **`minRanking` wirksam machen** — Artikel ohne Auswertung kommen bei jeder Schwelle mit.

5. **Kein KI-Zugang** — der Lauf endet als `FAILED` mit einem Text, der auf die Seite KI-Zugang
   verweist. Die Artikel sind dann geholt und stehen unbewertet unter _Sonstiges_. Erkannt wird
   der Fall daran, dass **jedes** Bündel gescheitert ist; ein einzelnes gescheitertes Bündel lässt
   den Lauf als `DONE` enden. Den Fehlertext des Anbieters auszuwerten, um es genauer zu
   unterscheiden, wäre eine Abhängigkeit von etwas, das uns nicht gehört.

6. **Tests** — `ArticleProcessorTest` gegen `StubChatClients` aus den bestehenden Testquellen:
   Bündelung, Auslesen des Rankings, Zuordnung der Kategorie über den Namen samt abweichender
   Groß- und Kleinschreibung, ein unbekannter Kategoriename, eine leer gelassene Kategorie,
   unvollständige Antwort, scheiternder Aufruf. Dazu der Fall ohne Zugang im `NewsRunnerTest`,
   nach dem alles unter _Sonstiges_ liegt.

### Frontend

7. **Artikelkarte erweitern** — die Kernaussage wird die Überschrift, der Originaltitel rutscht
   darunter als Link, das Ranking steht als Zahl daneben. Artikel ohne Auswertung stehen am Ende
   mit dem Hinweis _noch nicht bewertet_.

8. **Ranking-Schwelle** — Auswahl über den Tabs, gilt für alle. Die gewählte Schwelle merkt sich
   der Browser, wie `ThemeService` und `UserColumnsService` es vormachen.

9. **Sortierung** — Ranking absteigend, bei Gleichstand nach Datum. Gehört in `model/` und bekommt
   dort seinen Test.

10. **Hinweis auf der Kategorienseite nachziehen** — `categories.vocabularyHint` warnt heute vor
    Begriffen, die sich überschneiden. Mit der Regel „die engste gewinnt" sind ineinanderliegende
    Begriffe erwünscht; schlecht sind nur die, die sich kreuzen. Der Satz muss das sagen, sonst
    rät er zum Gegenteil dessen, was der Auftragstext tut.

11. **Tests** — Modelltests für Sortierung und Filter, e2e für das Setzen der Schwelle. Dazu im
    `ArticleProcessorTest` der Fall, dass bei „Sport", „NFL" und „Miami Dolphins" in der Liste die
    engste gewählt wird.

### Fertig, wenn

Ein Feed von t-online.de landet nach einem Lauf verteilt in den Tabs Politik, Soziales und Sport,
je Meldung mit positiver Kernaussage und Ranking. Eine Schwelle von 7 lässt nur noch das
Wesentliche stehen, und was die KI nicht einordnen konnte, steht unter _Sonstiges_.

## Reihenfolge und Abhängigkeiten

Die Schritte bauen strikt aufeinander auf:

- Schritt 2 braucht den Katalog aus Schritt 1, um die gefundene Quelle irgendwo abzulegen.
- Schritt 3 räumt das Modell aus Schritt 1 um. Er muss vor Schritt 4 liegen: Sonst entstehen
  `articles` und die Übersicht gegen den Baum und die Quellen-Kategorie und müssten gleich darauf
  wieder auseinandergenommen werden.
- Schritt 4 braucht den Feed-Leser aus Schritt 2, das Typ-Feld von dort und die flache
  Kategorienliste aus Schritt 3.
- Schritt 5 braucht die Artikel aus Schritt 4, um sie zu bewerten, und die Kategorienliste, aus der
  das Modell wählt.

Innerhalb eines Schrittes geht das Backend voran: Steht der Endpunkt, kann die Seite dagegen
gebaut werden, und die e2e-Tests mocken ohnehin. Was sich parallelisieren lässt, ist das
Modell-Paket im Frontend — `buildTree()` und die Filter hängen an keinem Endpunkt.

Schritt 3 ist der einzige, der Bestehendes abräumt statt etwas anzubauen. Im Backend geht es
voran wie sonst; im Frontend hängen die drei Seiten und das Modell-Paket so eng an denselben
Typen, dass sie in einem Zug fallen — erst `model/`, dann die Seiten, dann die Übersetzungen und
Tests.

Nach jedem Schritt ein Commit-Satz und ein PR, den du von Hand mergst. `main` ist geschützt, ein
direkter Push geht ohnehin nicht.
