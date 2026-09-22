# KI-Kosten — Entwurf

Stand: 2026-09-22. Status: abgestimmt, noch nicht umgesetzt.

<!-- Das Dokument ist auf Deutsch, wie der Entwurf zur News-App: es ist die Vorlage für eine
     Entscheidung und das Gespräch dazu lief auf Deutsch. Code, Kommentare und Commits im Repo
     bleiben Englisch. -->

## Inhalt

- [Worum es geht](#worum-es-geht)
- [Getroffene Entscheidungen](#getroffene-entscheidungen)
- [Datenmodell](#datenmodell)
- [Preise](#preise)
- [Erfassung](#erfassung)
- [Endpunkte](#endpunkte)
- [Die Seite](#die-seite)
- [Verhalten im Fehlerfall](#verhalten-im-fehlerfall)
- [Tests](#tests)
- [Bewusst nicht enthalten](#bewusst-nicht-enthalten)

## Worum es geht

Ein Admin soll sehen, was die KI seinen Mandanten kostet: vier Zahlen für heute, diese Woche,
diesen Monat und dieses Jahr, darunter eine Liste der einzelnen Aufrufe mit den Kosten je Aufruf.

Heute wird davon nichts festgehalten. `ArticleProcessor` bekommt die Token-Zahlen über
`response.getMetadata().getUsage()` zurück, liest sie aber nur für eine Fehlermeldung aus und
verwirft sie danach. Die Seite braucht deshalb zuerst eine Aufzeichnung im Backend; rückwirkend
gibt es keine Zahlen, sie beginnen mit dem Einbau.

Gerufen wird die KI an zwei Stellen: beim Bewerten eines Bündels von bis zu zehn Meldungen
(`ArticleProcessor`) und beim Ausprobieren eines Schlüssels (`ApiKeyTester`). Eine Zeile in der
Liste ist ein Aufruf, nicht eine Meldung — so wird auch abgerechnet.

## Getroffene Entscheidungen

| Frage                         | Entscheidung                                | Warum                                                                                                                                                      |
| ----------------------------- | ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Wessen Kosten die Seite zeigt | nur die des eigenen Mandanten               | Ein Mandant mit eigenem Schlüssel hat seinen eigenen Vertrag und seine eigene Rechnung. Eine plattformweite Sicht wäre eine andere Seite mit anderem Zweck. |
| Wer sie sehen darf            | Admin und Super-User                        | Dieselbe Schranke wie bei Benutzern, Quellen und KI-Einstellungen.                                                                                         |
| Wie die Zeiträume aussehen    | vier Kacheln, laufende Zeiträume            | Beantwortet „was kostet mich das gerade" in einer Abfrage und auf einen Blick. Ein Verlauf ist eine andere Frage und kann später dazukommen.                |
| Woher der Preis kommt         | Raten je Modell aus den Properties          | Sie ändern sich selten. Eine Pflegemaske wäre eine Seite und eine Migration für etwas, das ein Deployment mitnimmt.                                        |
| Wann gerechnet wird           | beim Schreiben, Betrag wird festgeschrieben | Eine spätere Preisänderung darf die Historie nicht umschreiben. Die Seite zeigt, was es gekostet hat, nicht was es heute kosten würde.                     |
| Was ohne Rate passiert        | Betrag bleibt leer                          | Ein neues Modell ohne Eintrag soll auffallen. Eine stille 0 sähe aus wie kostenlose Arbeit.                                                                |
| Währung                       | USD                                         | Anthropic rechnet so ab. Eine Umrechnung bräuchte einen Kurs von irgendwoher und macht die Zahl nur ungenauer.                                             |
| Wie weit die Liste reicht     | alle Aufrufe, seitenweise nachgeladen       | Die Tabelle wächst mit jedem Lauf. Alles auf einmal zu laden, wie es Benutzer und Quellen tun, hält hier nicht lange.                                      |
| Was eine Zeile ist            | ein Aufruf                                  | Die Einheit der Abrechnung. Kosten je Meldung wären eine Division durch die Bündelgröße und würden Genauigkeit vortäuschen.                                |

## Datenmodell

Migration `V6__create_ai_calls.sql`, eine Zeile je Aufruf:

| Spalte                          | Typ                  | Bedeutung                                                            |
| ------------------------------- | -------------------- | -------------------------------------------------------------------- |
| `id`                            | UUID PRIMARY KEY     |                                                                      |
| `tenant_id`                     | UUID NOT NULL        | Mandant, `REFERENCES tenants (id) ON DELETE CASCADE`                 |
| `purpose`                       | TEXT NOT NULL        | `RATING` oder `KEY_TEST`, per CHECK eingegrenzt                      |
| `model`                         | TEXT NOT NULL        | wie vom Provider zurückgemeldet                                      |
| `input_tokens`, `output_tokens` | INTEGER NOT NULL     |                                                                      |
| `cost_usd`                      | NUMERIC(12,6)        | beim Schreiben berechnet; leer, wenn für das Modell keine Rate steht |
| `called_at`                     | TIMESTAMPTZ NOT NULL |                                                                      |

Index auf `(tenant_id, called_at DESC)`. Das ist sowohl die Liste als auch jede Summe; eine andere
Abfrage gibt es auf dieser Tabelle nicht.

`NUMERIC(12,6)` statt `double precision`: Geldbeträge werden addiert und angezeigt, nicht
gerechnet, und sechs Nachkommastellen fassen auch ein Bündel für einen Bruchteil eines Cents.

Die Entität `AiCall` liegt im neuen Paket `de.prime_ux.goodnews.costs`. Weder `news` noch
`aisettings` ist der richtige Ort: beide rufen die KI, aber keines von beiden ist fürs Abrechnen
zuständig.

## Preise

Als `@ConfigurationProperties` unter `goodnews.ai.prices`, je Modell zwei Raten in USD pro
Million Token:

```properties
goodnews.ai.prices.claude-sonnet-5.input=2.00
goodnews.ai.prices.claude-sonnet-5.output=10.00
```

Die Werte sind die Anthropic-Listenpreise für `claude-sonnet-5`, das Modell aus
`spring.ai.anthropic.chat.options.model`. Wechselt das Modell, kommt ein Eintrag dazu; die alten
Zeilen behalten ihren festgeschriebenen Betrag.

Die Rechnung macht eine Komponente `ModelPrices`:

```
cost = input_tokens / 1_000_000 * input_rate + output_tokens / 1_000_000 * output_rate
```

in `BigDecimal`, gerundet auf sechs Stellen (`HALF_UP`). Für ein Modell ohne Eintrag gibt sie
nichts zurück, und die Zeile wird ohne Betrag geschrieben.

Prompt-Caching ist nicht eingeschaltet, deshalb gibt es keine Raten für Cache-Token. Käme es
dazu, wären das zwei weitere Raten und zwei weitere Spalten.

## Erfassung

Eine Komponente `AiCalls` mit einer Methode:

```java
void record(Tenant tenant, Purpose purpose, ChatResponse response);
```

Sie liest Modell und Token aus den Metadaten, holt sich den Betrag von `ModelPrices` und
speichert die Zeile. `ArticleProcessor` ruft sie direkt nach `call()` mit `Purpose.RATING`,
`ApiKeyTester` nach seinem Ping mit `Purpose.KEY_TEST`.

Bricht der Aufruf mit einer Exception ab, gibt es keine Metadaten und damit nichts zu buchen —
dann wird nichts geschrieben. Ein Aufruf, der eine leere oder unlesbare Antwort liefert, wird
dagegen erfasst: er hat Geld gekostet.

`accountOf` in `ArticleProcessor` bleibt wie es ist. Es liest dieselben Metadaten, aber für einen
anderen Zweck — eine Fehlermeldung für Menschen, keine Buchung.

## Endpunkte

Ein `CostsController` unter `/api/costs`, in `SecurityConfig` freigeschaltet mit

```java
.requestMatchers("/api/costs/**").hasAnyRole("ADMIN", "SUPERUSER")
```

Beide Endpunkte lesen den Mandanten aus `CurrentSession`; ein fremder Mandant ist nicht
adressierbar.

**`GET /api/costs/summary`** — die vier laufenden Zeiträume, je Zeitraum Betrag und Anzahl:

```json
{
  "day": { "cost": 0.482, "calls": 6 },
  "week": { "cost": 3.117, "calls": 41 },
  "month": { "cost": 12.64, "calls": 173 },
  "year": { "cost": 12.64, "calls": 173 }
}
```

Eine einzige Abfrage mit vier Paaren aus `SUM(CASE WHEN called_at >= :von THEN cost_usd END)` und
`COUNT(CASE WHEN …)`. Die Grenzen rechnet das Backend, nicht die Datenbank: Tagesbeginn,
Wochenbeginn am Montag, Monats- und Jahresbeginn in `ZoneId.systemDefault()`. Läuft der Server in
UTC, während der Benutzer in Berlin sitzt, verschiebt sich „heute" um zwei Stunden. Eine eigene
Property dafür wäre vorgezogene Arbeit, solange beides auf derselben Maschine läuft.

Ein Zeitraum ohne Aufrufe liefert `0` und nicht `null` — die Kachel zeigt dann 0,00 $, was
stimmt.

**`GET /api/costs/calls?page=0&size=25`** — eine Spring-Data-`Page`, neueste zuerst. Je Zeile:
`id`, `calledAt`, `purpose`, `model`, `inputTokens`, `outputTokens`, `costUsd`. Das ist die erste
serverseitig geblätterte Liste im Projekt; Benutzer und Quellen laden bisher alles auf einmal,
was bei einer mit jedem Lauf wachsenden Tabelle nicht trägt.

## Die Seite

Im Domain `admin`, parallel zu `feat-ai-settings`:

- `domains/admin/api/costs-routes.ts`, in `app.routes.ts` unter `/costs` mit `adminGuard`
- `domains/admin/feat-costs/costs-page.{ts,html,spec.ts}`
- `domains/admin/data/costs-service.ts` mit zwei `httpResource`; die Liste hängt an einem
  `page`-Signal, das die Tabelle setzt
- `domains/admin/model/ai-call.ts` und `cost-summary.ts`
- Eintrag in der Gruppe Administration der Sidebar, hinter den KI-Einstellungen
- Texte unter `costs.*` und `shell.costs` in `public/i18n/de.json` und `en.json`

Aufbau: Überschrift, darunter vier Kacheln im Grid — auf dem Handy untereinander, ab `sm`
nebeneinander. Jede Kachel zeigt den Zeitraum, den Betrag groß und die Anzahl der Aufrufe klein
darunter.

Darunter die `p-table` mit `[lazy]`, `[paginator]`, `[rows]`, `[totalRecords]` und
`(onLazyLoad)`. Spalten: Zeitpunkt, Zweck als `p-tag`, Modell, Token ein, Token aus, Kosten.

Beträge über die `currency`-Pipe mit `USD`: in den Kacheln zwei Nachkommastellen, in der Tabelle
vier. Ein einzelnes Bündel kostet Bruchteile eines Cents, auf zwei Stellen gerundet stünde in der
Tabelle überall 0,00 $. Eine Zeile ohne Betrag zeigt einen Gedankenstrich.

## Verhalten im Fehlerfall

Eine Kostenzeile, die sich nicht schreiben lässt, darf keinen Lauf kippen. `AiCalls.record` fängt
deshalb alles, protokolliert es und kehrt zurück. Eine fehlende Buchung ist ärgerlich, ein
abgebrochener Lauf ist schlimmer.

Auf der Seite: Ladefehler der Zusammenfassung und der Liste werden getrennt behandelt, jeder mit
einer `p-message` an seiner Stelle — wie auf den anderen Seiten. Eine leere Liste ist kein
Fehler, sondern der Zustand vor dem ersten Lauf, und bekommt einen eigenen Satz.

## Tests

Backend:

- `ModelPrices` rechnet richtig, und ein Modell ohne Rate liefert keinen Betrag
- Die Summen-Abfrage mit Zeilen knapp vor und nach jeder Zeitgrenze, damit Tages- und
  Wochenbeginn nicht um einen Tag danebenliegen
- `ArticleProcessor` schreibt nach einem Aufruf eine Zeile, und keine, wenn der Aufruf wirft
- Der Controller antwortet einem Benutzer ohne Admin-Rolle mit 403 und liefert keine Zeilen
  eines fremden Mandanten

Frontend:

- Spec für `costs-page` mit gefälschtem Service: vier Kacheln mit den erwarteten Beträgen,
  Tabellenzeilen aus der ersten Seite, und der Gedankenstrich bei fehlendem Betrag

## Bewusst nicht enthalten

- **Euro.** Keine Umrechnung, kein Kurs, keine zweite Währung in der Anzeige.
- **Verlauf.** Kein Diagramm, keine Tabelle je Tag oder Monat. Die vier Kacheln sind laufende
  Zeiträume ohne Historie.
- **Gruppierung nach Lauf.** Die Liste ist ein flacher Strom. Was ein einzelner Lauf gekostet
  hat, beantwortet sie nicht.
- **Plattformweite Sicht.** Kein Super-User-Blick über alle Mandanten hinweg.
- **Pflegemaske für Preise.** Die Raten stehen in den Properties.
- **Aufbewahrungsgrenze.** Die Tabelle wächst unbegrenzt; es gibt keinen Job, der alte Zeilen
  wegräumt.
- **Budget oder Warnung.** Keine Obergrenze, kein Alarm, wenn es teuer wird.
