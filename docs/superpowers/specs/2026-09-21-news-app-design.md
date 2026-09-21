# News-App — Entwurf

Stand: 2026-09-21. Status: abgestimmt. Schritt 1 (Katalog und Auswahl) und Schritt 2
(Quellensuche und der Typ `PAGE`) sind umgesetzt. Nach einer Rückfrage hängt die Kategorie
seither an der einzelnen Meldung statt an der Quelle; das ist unten eingearbeitet und steht als
Schritt 3 noch aus.

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
- [Die vier Seiten](#die-vier-seiten)
- [Verhalten im Fehlerfall](#verhalten-im-fehlerfall)
- [Tests](#tests)
- [Bewusst nicht enthalten](#bewusst-nicht-enthalten)

## Worum es geht

Ein Admin pflegt zweierlei: eine Liste von Nachrichtenkategorien und eine Liste von Quellen. Eine
Quelle ist im Regelfall ein RSS- oder Atom-Feed; wo eine Seite keinen anbietet, wird die Seite
selbst gelesen.

Die beiden Listen hängen nicht aneinander. Ein Feed von t-online.de bringt Politik, Soziales und
Sport in einem, und wohin eine Meldung gehört, entscheidet sich je Meldung. Eine Quelle bekommt
deshalb keine Kategorie. Jeder Benutzer kreuzt an, welche Kategorien ihn interessieren.

Auf Knopfdruck holt der Server alle Quellen des Mandanten, schickt jede Meldung durch die KI und
zeigt das Ergebnis auf einer Seite an, ein Tab je Kategorie.

Die KI macht drei Dinge pro Meldung: sie zieht die Kernaussage heraus und formuliert sie positiv,
sie ordnet die Meldung einer der Kategorien des Mandanten zu, und sie vergibt ein Ranking von 0
(unwichtig) bis 10 (sehr wichtig). Über das Ranking lässt sich die Liste eindampfen, etwa auf
alles ab 7.

## Getroffene Entscheidungen

| Frage                        | Entscheidung                             | Warum                                                                                                                                                                                         |
| ---------------------------- | ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Woher die Nachrichten kommen | RSS und Atom, ersatzweise die Webseite   | Ein Feed ist strukturiert, stabil und rechtlich unbedenklich und bleibt deshalb der Normalfall. Wo keiner existiert, wird die Seite selbst gelesen — siehe [Quellen finden](#quellen-finden). |
| Wem der Katalog gehört       | dem Mandanten, gepflegt vom Admin        | Der Benutzer legt weder Kategorien noch Quellen an.                                                                                                                                           |
| Woran die Kategorie hängt    | an der einzelnen Meldung                 | Eine Quelle liefert gemischte Ware. Hinge die Kategorie an ihr, stünde im Tab _Politik_ der Sport aus demselben Feed.                                                                         |
| Wer die Kategorie vergibt    | die KI, im selben Aufruf                 | Das Modell sieht Titel und Teaser ohnehin schon für Kernaussage und Ranking. Die Kategorie ist ein drittes Feld in derselben Antwort und kostet keinen weiteren Aufruf.                       |
| Woraus die KI wählt          | der flachen Liste des Mandanten          | Ein fester Wortschatz statt freier Erfindung, sonst wachsen die Tabs ins Unbegrenzte. Flach, weil ein Modell aus fünfzehn klaren Begriffen zuverlässiger wählt als aus einem Baum.            |
| Was der Benutzer wählt       | Kategorien, nicht Quellen                | Geholt wird ohnehin für den ganzen Mandanten, siehe die nächste Zeile. Die Auswahl entscheidet nur, welche Tabs erscheinen.                                                                   |
| Wem ein Lauf gehört          | dem Mandanten                            | Artikel und ihre Bewertung gehören allen Benutzern gemeinsam. Ein Lauf je Benutzer würde dieselben Quellen mehrfach holen.                                                                    |
| Wann geholt wird             | auf Knopfdruck                           | Kein Scheduler in der ersten Fassung.                                                                                                                                                         |
| Wie der Knopf sich verhält   | Lauf im Hintergrund, Frontend fragt nach | Ein Lauf dauert bis zu einer Minute. Das überlebt keine synchrone HTTP-Antwort, und man will Fortschritt sehen.                                                                               |
| Wie lange Artikel bleiben    | nur was aktuell im Feed steht            | Kleinste Datenmenge. Preis: Eine Meldung, die aus dem Feed fällt und später wiederkehrt, kostet erneut einen KI-Aufruf.                                                                       |

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

| Seite         | Ergebnis                                                                                                                                    |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `t-online.de` | Schritt 2 — der Feed steht ordentlich im Kopf.                                                                                              |
| `heise.de`    | Schritt 3 — die Startseite schweigt, aber das Seitengerüst jeder Fehlerseite nennt beide Feeds.                                             |
| `golem.de`    | Schritt 4 — die eigene RSS-Übersicht wird per JavaScript nachgeladen und ist für den Server leer; `rss.golem.de` liefert direkt einen Feed. |
| `nfl.com`     | nichts gefunden — diese Quelle wird eine vom Typ `PAGE`.                                                                                    |

## Bausteine

### Backend

Drei Pakete unter `de.prime_ux.goodnews`, neben den bestehenden:

| Paket           | Aufgabe                                                                       |
| --------------- | ----------------------------------------------------------------------------- |
| `catalog`       | `Category` und `Feed` — was der Admin pflegt, samt Verwaltungs-Endpunkten.    |
| `subscriptions` | Welche Kategorien ein Benutzer sehen will.                                    |
| `news`          | Feed abrufen, Artikel ablegen, KI aufrufen, Lauf verwalten, Liste ausliefern. |

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
feat-picks/    Benutzer: Kategorien ankreuzen
feat-board/    Die Übersicht mit den Tabs
ui/            Artikelkarte, Quellenliste
data/          REST-Clients und Stores
model/         Typen, Sortierung, Gruppierung, Ranking-Filter (frameworkfrei)
```

`domains/playground` mit der leeren Testseite entfällt, sobald die Übersicht steht.

### Navigation

Die Sidebar-Gruppe heißt künftig **Nachrichten** statt Vorgänge und enthält _Übersicht_ und
_Meine Auswahl_. Unter **Administration** kommen _Kategorien_ und _Quellen_ dazu.

## Datenmodell

Fünf Tabellen. Die vier des Katalogs und der Nachrichten hängen am Mandanten und mit
`ON DELETE CASCADE` daran, wie die bestehenden; `user_categories` hängt am Benutzer.

### `categories`

`id`, `tenant_id`, `name`, `sort_order`, `created_at`

Eine flache Liste ohne Ebenen: Politik, Soziales, Sport, Wirtschaft, Technik. `sort_order`
bestimmt, in welcher Reihenfolge die Tabs stehen. Der Name ist je Mandant eindeutig, ohne Rücksicht
auf Groß- und Kleinschreibung.

Die Liste ist zugleich der Wortschatz, aus dem die KI wählt, und sollte deshalb überschaubar
bleiben. Aus fünfzehn Begriffen trifft ein Modell zuverlässig, aus fünfzig nicht mehr.

Begriffe, die ineinanderliegen, sind dabei kein Problem: „Sport", „NFL" und „Miami Dolphins"
dürfen nebeneinanderstehen, weil die KI die engste passende nimmt. Schlecht sind Begriffe, die
sich kreuzen, ohne dass einer im anderen steckt — bei „Sport" und „USA" trifft eine
Dolphins-Meldung beides, und keiner ist der engere.

### `feeds`

`id`, `tenant_id`, `name`, `url`, `type`, `last_fetched_at`, `last_error`, `created_at`

Keine Kategorie. Eine Quelle ist Name, Adresse und Typ, und wohin ihre Meldungen gehören, steht
erst fest, wenn die KI sie gesehen hat. Die URL ist je Mandant eindeutig, damit dieselbe Quelle
nicht mehrfach geholt wird. `last_error` hält fest, woran der letzte Abruf gescheitert ist, und ist
leer, solange alles ging.

`type` ist `FEED` oder `PAGE` und entscheidet, welcher Leser die Quelle anfasst. Alles andere an
der Quelle ist für beide gleich, und was hinten herauskommt — ein Artikel mit Titel, Link, Datum
und Teaser — ebenfalls. Deshalb merkt der Rest der Anwendung von dem Unterschied nichts.

Tabelle und Entity heißen weiterhin `feeds` und `Feed`, obwohl die Oberfläche von **Quellen**
spricht und eine Quelle nun auch eine Webseite sein kann. Das ist bewusst so gelassen worden; wer
den Code liest, sollte es wissen.

### `user_categories`

`id`, `user_id`, `category_id` — eindeutig als Paar.

Welche Kategorien ein Benutzer sehen will. Wer nichts angekreuzt hat, sieht alles; sonst stünde
jeder neue Benutzer vor einer leeren Tafel und hielte sie für kaputt.

Die Auswahl ordnet nur die Anzeige, sie spart nichts. Welche Kategorie eine Meldung hat, steht erst
fest, nachdem die KI sie gesehen hat, und geholt und bewertet wird ohnehin für den ganzen
Mandanten.

### `articles`

`id`, `feed_id`, `category_id`, `guid`, `title`, `link`, `published_at`, `teaser`,
`positive_summary`, `ranking`, `processed_at`, `fetched_at`

`feed_id` und `guid` zusammen eindeutig. Die `guid` ist das, was der Feed als Kennung mitgibt,
ersatzweise der Link. `positive_summary`, `ranking` und `category_id` sind leer, bis die KI den
Artikel gesehen hat; `ranking` liegt zwischen 0 und 10.

`category_id` bleibt auch danach leer, wenn die KI die Meldung keiner Kategorie zuordnen konnte.
Beide Fälle — noch nicht gesehen und nicht einzuordnen — werden auf der Übersicht zum Tab
_Sonstiges_. Der Fremdschlüssel hat `ON DELETE SET NULL`: Eine gelöschte Kategorie nimmt ihre
Artikel nicht mit, sie schiebt sie nach Sonstiges.

Artikel hängen am Feed, nicht am Benutzer. Alle Benutzer eines Mandanten teilen sich die Artikel
und damit die KI-Kosten.

### `news_runs`

`id`, `tenant_id`, `status`, `total_articles`, `processed_articles`, `started_at`,
`finished_at`, `error`

`status` ist `RUNNING`, `DONE` oder `FAILED`. Kein `user_id`: Ein Lauf gehört dem Mandanten, weil
er alle seine Quellen abarbeitet und das Ergebnis allen gehört.

## Endpunkte

### Katalog, nur für Admins

| Methode  | Pfad                   | Zweck                                                                      |
| -------- | ---------------------- | -------------------------------------------------------------------------- |
| `GET`    | `/api/categories`      | Die Liste, in ihrer Reihenfolge.                                           |
| `POST`   | `/api/categories`      | Anlegen, am Ende der Liste.                                                |
| `PUT`    | `/api/categories/{id}` | Umbenennen und umsortieren.                                                |
| `DELETE` | `/api/categories/{id}` | Löschen. Die Artikel daran rutschen nach Sonstiges.                        |
| `GET`    | `/api/feeds`           | Alle Quellen des Mandanten, mit letztem Fehler.                            |
| `POST`   | `/api/feeds`           | Anlegen.                                                                   |
| `PUT`    | `/api/feeds/{id}`      | Ändern.                                                                    |
| `DELETE` | `/api/feeds/{id}`      | Löschen, Artikel gehen mit.                                                |
| `POST`   | `/api/feeds/probe`     | Feeds zu einer Seiten-URL suchen, siehe [Quellen finden](#quellen-finden). |

Das Löschen einer Kategorie antwortet nie mit 409. Früher hingen Quellen daran; jetzt nur noch
Artikel, und die verlieren über `ON DELETE SET NULL` ihre Zuordnung, statt einen Löschvorgang zu
blockieren.

`probe` nimmt eine beliebige Seiten-URL entgegen und antwortet mit allen Feeds, die sich dazu
finden und parsen ließen — je Feed die Adresse, der Titel und die Zahl der Einträge. Findet sich
keiner, sagt die Antwort das, und die Seite bietet an, die Quelle als `PAGE` anzulegen.

### Auswahl und Übersicht, für jeden angemeldeten Benutzer

| Methode | Pfad                   | Zweck                                                                                |
| ------- | ---------------------- | ------------------------------------------------------------------------------------ |
| `GET`   | `/api/news/categories` | Die Kategorien des Mandanten, je mit der Angabe, ob der Benutzer sie angekreuzt hat. |
| `PUT`   | `/api/news/picks`      | Die Auswahl setzen, als vollständige Liste von Kategorie-Ids.                        |
| `GET`   | `/api/news/articles`   | Artikel des Mandanten, nach Kategorie gruppiert. Parameter `minRanking` (Vorgabe 0). |
| `POST`  | `/api/news/runs`       | Lauf starten. Antwortet mit der Id, auch wenn schon einer läuft.                     |
| `GET`   | `/api/news/runs/{id}`  | Stand des Laufs.                                                                     |

`articles` liefert alle Artikel des Mandanten, nicht nur die der angekreuzten Kategorien. Welche
Tabs daraus werden, entscheidet die Seite; der Server soll nicht zweimal dieselbe Regel kennen.

Artikel ohne Auswertung kommen bei jedem `minRanking` mit. Ein leeres Ranking ist keine Null, es
ist eine offene Frage, und eine Schwelle von 7 würde sie sonst genau dann verschlucken, wenn die
KI gerade nicht durchgekommen ist.

Die Rechteverteilung folgt der bestehenden `SecurityConfig`: `/api/categories/**` und
`/api/feeds/**` sind für `ADMIN` und `SUPERUSER`, `/api/news/**` für jeden angemeldeten Benutzer
mit offenem Mandanten.

## Ablauf eines Laufs

1. Der Benutzer drückt **Aktualisieren**. `POST /api/news/runs` legt den Lauf mit `RUNNING` an und
   antwortet sofort mit der Id. Läuft bereits einer für diesen Mandanten, kommt dessen Id zurück und
   es wird nichts Zweites gestartet — auch dann, wenn jemand anderes ihn angestoßen hat. Ein Lauf,
   der länger als fünfzehn Minuten auf `RUNNING` steht, gilt als abgebrochen — etwa weil der Server
   neu gestartet wurde — und wird beim nächsten Knopfdruck auf `FAILED` gesetzt, damit ein Absturz
   den Knopf nicht dauerhaft blockiert.
2. Verarbeitet werden **alle Quellen des Mandanten**. Wer welche Kategorie sehen will, spielt hier
   keine Rolle: Die Kategorie einer Meldung steht erst nach der Auswertung fest.
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
Titel, der Teaser aus dem Feed und der Name der Quelle, dazu einmal die Kategorienamen des
Mandanten. Zurück kommt eine Liste mit derselben Nummer, der positiv formulierten Kernaussage, dem
Kategorienamen und dem Ranking.

Die Kategorie kommt als Name zurück, nicht als Id. Eine UUID kostet Token und lädt ein Modell zum
Erfinden ein; ein Name lässt sich ohne Rücksicht auf Groß- und Kleinschreibung gegen die Liste
abgleichen. Was nicht trifft, bleibt leer. Das Modell darf ausdrücklich nichts zuordnen, wenn
nichts passt — eine erzwungene Zuordnung wäre schlechter als ein sichtbares _Sonstiges_.

Gewählt wird die **engste** passende Kategorie. Stehen „Sport", „NFL" und „Miami Dolphins"
nebeneinander in der Liste, gehört eine Dolphins-Meldung unter Miami Dolphins, eine über die
Patriots unter NFL und ein Handballspiel unter Sport. Dass die Dolphins ein NFL-Team sind, weiß
das Modell von sich aus; es braucht nur die Ansage, nach unten aufzulösen statt irgendetwas
Zutreffendes zu nehmen. Eine Hierarchie im Datenmodell ist dafür nicht nötig.

Eine Meldung liegt damit in genau einem Tab, auch wenn eine Oberkategorie ebenfalls zuträfe. Wer
alle NFL-Nachrichten sehen will, kreuzt NFL und Miami Dolphins an. Das Hochrollen in die
Oberkategorie wäre ein eigener Zug — es bräuchte den Elternbezug, den Schritt 3 aus den
Kategorien entfernt hat — und ist bewusst nicht gemacht, solange sich nicht zeigt, dass es fehlt.

Die Antwort kommt in mehreren Teilen zurück, und der erste ist nicht zwingend der mit den Worten
darin: Modelle, die vor dem Antworten nachdenken, stellen ihre Überlegung voran, und dieser Teil
trägt keinen Text. Wer nur den ersten Teil liest, findet eine leere Antwort, während das Modell
sehr wohl etwas gesagt hat. Gelesen wird deshalb der Text aller Teile.

Die Antwort wird als Struktur ausgelesen, nicht als Freitext — Spring AI bildet sie über
`.entity(...)` auf einen Record ab. Kommen weniger Einträge zurück als hineingingen, oder passt
eine Nummer nicht, bleiben die betroffenen Artikel unverarbeitet und gehen beim nächsten Lauf
erneut mit.

Für das Ranking bekommt das Modell einen Maßstab an die Hand, sonst vergibt es willkürliche Zahlen:
0 bis 3 für Randnotizen und Belangloses, 4 bis 6 für Alltägliches im jeweiligen Gebiet, 7 bis 8 für
Bemerkenswertes, 9 bis 10 für Einschneidendes. Gemessen wird innerhalb des Gebiets, nicht gegen das
Weltgeschehen: Ein wichtiges Angular-Release ist im Tab _Angular_ eine 8, auch wenn es die Welt
nicht bewegt.

Das Gebiet ist die Kategorie, die derselbe Aufruf gerade vergeben hat, nicht mehr die der Quelle.
Der Maßstab wird dadurch schärfer: Ein Fußballergebnis aus einem gemischten Nachrichtenfeed wird
gegen Sport gemessen und nicht gegen die Politik, die im selben Feed steht.

Der Auftrag für die Kernaussage lautet, den Sachverhalt zu benennen und dabei den konstruktiven
Teil nach vorn zu stellen, ohne etwas hinzuzuerfinden und ohne Schlechtes zu beschönigen. Eine
Meldung über einen Unfall wird nicht zur guten Nachricht; sie wird knapp und sachlich, mit dem
Blick auf das, was hilft.

## Die vier Seiten

### Kategorien (Admin)

Eine Tabelle, keine Ebenen: Name und Reihenfolge, anlegen und umbenennen im Dialog, umsortieren
über zwei Knöpfe. Löschen fragt nach und verweigert sich nie — die Artikel der Kategorie rutschen
nach Sonstiges.

Unter der Tabelle ein Satz dazu, wofür die Liste da ist: Sie ist der Wortschatz, aus dem die KI
jede Meldung einsortiert. Wer das nicht weiß, legt vierzig sich überschneidende Kategorien an und
wundert sich über die Treffer.

### Quellen (Admin)

Tabelle mit Name, URL, Typ und Stand des letzten Abrufs. Eine Quelle mit Fehler beim
letzten Abruf ist markiert, mit dem Fehlertext als Tooltip.

Beim Anlegen trägt man eine gewöhnliche Seiten-URL ein — `heise.de` genügt — und drückt **Suchen**.
Der Server antwortet mit den Feeds, die er dazu gefunden hat, je mit Titel und Anzahl der
Einträge; daraus wählt man einen oder mehrere aus, und der Titel wird als Name vorgeschlagen.
Findet sich keiner, bietet die Seite an, die Webseite direkt auszulesen.

### Meine Auswahl (Benutzer)

Eine Liste der Kategorien mit einem Kästchen an jeder. Gespeichert wird über einen Knopf, nicht bei
jedem Klick: `PUT /api/news/picks` schickt die vollständige Liste, und ein Klick mehr oder weniger
soll keinen Schreibzugriff auslösen. Verlässt jemand die Seite mit ungespeicherten Änderungen,
fragt ein Dialog nach.

Die Seite sagt dazu, dass die Auswahl nur die Anzeige ordnet: Geholt und bewertet wird ohnehin
alles, was der Mandant an Quellen hat. Sonst hält man das Abwählen für eine Sparmaßnahme.

### Übersicht (Benutzer)

Ein Tab je angekreuzter Kategorie, in der etwas liegt. Wer nichts angekreuzt hat, bekommt alle.
Dahinter, sobald etwas darin liegt, der Tab **Sonstiges** mit den Meldungen ohne Kategorie — den
noch nicht bewerteten und denen, die die KI nicht einordnen konnte. Er hält sich nicht an die
Auswahl: Was unzugeordnet ist, ließe sich sonst nur ausblenden, und genau das will man sehen, wenn
die KI gerade nicht durchkommt.

Über den Tabs der Knopf **Aktualisieren** und die Ranking-Schwelle; beides gilt für alle Tabs.
Während eines Laufs steht dort statt des Knopfes der Fortschritt.

Je Artikel: die positive Kernaussage als Überschrift, darunter Quelle und Zeitpunkt, das Ranking als
Zahl, und der Originaltitel als Link auf den Artikel. Sortiert nach Ranking absteigend, bei
gleichem Ranking nach Datum. Artikel ohne Auswertung stehen am Ende, mit dem Hinweis _noch nicht
bewertet_.

Die gewählte Ranking-Schwelle merkt sich der Browser, wie es `ThemeService` und
`UserColumnsService` schon vormachen.

## Verhalten im Fehlerfall

**Ein Feed antwortet nicht, liefert kaputtes XML oder braucht zu lange.** Der Fehlertext landet in
`last_error`, der Lauf macht mit dem nächsten Feed weiter. Zeitlimit zehn Sekunden je Feed,
höchstens fünfzig Einträge je Abruf, damit ein einzelner Riesen-Feed den Lauf nicht auffrisst.

**Eine Seite liefert keine Artikel.** Entweder lädt sie ihren Inhalt per JavaScript nach, oder der
Filter hat alles verworfen. Die Quelle bekommt „keine Artikel gefunden" als `last_error`, damit
der Admin sie auf der Quellen-Seite sieht, statt sich zu wundern, warum ein Tab leer bleibt.

**Ein KI-Bündel scheitert.** Die Artikel bleiben unverarbeitet, erscheinen als _noch nicht bewertet_
und gehen beim nächsten Lauf erneut mit. Der Lauf endet als `DONE`.

**Die KI nennt eine Kategorie, die es nicht gibt.** `category_id` bleibt leer, der Artikel landet
in Sonstiges. Kein Fehler, kein Abbruch — beim nächsten Lauf geht er nicht erneut mit, denn
bewertet ist er.

**Es gibt gar keinen KI-Zugang.** Der Lauf endet als `FAILED` mit einem Text, der auf die Seite
KI-Zugang verweist. Die Artikel sind dann trotzdem geholt und stehen unbewertet da — sämtlich unter
Sonstiges, weil ohne Auswertung auch keine Kategorie vergeben wurde.

**Zweiter Knopfdruck während eines Laufs.** Gibt die Id des laufenden zurück, startet nichts, egal
von wem.

**Fremder Mandant.** Jeder Zugriff auf Kategorie, Quelle oder Artikel eines anderen Mandanten
antwortet 404, als gäbe es das Objekt nicht — wie es die bestehenden Controller halten.

## Tests

**Backend**

- `FeedReader` gegen abgelegte XML-Dateien, je eine für RSS 2.0 und Atom, dazu eine kaputte. Kein Netz.
- `ArticleProcessor` gegen `StubChatClients` aus den bestehenden Testquellen: Bündelung, Auslesen
  des Rankings, Zuordnung der Kategorie über den Namen samt Groß- und Kleinschreibung, ein
  unbekannter Kategoriename, Verhalten bei unvollständiger und bei scheiternder Antwort.
- `NewsRunner`: Zähler steigen, ein kaputter Feed stoppt den Lauf nicht, ein zweiter Start liefert
  denselben Lauf, auch wenn ein anderer Benutzer ihn auslöst.
- Controller-Tests mit MockMvc und Testcontainers im Stil von `UserControllerTest`, je einer pro
  Endpunktgruppe, samt Test auf Mandantentrennung.

**Frontend**

- Je Seite ein Komponententest.
- `model/`: Sortierung der Kategorien, Gruppierung der Artikel nach Kategorie samt dem Eimer
  _Sonstiges_, und der Ranking-Filter — frameworkfrei und damit billig.
- e2e für die drei Seiten mit gemockter API, wie die bestehenden Specs. Für die Übersicht gehört
  ein Lauf dazu, der über zwei Abfragen von _läuft_ auf _fertig_ springt.

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
