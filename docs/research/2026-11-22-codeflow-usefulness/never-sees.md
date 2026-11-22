# What codeflow never sees, with code (2026-11-22)

Back to the [main report](README.md#what-it-never-sees).

**Answer.** Each category below has one real fix from the four repos, fetched with `gh api …/pulls/<n>/files` or
from the pre-fix source. In every Java case, the graph before the fix is **faithful and looks correct**: values
flow where the code sends them. The bug lives in something a value edge cannot carry, such as a modifier, a
string, a library's rules, a protocol or a declaration. That is why these are out of scope and not gaps to fix:
a graph that tried to draw them would be guessing.

| # | Category | Example | Where the fact lives |
|---|---|---|---|
| 1 | Concurrency | Kafka #24276 | Java memory model (`volatile`) |
| 2 | SQL text | Fineract #6724 | SQL string + Liquibase schema |
| 3 | Config / security DSL | Fineract #6961 | Spring Security matcher rules |
| 4 | Error-code policy | Kafka #24188 | protocol contract with old clients |
| 5 | Iteration order | Kafka #24269 | hash-map ordering + `Stream.max` tie rule |
| 6 | String semantics | Keycloak #54837 | Unicode case folding |
| 7 | Non-Java code | Keycloak #55163, Odoo #296973, Kafka #24114 | TypeScript, QWeb, Scala |
| 8 | Odoo field metadata | Odoo #295931 | a keyword on a field declaration |

---

## 1. Concurrency, races, transactions

[apache/kafka#24276](https://github.com/apache/kafka/pull/24276). ACL changes made by the metadata-loader thread
were not safely visible to the request-handler threads. The read-write lock had been removed, and the field was
not `volatile`.
```diff
 // StandardAuthorizerData.java
     /** Contains all of the current ACLs */
-    private AclCache aclCache;
+    private volatile AclCache aclCache;
```
**Why the graph can't see it.** The flow is the same before and after: the loader writes `aclCache` and
`authorize` reads it. The fix is one modifier. The fact is happens-before between threads, which no value edge
expresses.

## 2. SQL text and DDL

[apache/fineract#6724](https://github.com/apache/fineract/pull/6724) (FINERACT-2921). Interest recalculation never
picked loans with floating-rate changes. The query filtered on `created_date`, but the populated column is
`created_on_utc`. It also bound a bare `LocalDate` against a UTC timestamp.
```diff
 // LoanReadPlatformServiceImpl.fetchLoansForInterestRecalculation, inside a """ text block
-  LEFT JOIN m_floating_rates_periods bfrp ON bfr.id = bfrp.floating_rates_id AND bfrp.created_date >= ?
+  LEFT JOIN m_floating_rates_periods bfrp ON bfr.id = bfrp.floating_rates_id AND bfrp.created_on_utc >= ?
 // …
-  LocalDate yesterday = DateUtils.getBusinessLocalDate().minusDays(1);
+  OffsetDateTime yesterday = DateUtils.getOffsetDateTimeOfTenantFromLocalDate(DateUtils.getBusinessLocalDate().minusDays(1));
   return this.jdbcTemplate.queryForList(sql, Long.class, yesterday, LoanStatus.ACTIVE.getValue(), …);
```
**Why the graph can't see it.** `yesterday` and the status flow into `queryForList` as arguments, which is correct.
`sql` is one opaque string constant. Which column each `?` is compared with, and that the column is stale, is in
the SQL text and the Liquibase schema.

## 3. Config and security DSLs

[apache/fineract#6961](https://github.com/apache/fineract/pull/6961) (FINERACT-3306). With 2FA on, the OAuth2
filter chain called `anyRequest()` twice, which Spring Security rejects at startup. The 2FA endpoints also
required `TWOFACTOR_AUTHENTICATED`, so nobody could get the token.
```diff
 // AuthorizationServerConfig.protectedEndpoints
  .csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(auth -> {
-     auth.anyRequest().authenticated();
      if (fineractProperties.getSecurity().getTwoFactor().isEnabled()) {
-         auth.anyRequest().hasAuthority("TWOFACTOR_AUTHENTICATED");
+         auth.requestMatchers(HttpMethod.POST, "/api/*/twofactor/validate").authenticated()
+             .requestMatchers("/api/*/twofactor").authenticated().anyRequest().hasAuthority("TWOFACTOR_AUTHENTICATED");
+     } else {
+         auth.anyRequest().authenticated();
      }
```
**Why the graph can't see it.** The graph shows a lambda handed to a library builder and a phi gated on
`isEnabled()`, both of which look plausible. The facts that matter are Spring Security's rules: matchers are
ordered, the first match wins, and only one `anyRequest()` is allowed. Also involved are URL pattern strings and
a properties flag.

## 4. Error-code policy

[apache/kafka#24188](https://github.com/apache/kafka/pull/24188) (KAFKA-21468). SyncGroup could return
`COORDINATOR_LOAD_IN_PROGRESS`, which classic clients do not handle in that response.
```diff
 // GroupCoordinatorService.syncGroup
-  (error, __) -> new SyncGroupResponseData().setErrorCode(error.code()),
+  (error, __) -> {
+      if (error == Errors.COORDINATOR_LOAD_IN_PROGRESS) {
+          // Classic clients do not expect COORDINATOR_LOAD_IN_PROGRESS in a SyncGroup response.
+          return new SyncGroupResponseData().setErrorCode(Errors.REBALANCE_IN_PROGRESS.code());
+      } else {
+          return new SyncGroupResponseData().setErrorCode(error.code());
+      }
+  },
```
**Why the graph can't see it.** Before the fix, the edge from `error.code()` to `setErrorCode` is faithful. Which
codes an RPC may legally return, and to which client versions, is protocol policy. It lives in the `Errors` docs
and in client handlers, often in other repos.

## 5. Iteration-order semantics

[apache/kafka#24269](https://github.com/apache/kafka/pull/24269) (KAFKA-21566). On a tied assignor vote, the winner
is whichever entry the map lists first. `hasPreferredServerAssignorChanged` computed it once from the
`TimelineHashMap` and once from a `HashMap` copy, which can order tied entries differently. So plain heartbeats
looked like an assignor change and kept bumping the group epoch.
```java
// ConsumerGroup, pre-fix
Map<String, Integer> counts = new HashMap<>(this.serverAssignors);   // copy of a TimelineHashMap
maybeUpdateServerAssignors(counts, oldMember, newMember);
return counts.entrySet().stream()
    .max(Map.Entry.comparingByValue())     // tie -> first in this map's iteration order
    .map(Map.Entry::getKey);
// … preferredServerAssignor(long committedOffset):
return serverAssignors.entrySet(committedOffset).stream()
    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
```
**Why the graph can't see it.** Both sides flow from the vote counts through `max` to the assignor name, which
is correct. The bug is that two hash tables may order equal keys differently, and `Stream.max` keeps the first
maximum it meets. That is how the collections behave, inside the JDK and the timeline library.

## 6. String semantics

[keycloak/keycloak#54837](https://github.com/keycloak/keycloak/pull/54837). A reviewer caught that
`equalsIgnoreCase` treats dotless `ı` and `i` as equal. So changing `probe@gıthub.test` to `probe@github.test`,
which is a different address, still counted as a "case-only change" and kept Trust Email. The fix lower-cases
the way the user profile does and then compares with `equals`.
```java
// IdpReviewProfileAuthenticator, after review; the first version used equalsIgnoreCase
private boolean isCaseOnlyChange(List<String> oldValue, String newValue) {
    // equalsIgnoreCase also treats some distinct letters as equal, such as
    // the dotless 'ı' and 'i', which the lower-casing does not
    return oldValue != null && oldValue.size() == 1 && newValue != null
            && KeycloakModelUtils.toLowerCaseSafe(oldValue.get(0)).equals(newValue);
}
```
**Why the graph can't see it.** Both versions show `oldValue` and `newValue` flowing into a comparison that gates
`setAuthNote`, which is right either way. The difference is what the operator means under Unicode case folding,
not where the values go.

## 7. Non-Java code: TypeScript, QWeb, Scala

**TypeScript.** [keycloak/keycloak#55163](https://github.com/keycloak/keycloak/pull/55163). The admin UI table
showed its "empty" state before the first load started.
```diff
 // js/libs/ui-shared/src/controls/table/KeycloakDataTable.tsx
   const [unPaginatedData, setUnPaginatedData] = useState<T[]>();
-  const [loading, setLoading] = useState(false);
+  const [loading, setLoading] = useState(true);
```

**QWeb.** [odoo/odoo#296973](https://github.com/odoo/odoo/pull/296973). The portal's "Full amount" button showed
the total instead of the remainder on partly paid quotations.
```diff
 <!-- addons/sale/views/sale_portal_templates.xml -->
       Full amount <br/>
       <span
-          t-out="sale_order.amount_total"
+          t-out="sale_order.amount_total - sale_order.amount_paid"
           t-options="{'widget': 'monetary', 'display_currency': sale_order.currency_id, …}"
```

**Scala.** [apache/kafka#24114](https://github.com/apache/kafka/pull/24114) (KAFKA-21422). One failing entry threw
out of the loop, so the remaining transaction markers in the same response were never handled.
```scala
// TransactionMarkerRequestCompletionHandler.scala, pre-fix
for (pendingCompleteTxnAndMarker <- pendingCompleteTxnAndMarkerEntries.asScala) {
  val txnMarker = pendingCompleteTxnAndMarker.txnMarkerEntry
  val errors = responseErrors.get(txnMarker.producerId)
  if (errors == null)
    throw new IllegalStateException(s"… expected error map for producer id ${txnMarker.producerId}")
  txnStateManager.getTransactionState(transactionalId) match { // …
```
**Why the graph can't see them.** codeflow parses Java with javac, so these files never enter the pipeline and no
node is drawn. A Java root that calls into Kafka's `core/` ends at an `EXTERNAL` box. The QWeb case is a
dataflow bug (a wrong formula), but it is written in a template expression.

## 8. Odoo field metadata

[odoo/odoo#295931](https://github.com/odoo/odoo/pull/295931) (fixes #295897). Duplicating a replacement tax also
copied its `original_tax_ids`, so `fiscal_position.map_tax(source_tax)` then matched two taxes.
```diff
 # addons/account/models/account_tax.py
     original_tax_ids = fields.Many2many(
         comodel_name='account.tax',
         relation='account_tax_alternatives',
         column1='dest_tax_id',  # This Replacement tax
         column2='src_tax_id',  # Domestic Tax to replace
         string="Replaces",
+        copy=False,
         domain="""[ ('type_tax_use', '=', type_tax_use), ('is_domestic', '=', True), ]""",
         ondelete='cascade',
```
**Why the graph can't see it.** No statement writes the copied value. The ORM's `copy()` reads `copy=` off the
declaration and duplicates the relation rows. The fact is a declarative attribute, as are `compute=`,
`store=`, `related=` and `@api.depends_context`, plus a domain held in a string. An Odoo-specific port could model
some of these as registry facts, which is part of the estimated 60–70% in the main report.

---

**Spare candidates** (from the study data, not fetched):
- Concurrency: keycloak#55030 (compare-and-swap plus a pessimistic lock), FINERACT-3196 (`REPEATABLE_READ` snapshot).
- SQL: fineract#6952 and #6977 (QueryDSL migration, where equivalence is judged on the rendered SQL).
- Security: fineract#6935 (permission strings and Liquibase permission rows).
