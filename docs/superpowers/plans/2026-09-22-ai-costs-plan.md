# KI-Kosten — Umsetzungsplan

Stand: 2026-09-22. Grundlage: [Entwurf](../specs/2026-09-22-ai-costs-design.md).

Zwei Schritte. Schritt 1 fängt an aufzuzeichnen und liefert die Zahlen über zwei Endpunkte aus;
er ist für sich lauffähig, testbar und mergefähig, auch wenn noch niemand die Zahlen sieht.
Schritt 2 baut die Seite darauf.

Die Reihenfolge ist zwingend, und sie hat einen praktischen Nebeneffekt: Wird Schritt 1 zuerst
gemergt, sammeln sich schon Daten, während Schritt 2 entsteht. Die Seite läuft dann beim ersten
Aufruf nicht gegen eine leere Tabelle.

Gearbeitet wird auf einem Feature-Branch je Schritt; committet und gepusht wird erst auf Ansage,
gemergt von Hand über GitHub. Vor jedem Commit läuft `node scripts/verify.mjs` durch.

## Inhalt

- [Was für beide Schritte gilt](#was-für-beide-schritte-gilt)
- [Schritt 1 — Erfassung und Endpunkte](#schritt-1--erfassung-und-endpunkte)
- [Schritt 2 — Die Kostenseite](#schritt-2--die-kostenseite)
- [Was danach offen bleibt](#was-danach-offen-bleibt)

## Was für beide Schritte gilt

**Migrationen.** `V5` ist der letzte Stand. Schritt 1 bringt `V6__create_ai_calls.sql`, Schritt 2
keine.

**Sheriff.** Die Seite liegt im bestehenden Domain `admin` unter `feat-costs`, `data` und `model`.
An `sheriff.config.ts` ist nichts zu ändern; muss doch etwas angefasst werden, ist das ein Hinweis
auf einen falschen Schnitt.

**Übersetzungen.** Die Schlüssel kommen in `public/i18n/de.json` und `en.json` gleichzeitig rein.
Deutsch ist die Vorgabesprache und das, wogegen die e2e-Tests prüfen.

**Testabdeckung.** Kein Schritt gilt als fertig ohne Komponententests, Controller-Tests, einen
Test auf Mandantentrennung und — für die neue Seite — einen e2e-Test.

## Schritt 1 — Erfassung und Endpunkte

Ziel: Jeder KI-Aufruf hinterlässt eine Zeile mit Tokens und Betrag, und zwei Endpunkte geben die
vier Summen und die geblätterte Liste heraus. Kein Frontend.

### Backend

1. **Migration `V6__create_ai_calls.sql`** — Tabelle `ai_calls` mit den Spalten aus dem Entwurf,
   Fremdschlüssel auf `tenants` mit `ON DELETE CASCADE`, CHECK auf `purpose IN ('RATING',
   'KEY_TEST')` und Index `ai_calls_tenant_called_idx` auf `(tenant_id, called_at DESC)`. Ein
   Kommentarkopf hält fest, warum die Kosten in der Zeile stehen und nicht beim Lesen gerechnet
   werden — das ist die Entscheidung, die man dem Schema sonst nicht ansieht.

2. **Paket `de.prime_ux.goodnews.costs`** — `AiCall` als Entity, `Purpose` als Enum,
   `AiCallRepository`. Die Entity bekommt einen Konstruktor, der alle Felder verlangt, und keine
   Setter: eine Buchung wird geschrieben und danach nicht mehr angefasst.

3. **`AiPriceProperties`** — `@ConfigurationProperties("goodnews.ai.prices")`, eine
   `Map<String, Rate>` mit `input` und `output` als `BigDecimal`. Die Einträge für
   `claude-sonnet-5` (2.00 / 10.00) kommen in `application.properties`.

4. **`ModelPrices`** — rechnet aus Modellname und Token den Betrag, gerundet auf sechs Stellen
   (`HALF_UP`), und liefert für ein unbekanntes Modell `Optional.empty()`. Einmal pro unbekanntem
   Modell eine Warnung ins Log, nicht einmal pro Aufruf.

5. **`AiCalls`** — die Komponente mit `record(Tenant, Purpose, ChatResponse)`. Sie zieht Modell
   und Tokens aus `response.getMetadata()`, holt den Betrag von `ModelPrices` und speichert. Alles
   in einem `try`/`catch` um den ganzen Rumpf: eine misslungene Buchung wird protokolliert und
   verschluckt, damit sie keinen Lauf kippt.

6. **Aufrufer anschließen** — `ArticleProcessor` ruft `record(...)` direkt nach `call()` mit
   `Purpose.RATING`, vor dem Auslesen des Texts, damit auch eine leere oder unlesbare Antwort
   gebucht wird. `ApiKeyTester` ruft nach seinem Ping mit `Purpose.KEY_TEST`. `accountOf` bleibt
   unverändert.

7. **`AiCallSummary` und die Abfrage** — ein `@Query` auf dem Repository mit vier Paaren aus
   `SUM(CASE WHEN called_at >= :von THEN cost_usd ELSE 0 END)` und `COUNT(CASE WHEN … THEN 1 END)`,
   projiziert auf ein Record. Die vier Grenzen rechnet eine kleine Klasse `Periods` aus einer
   `Clock` und `ZoneId.systemDefault()`: Tagesbeginn, Montag, Monatserster, Jahresbeginn. Die
   `Clock` kommt per Injektion, sonst ist der Test auf die Zeitgrenzen nicht schreibbar.

8. **`CostsController`** — `GET /api/costs/summary` und `GET /api/costs/calls`, beide über
   `CurrentSession.tenant()`. Die Liste als `Page<AiCallResponse>` mit
   `PageRequest.of(page, size, Sort.by(DESC, "calledAt"))`, `size` auf höchstens 100 begrenzt,
   damit niemand über die Adresszeile die ganze Tabelle zieht.

9. **`SecurityConfig`** — `.requestMatchers("/api/costs/**").hasAnyRole("ADMIN", "SUPERUSER")`,
   eingeordnet bei den anderen Admin-Regeln.

### Tests

- `ModelPricesTest` — ein gerechnetes Beispiel, die Rundung auf sechs Stellen, und ein unbekanntes
  Modell ohne Betrag.
- `AiCallsTest` — schreibt eine Zeile aus einer Antwort mit Usage; schreibt eine Zeile ohne Betrag,
  wenn das Modell keine Rate hat; wirft nicht, wenn das Repository wirft.
- `AiCallRepositoryTest` — Zeilen eine Sekunde vor und nach jeder der vier Grenzen, gegen eine
  feste `Clock`. Der Test, der den Plan trägt: hier fällt auf, wenn der Wochenbeginn auf Sonntag
  rutscht oder die Zeitzone nicht stimmt.
- `ArticleProcessorTest` — bestehende Tests um die Erwartung ergänzen, dass eine Buchung entsteht;
  ein neuer Fall, dass bei einer geworfenen Exception keine entsteht. `StubChatClients` liefert
  dafür eine Antwort mit Usage.
- `CostsControllerTest` — beide Endpunkte für einen Admin; 403 für einen Benutzer ohne Rolle;
  ein zweiter Mandant mit eigenen Zeilen, die in der Antwort nicht auftauchen; `size=1000` wird
  auf 100 gekappt.

### Fertig, wenn

`node scripts/verify.mjs` grün ist und ein Lauf über die Oberfläche in `ai_calls` je Bündel eine
Zeile mit plausiblem Betrag hinterlässt.

## Schritt 2 — Die Kostenseite

Ziel: Die Seite unter `/costs` zeigt die vier Kacheln und die geblätterte Liste.

### Frontend

1. **Modelle** — `domains/admin/model/cost-summary.ts` mit `CostSummary` und `PeriodCost`,
   `domains/admin/model/ai-call.ts` mit `AiCall` und `AiCallPurpose`. `calledAt` ist im Modell ein
   `Date` und auf der Leitung ein ISO-String, wie bei `User.createdAt`; die Umwandlung macht die
   `parse`-Funktion der Ressource.

2. **`costs-service.ts`** — zwei `httpResource`. Die Zusammenfassung hängt an keiner Eingabe, die
   Liste an einem `page`- und einem `size`-Signal, die die Tabelle setzt. Beide mit `defaultValue`,
   damit die Vorlage nicht auf `undefined` trifft.

3. **`costs-page.ts` / `.html`** — Überschrift, vier Kacheln im Grid (`grid-cols-1 sm:grid-cols-2
   lg:grid-cols-4`), darunter die `p-table` mit `[lazy]`, `[paginator]`, `[rows]`,
   `[totalRecords]` und `(onLazyLoad)`. Lade- und Fehlerzustand getrennt für Kacheln und Tabelle,
   nach dem Muster der `ai-settings-page`. Die leere Liste bekommt einen eigenen Satz statt einer
   leeren Tabelle.

4. **Route und Navigation** — `domains/admin/api/costs-routes.ts`, in `app.routes.ts` unter
   `costs` mit `adminGuard`, und der Eintrag in der Gruppe Administration der Sidebar hinter den
   KI-Einstellungen.

5. **Übersetzungen** — `shell.costs` und der Block `costs.*` in beiden Sprachdateien: Titel,
   die vier Zeitraumnamen, die Spaltenüberschriften, die beiden Zwecke, Lade- und Fehlertexte, der
   Satz für die leere Liste.

### Tests

- `costs-page.spec.ts` — mit gefälschtem Service: vier Kacheln mit den erwarteten Beträgen und
  Anzahlen, die Zeilen der ersten Seite, der Gedankenstrich bei einer Zeile ohne Betrag, und der
  Satz statt der Tabelle bei leerer Liste.
- `e2e/costs.spec.ts` — Admin meldet sich an, ruft die Seite über die Sidebar auf, sieht die vier
  Kacheln und mindestens eine Zeile; ein Benutzer ohne Admin-Rolle sieht den Menüpunkt nicht.

### Fertig, wenn

`node scripts/verify.mjs` grün ist, die Seite im laufenden Frontend die Zahlen aus Schritt 1 zeigt
und das Blättern eine zweite Seite nachlädt.

## Was danach offen bleibt

Aus dem Abschnitt „Bewusst nicht enthalten" des Entwurfs, hier noch einmal als das, was ein
späterer Schritt aufgreifen könnte: ein Verlauf über die Zeit, die Gruppierung nach Lauf, eine
plattformweite Sicht für Super-User, eine Aufbewahrungsgrenze für alte Zeilen und eine Warnung bei
Überschreiten eines Budgets.
