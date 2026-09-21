# News-App — Entwurf

Stand: 2026-09-21. Status: abgestimmt. Schritt 1 (Katalog und Auswahl) ist umgesetzt; die
Quellensuche und der Typ `PAGE` kamen nach einer Rückfrage dazu und stehen noch aus.

<!-- Das Dokument ist auf Deutsch, weil es die Vorlage für eine Entscheidung ist und das
     Gespräch dazu auf Deutsch lief. Code, Kommentare und Commits im Repo bleiben Englisch. -->

## Inhalt

- [Worum es geht](#worum-es-geht)
- [Getroffene Entscheidungen](#getroffene-entscheidungen)
- [Quellen finden](#quellen-finden)
- [Bausteine](#bausteine)
- [Datenmodell](#datenmodell)
- [Endpunkte](#endpunkte)
- [Ablauf eines Laufs](#ablauf-eines-laufs)
- [Wie eine Webseite gelesen wird](#wie-eine-webseite-gelesen-wird)
- [Der KI-Aufruf](#der-ki-aufruf)
- [Die drei Seiten](#die-drei-seiten)
- [Verhalten im Fehlerfall](#verhalten-im-fehlerfall)
- [Tests](#tests)
- [Bewusst nicht enthalten](#bewusst-nicht-enthalten)

## Worum es geht

Ein Admin pflegt einen Katalog aus Nachrichtenkategorien und Quellen. Eine Quelle ist im Regelfall
ein RSS- oder Atom-Feed; wo eine Seite keinen anbietet, wird die Seite selbst gelesen. Jeder
Benutzer wählt aus dem Katalog die Quellen, die ihn interessieren. Auf Knopfdruck holt der Server
sie, schickt jede Meldung durch die KI und zeigt das Ergebnis auf einer Seite an, ein Tab je
Kategorie.

Die KI macht zwei Dinge pro Meldung: sie zieht die Kernaussage heraus und formuliert sie positiv,
und sie vergibt ein Ranking von 0 (unwichtig) bis 10 (sehr wichtig). Über das Ranking lässt sich
die Liste eindampfen, etwa auf alles ab 7.

## Getroffene Entscheidungen

| Frage                           | Entscheidung                          | Warum                                                                                                |
| ------------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Woher die Nachrichten kommen    | RSS und Atom, ersatzweise die Webseite | Ein Feed ist strukturiert, stabil und rechtlich unbedenklich und bleibt deshalb der Normalfall. Wo keiner existiert, wird die Seite selbst gelesen — siehe [Quellen finden](#quellen-finden). |
| Wem der Katalog gehört          | dem Mandanten, gepflegt vom Admin     | Der Benutzer wählt aus dem Pool, legt aber nichts an.                                                 |
| Wann geholt wird                | auf Knopfdruck                        | Kein Scheduler in der ersten Fassung.                                                                 |
| Wie der Knopf sich verhält      | Lauf im Hintergrund, Frontend fragt nach | Ein Lauf dauert bis zu einer Minute. Das überlebt keine synchrone HTTP-Antwort, und man will Fortschritt sehen. |
| Wie lange Artikel bleiben       | nur was aktuell im Feed steht         | Kleinste Datenmenge. Preis: Eine Meldung, die aus dem Feed fällt und später wiederkehrt, kostet erneut einen KI-Aufruf. |

## Quellen finden

Der Admin fügt eine gewöhnliche Seiten-URL ein, nicht die Adresse eines Feeds. Feed-Adressen
kennt kaum jemand auswendig, und die Seiten verstecken sie: von vier Beispielseiten verlinkt genau
eine ihren Feed dort, wo man ihn vermutet.

Der Server sucht in vier Schritten und prüft jeden Fund, indem er ihn holt und mit dem Feed-Leser
zu parsen versucht:

1. **Ist die URL selbst schon ein Feed?**
2. **`<link rel="alternate">` im Kopf der Seite** — der vorgesehene Weg, den kaum noch jemand geht.
3. **Das geholte HTML nach Adressen durchsuchen**, die auf `.xml` oder `.rdf` enden oder `rss`,
   `feed` oder `atom` enthalten.
4. **Gängige Pfade und Hosts durchprobieren**: `/feed`, `/rss`, `/rss.xml`, `/feed.xml`,
   `/atom.xml`, `/index.xml`, `/?feed=rss2` sowie die Subdomain `rss.<domain>`.

Was sich als Feed parsen lässt, kommt mit Titel und Anzahl der Einträge in eine Liste; der Admin
wählt daraus aus. Findet sich nichts, bietet die Seite an, **die Webseite selbst auszulesen** — die
Quelle bekommt dann den Typ `PAGE` statt `FEED`.

An vier Seiten geprüft, Stand 2026-09-21:

| Seite          | Ergebnis                                        |
| -------------- | ----------------------------------------------- |
| `t-online.de`  | Schritt 2 — der Feed steht ordentlich im Kopf.  |
| `heise.de`     | Schritt 3 — die Startseite schweigt, aber das Seitengerüst jeder Fehlerseite nennt beide Feeds. |
| `golem.de`     | Schritt 4 — die eigene RSS-Übersicht wird per JavaScript nachgeladen und ist für den Server leer; `rss.golem.de` liefert direkt einen Feed. |
| `nfl.com`      | nichts gefunden — diese Quelle wird eine vom Typ `PAGE`. |

## Bausteine

### Backend

Drei Pakete unter `de.prime_ux.goodnews`, neben den bestehenden:

| Paket           | Aufgabe                                                                            |
| --------------- | ---------------------------------------------------------------------------------- |
| `catalog`       | `Category` und `Feed` — was der Admin pflegt, samt Verwaltungs-Endpunkten.          |
| `subscriptions` | Was ein Benutzer aus dem Katalog gewählt hat.                                       |
| `news`          | Feed abrufen, Artikel ablegen, KI aufrufen, Lauf verwalten, Liste ausliefern.       |

Neue Abhängigkeit: `com.rometools:rome` zum Lesen der Feeds. Java bringt keinen RSS-Parser mit,
und Rome versteht RSS 0.9 bis 2.0 und Atom über dieselbe Schnittstelle.

Die KI-Aufrufe laufen über `ChatClients.forTenant(tenant)`. Damit greift der Schlüssel, den der
Mandant auf der Seite **KI-Zugang** hinterlegt hat; ohne eigenen Schlüssel die Plattform-Zugangsdaten.
Diese Weiche steht bereits und wird nicht angefasst.

### Frontend

Eine neue Domäne `src/app/domains/news`, aufgeteilt nach den Sheriff-Kategorien des Projekts:

```text
api/           Routen
feat-catalog/  Admin: Kategorien und Quellen pflegen
feat-picks/    Benutzer: aus dem Katalog auswählen
feat-board/    Die Übersicht mit den Tabs
ui/            Artikelkarte, Kategorienbaum, Quellenliste
data/          REST-Clients und Stores
model/         Typen, Gruppierung, Ranking-Filter (frameworkfrei)
```

`domains/playground` mit der leeren Testseite entfällt, sobald die Übersicht steht.

### Navigation

Die Sidebar-Gruppe heißt künftig **Nachrichten** statt Vorgänge und enthält *Übersicht* und
*Meine Auswahl*. Unter **Administration** kommen *Kategorien* und *Quellen* dazu.

## Datenmodell

Fünf Tabellen, alle am Mandanten hängend und mit `ON DELETE CASCADE` daran, wie die bestehenden.

### `categories`

`id`, `tenant_id`, `parent_id`, `name`, `sort_order`, `created_at`

Leeres `parent_id` heißt Oberkategorie. Mehr als zwei Ebenen sind nicht erlaubt; geprüft wird das
im Code, weil SQL das nur umständlich ausdrückt. Der Name ist je Mandant und Elternkategorie
eindeutig, ohne Rücksicht auf Groß- und Kleinschreibung.

Eine Kategorie ohne Kinder ist ein **Blatt**. Nur an Blättern hängen Quellen, und nur Blätter
werden zu Tabs.

### `feeds`

`id`, `tenant_id`, `category_id`, `name`, `url`, `type`, `last_fetched_at`, `last_error`,
`created_at`

`category_id` zeigt auf ein Blatt. Die URL ist je Mandant eindeutig, damit dieselbe Quelle nicht
mehrfach geholt wird. `last_error` hält fest, woran der letzte Abruf gescheitert ist, und ist
leer, solange alles ging.

`type` ist `FEED` oder `PAGE` und entscheidet, welcher Leser die Quelle anfasst. Alles andere an
der Quelle ist für beide gleich, und was hinten herauskommt — ein Artikel mit Titel, Link, Datum
und Teaser — ebenfalls. Deshalb merkt der Rest der Anwendung von dem Unterschied nichts.

Tabelle und Entity heißen weiterhin `feeds` und `Feed`, obwohl die Oberfläche von **Quellen**
spricht und eine Quelle nun auch eine Webseite sein kann. Das ist bewusst so gelassen worden; wer
den Code liest, sollte es wissen.

### `user_feeds`

`id`, `user_id`, `feed_id` — eindeutig als Paar.

Mehr braucht die Auswahl nicht. Eine Kategorie gilt als gewählt, sobald eine ihrer Quellen gewählt
ist. Eine eigene Tabelle für gewählte Kategorien wäre doppelte Wahrheit.

### `articles`

`id`, `feed_id`, `guid`, `title`, `link`, `published_at`, `teaser`, `positive_summary`, `ranking`,
`processed_at`, `fetched_at`

`feed_id` und `guid` zusammen eindeutig. Die `guid` ist das, was der Feed als Kennung mitgibt,
ersatzweise der Link. `positive_summary` und `ranking` sind leer, bis die KI den Artikel gesehen
hat; `ranking` liegt zwischen 0 und 10.

Artikel hängen am Feed, nicht am Benutzer. Zwei Benutzer, die dieselbe Quelle gewählt haben, teilen
sich die Artikel und damit die KI-Kosten.

### `news_runs`

`id`, `tenant_id`, `user_id`, `status`, `total_articles`, `processed_articles`, `started_at`,
`finished_at`, `error`

`status` ist `RUNNING`, `DONE` oder `FAILED`.

## Endpunkte

### Katalog, nur für Admins

| Methode  | Pfad                   | Zweck                                                          |
| -------- | ---------------------- | -------------------------------------------------------------- |
| `GET`    | `/api/categories`      | Der Baum, mit der Zahl der Quellen je Blatt.                    |
| `POST`   | `/api/categories`      | Anlegen, wahlweise unter einer Oberkategorie.                   |
| `PUT`    | `/api/categories/{id}` | Umbenennen, verschieben, umsortieren.                           |
| `DELETE` | `/api/categories/{id}` | Löschen. 409, solange Quellen daran hängen.                     |
| `GET`    | `/api/feeds`           | Alle Quellen des Mandanten, mit Kategorie und letztem Fehler.   |
| `POST`   | `/api/feeds`           | Anlegen.                                                        |
| `PUT`    | `/api/feeds/{id}`      | Ändern, auch die Kategorie.                                     |
| `DELETE` | `/api/feeds/{id}`      | Löschen, Artikel gehen mit.                                     |
| `POST`   | `/api/feeds/probe`     | Feeds zu einer Seiten-URL suchen, siehe [Quellen finden](#quellen-finden). |

`probe` nimmt eine beliebige Seiten-URL entgegen und antwortet mit allen Feeds, die sich dazu
finden und parsen ließen — je Feed die Adresse, der Titel und die Zahl der Einträge. Findet sich
keiner, sagt die Antwort das, und die Seite bietet an, die Quelle als `PAGE` anzulegen.

### Auswahl und Übersicht, für jeden angemeldeten Benutzer

| Methode | Pfad                    | Zweck                                                                     |
| ------- | ----------------------- | ------------------------------------------------------------------------- |
| `GET`   | `/api/news/catalog`     | Der Baum mit allen Quellen und der Angabe, welche der Benutzer gewählt hat. |
| `PUT`   | `/api/news/picks`       | Die Auswahl setzen, als vollständige Liste von Feed-Ids.                   |
| `GET`   | `/api/news/articles`    | Artikel der gewählten Quellen, nach Blatt-Kategorie gruppiert. Parameter `minRanking` (Vorgabe 0). |

Artikel ohne Auswertung kommen bei jedem `minRanking` mit. Ein leeres Ranking ist keine Null, es
ist eine offene Frage, und eine Schwelle von 7 würde sie sonst genau dann verschlucken, wenn die
KI gerade nicht durchgekommen ist.
| `POST`  | `/api/news/runs`        | Lauf starten. Antwortet mit der Id, auch wenn schon einer läuft.           |
| `GET`   | `/api/news/runs/{id}`   | Stand des Laufs.                                                           |

Die Rechteverteilung folgt der bestehenden `SecurityConfig`: `/api/categories/**` und
`/api/feeds/**` sind für `ADMIN` und `SUPERUSER`, `/api/news/**` für jeden angemeldeten Benutzer
mit offenem Mandanten.

## Ablauf eines Laufs

1. Der Benutzer drückt **Aktualisieren**. `POST /api/news/runs` legt den Lauf mit `RUNNING` an und
   antwortet sofort mit der Id. Läuft bereits einer für diesen Benutzer, kommt dessen Id zurück und
   es wird nichts Zweites gestartet. Ein Lauf, der länger als fünfzehn Minuten auf `RUNNING` steht,
   gilt als abgebrochen — etwa weil der Server neu gestartet wurde — und wird beim nächsten
   Knopfdruck auf `FAILED` gesetzt, damit ein Absturz den Knopf nicht dauerhaft blockiert.
2. Verarbeitet werden die Feeds, die **dieser Benutzer** gewählt hat, nicht alle des Mandanten.
3. Je Feed: abrufen, parsen, Einträge über die `guid` abgleichen. Neue Einträge werden angelegt,
   bekannte bleiben mitsamt ihrer KI-Auswertung stehen. Einträge, die nicht mehr im Feed stehen,
   werden gelöscht.
4. Alle Artikel ohne `processed_at` gehen in Bündeln zu zehn an die KI. Nach jedem Bündel steigt
   `processed_articles`.
5. Am Ende `DONE` und `finished_at`. Der Lauf endet auch dann als `DONE`, wenn einzelne Feeds oder
   Bündel gescheitert sind — `FAILED` bleibt dem Fall vorbehalten, dass gar nichts ging.
6. Das Frontend fragt währenddessen alle zwei Sekunden `GET /api/news/runs/{id}` und lädt die
   Artikelliste nach, solange sich der Zähler bewegt.

Der Lauf selbst läuft in einem eigenen Thread, damit die HTTP-Antwort sofort zurückgeht.

## Wie eine Webseite gelesen wird

Eine Übersichtsseite hat keine Struktur, an der man sich festhalten könnte: Zwischen Navigation,
Werbung und Fußbereich stehen irgendwo die Artikel. Die NFL-Nachrichtenseite bringt 437 Links in
1,5 MB HTML mit. Das einer KI vorzulegen, wäre in Zeit und Geld nicht zu rechtfertigen — es wären
rund 400.000 Token für einen einzigen Abruf.

Stattdessen in zwei Zügen, ohne KI und ohne seitenspezifische Regeln:

1. **Links einsammeln und filtern.** Alle `<a href>` der Seite, behalten wird, was auf dieselbe
   Domain zeigt, einen Pfad mit mindestens zwei Abschnitten hat, einen Ankertext von mehr als
   dreißig Zeichen trägt und noch nicht dabei war. Aus 437 Links werden so um die dreißig
   Kandidaten.
2. **Von jedem Kandidaten den Kopf lesen.** Ein Abruf je Artikel, ausgewertet werden nur
   `og:title`, `og:description` und `article:published_time`. Diese Angaben sind auf
   Nachrichtenseiten praktisch immer da, weil Facebook und WhatsApp sie brauchen.

Heraus kommen Titel, Teaser und Datum — dieselben Felder, die ein Feed liefert. Ab da ist eine
Seite von einem Feed nicht mehr zu unterscheiden, und Ranking und Kernaussage arbeiten auf
demselben Material.

Der Preis sind die zusätzlichen Abrufe: dreißig kleine Anfragen je Seite und Lauf statt einer.
Sie laufen nacheinander mit kurzer Pause, damit die Quelle das nicht als Angriff auffasst, und der
Lauf nimmt höchstens dreißig Kandidaten je Seite mit.

Was damit **nicht** geht: Seiten, die ihren Inhalt erst per JavaScript nachladen. Für den Server
sind sie leer. Das ließe sich nur mit einem kopflosen Browser lösen, und das ist eine ganz andere
Größenordnung. Trifft es eine Quelle, sagt sie das als Fehler — „keine Artikel gefunden" — statt
stumm nichts zu liefern.

## Der KI-Aufruf

Ein Aufruf verarbeitet zehn Artikel. Hineingegeben werden je Artikel eine laufende Nummer, der
Titel, der Teaser aus dem Feed und der Name der Quelle. Zurück kommt eine Liste mit derselben
Nummer, der positiv formulierten Kernaussage und dem Ranking.

Die Antwort wird als Struktur ausgelesen, nicht als Freitext — Spring AI bildet sie über
`.entity(...)` auf einen Record ab. Kommen weniger Einträge zurück als hineingingen, oder passt
eine Nummer nicht, bleiben die betroffenen Artikel unverarbeitet und gehen beim nächsten Lauf
erneut mit.

Für das Ranking bekommt das Modell einen Maßstab an die Hand, sonst vergibt es willkürliche Zahlen:
0 bis 3 für Randnotizen und Belangloses, 4 bis 6 für Alltägliches im jeweiligen Gebiet, 7 bis 8 für
Bemerkenswertes, 9 bis 10 für Einschneidendes. Gemessen wird innerhalb des Gebiets, nicht gegen das
Weltgeschehen: Ein wichtiges Angular-Release ist im Tab *Angular* eine 8, auch wenn es die Welt
nicht bewegt.

Der Auftrag für die Kernaussage lautet, den Sachverhalt zu benennen und dabei den konstruktiven
Teil nach vorn zu stellen, ohne etwas hinzuzuerfinden und ohne Schlechtes zu beschönigen. Eine
Meldung über einen Unfall wird nicht zur guten Nachricht; sie wird knapp und sachlich, mit dem
Blick auf das, was hilft.

## Die drei Seiten

### Kategorien (Admin)

Baum mit Ober- und Unterkategorien, anlegen und umbenennen im Dialog, Reihenfolge per Drag & Drop.
An jedem Blatt steht, wie viele Quellen daran hängen. Löschen fragt nach und verweigert sich bei
belegten Kategorien.

### Quellen (Admin)

Tabelle mit Name, Kategorie, URL, Typ und Stand des letzten Abrufs. Eine Quelle mit Fehler beim
letzten Abruf ist markiert, mit dem Fehlertext als Tooltip.

Beim Anlegen trägt man eine gewöhnliche Seiten-URL ein — `heise.de` genügt — und drückt **Suchen**.
Der Server antwortet mit den Feeds, die er dazu gefunden hat, je mit Titel und Anzahl der
Einträge; daraus wählt man einen oder mehrere aus, und der Titel wird als Name vorgeschlagen.
Findet sich keiner, bietet die Seite an, die Webseite direkt auszulesen.

### Meine Auswahl (Benutzer)

Derselbe Baum, diesmal mit Kästchen an den Quellen. Ein Kästchen an der Kategorie wählt alle ihre
Quellen. Gespeichert wird über einen Knopf, nicht bei jedem Klick: `PUT /api/news/picks` schickt
die vollständige Liste, und ein Klick mehr oder weniger soll keinen Schreibzugriff auslösen.
Verlässt jemand die Seite mit ungespeicherten Änderungen, fragt ein Dialog nach.

### Übersicht (Benutzer)

Ein Tab je Blatt-Kategorie, in der etwas gewählt ist. Über den Tabs der Knopf **Aktualisieren** und
die Ranking-Schwelle; beides gilt für alle Tabs. Während eines Laufs steht dort statt des Knopfes
der Fortschritt.

Je Artikel: die positive Kernaussage als Überschrift, darunter Quelle und Zeitpunkt, das Ranking als
Zahl, und der Originaltitel als Link auf den Artikel. Sortiert nach Ranking absteigend, bei
gleichem Ranking nach Datum. Artikel ohne Auswertung stehen am Ende, mit dem Hinweis *noch nicht
bewertet*.

Die gewählte Ranking-Schwelle merkt sich der Browser, wie es `ThemeService` und
`UserColumnsService` schon vormachen.

## Verhalten im Fehlerfall

**Ein Feed antwortet nicht, liefert kaputtes XML oder braucht zu lange.** Der Fehlertext landet in
`last_error`, der Lauf macht mit dem nächsten Feed weiter. Zeitlimit zehn Sekunden je Feed,
höchstens fünfzig Einträge je Abruf, damit ein einzelner Riesen-Feed den Lauf nicht auffrisst.

**Eine Seite liefert keine Artikel.** Entweder lädt sie ihren Inhalt per JavaScript nach, oder der
Filter hat alles verworfen. Die Quelle bekommt „keine Artikel gefunden" als `last_error`, damit
der Admin sie auf der Quellen-Seite sieht, statt sich zu wundern, warum ein Tab leer bleibt.

**Ein KI-Bündel scheitert.** Die Artikel bleiben unverarbeitet, erscheinen als *noch nicht bewertet*
und gehen beim nächsten Lauf erneut mit. Der Lauf endet als `DONE`.

**Es gibt gar keinen KI-Zugang.** Der Lauf endet als `FAILED` mit einem Text, der auf die Seite
KI-Zugang verweist. Die Artikel sind dann trotzdem geholt und stehen unbewertet da.

**Zweiter Knopfdruck während eines Laufs.** Gibt die Id des laufenden zurück, startet nichts.

**Fremder Mandant.** Jeder Zugriff auf Kategorie, Quelle oder Artikel eines anderen Mandanten
antwortet 404, als gäbe es das Objekt nicht — wie es die bestehenden Controller halten.

## Tests

**Backend**

- `FeedReader` gegen abgelegte XML-Dateien, je eine für RSS 2.0 und Atom, dazu eine kaputte. Kein Netz.
- `ArticleProcessor` gegen `StubChatClients` aus den bestehenden Testquellen: Bündelung, Auslesen
  des Rankings, Verhalten bei unvollständiger und bei scheiternder Antwort.
- `NewsRunner`: Zähler steigen, ein kaputter Feed stoppt den Lauf nicht, ein zweiter Start liefert
  denselben Lauf.
- Controller-Tests mit MockMvc und Testcontainers im Stil von `UserControllerTest`, je einer pro
  Endpunktgruppe, samt Test auf Mandantentrennung.

**Frontend**

- Je Seite ein Komponententest.
- `model/`: Gruppierung nach Kategorie und der Ranking-Filter, frameworkfrei und damit billig.
- e2e für die drei Seiten mit gemockter API, wie die bestehenden Specs. Für die Übersicht gehört
  ein Lauf dazu, der über zwei Abfragen von *läuft* auf *fertig* springt.

## Bewusst nicht enthalten

**Kein Scheduler.** Kommt, wenn der Knopf nicht mehr reicht. Der Lauf ist so gebaut, dass ein
Scheduler ihn später nur anstoßen muss.

**Keine Kostenseite.** Die gab es im Altprojekt und wurde beim Entkernen entfernt. Ein Lauf über
zehn Feeds mit je zwanzig Meldungen sind rund zwanzig Sonnet-Aufrufe, und im Moment sieht das
niemand. Wenn die Kosten interessant werden, ist das der nächste Kandidat.

**Kein Nachladen des Artikeltextes.** Die KI sieht bei einem Feed nur, was der Feed liefert, und
bei einer Seite nur, was deren Open-Graph-Angaben hergeben — also Titel und zwei, drei Sätze.
Manche Feeds geben nur den Titel; daraus wird die Kernaussage entsprechend dünn. Den ganzen
Artikeltext zu holen und auszuwerten wäre der nächste Schritt, wenn sich zeigt, dass der Teaser
nicht trägt.

**Keine Seiten, die ihren Inhalt per JavaScript nachladen.** Siehe
[Wie eine Webseite gelesen wird](#wie-eine-webseite-gelesen-wird).

**Kein Gelesen-Status, keine Merkliste, keine Suche.** Nichts davon war gefordert.
