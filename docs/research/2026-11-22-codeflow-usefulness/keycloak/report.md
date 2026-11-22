# codeflow on keycloak/keycloak

## Sample
- Census: last 100 merged PRs (merged 2026-11-11..20, one week). 24+ are release-branch backports, so deep dives use the original PR.
- Deep dives: 36 Java production PRs (25 bugfix, 9 security, 1 perf, 1 feature) and 32 closed `kind/bug` issues, each paired with its fix PR. Scoring: D1/D2a/D2b on PR rows, D3/D4 on issue rows.
- 44 human review comments from 20 PRs (bots, LGTM and rebase comments skipped).
- Trial runs: codeflow (JDK 25, installed CLI) on the pre-fix sources of 4 fixes: Argon2PasswordHashProvider, SAML DeploymentBuilder/SP, AbstractScimResourceTypeProvider, OIDCIdentityProvider. No classpath. Outputs are in `trial/`.

## Census (100)
bugfix 33, security 17, build/CI/deps 17, test-only 10, docs 9, feature 6, refactor 4, perf 2, config 1, other 1.
By language: Java production 63, Java tests only 8, TypeScript 5 (scored 0), no code 24.

## Scores
| Dim | 2 | 1 | 0 | n |
|---|---|---|---|---|
| D1 Understand | 0 | 22 | 14 | 36 |
| D2a Own PR | 0 | 0 | 36 | 36 |
| D2b Review | 0 | 6 | 30 | 36 |
| D3 Solve bug | 1 | 19 | 12 | 32 |
| D4 Prevent | 0 | 12 | 20 | 32 |

Capability on scored rows: 10 EXISTING, 28 PLANNED, 30 none. Comments: 33 score 0, 11 score 1, none score 2. The 1s are correctness/data-propagation comments that a diverges or slice query would back up.

Issue root causes: missing-propagation 7, condition/logic 5, state/persistence 5, null/missing-guard 4, wrong-value 3, concurrency 2, other 5.

## Best cases
1. **#53471 → #54268, email_verified from userinfo** (D3 2, EXISTING, verified). Rooted at `extractIdentity`, the `--json` graph shows the gated phi chain (ifs at lines 461/463/465) into `identity.getContextData().put(EMAIL_VERIFIED, …)`. The `userInfoEmailVerified` branch overwrites the ID-token value whatever the email. A reviewer's null case (`isSameEmail` true on a null email) sits on the same gate. This is the only decisive case.
2. **#53633 → #55027, Argon2 policyCheck** (D3 1, EXISTING, verified). In the `--summaries` output for `policyCheck`, parameter P0 (`policy`) reaches nothing. Only `data.getHashIterations()` flows to RET. The fix was `expectedIterations(policy.getHashIterations())`. "A parameter is dead" is a fact the summary states outright.
3. **#54733 → #54761, GroupMembershipMapper mutates cached config** (D3 1, EXISTING). The summary shows a write into the parameter's `config` map (`put(MULTIVALUED,"true")`), so the mapper modifies its input. The fix copies the model first.
4. **#49240 → #54006, client private key exposed by REST** (D4 1, PLANNED). The fact is that `privatePem` reaches the `getKeystore` response. This needs `slice_fwd` from the key attribute to a typed `net:out` boundary. Today it ends in JAX-RS returns that are not sinks. A reviewer (mposolda) asked for exactly this: "every other place exposing publicKey/certificate".
5. **#54456 → #55158, clientTemplate bypasses the allowed-scopes check** (D3 1, PLANNED). The fact is that `convertClientScopeName(clientTemplate)` reaches the scope attach without passing the policy check that `clientScopes` passes through. `diverges` between the two inputs shows it.
6. **#52173 → #52238, realm_client round-trip** (D3 1, PLANNED). In RepresentationToModel's attribute loop, a computed attribute (`REALM_CLIENT`) goes back into the model on import. `blastRadius` on the computed value would show it reaching a persisted setter.
7. **#53497 → #53518, organization mapper** (D3 1, PLANNED). The sibling mapper had been fixed in #43339 to read `session.getContext().getOrganization()`. This mapper still read a client-session note. `diverges` between the two mappers' summaries would show the difference.
8. **#54485 → #54643, wrong client session detached** (D3 1, PLANNED). `slice_back` from `detachFromUserSession` shows no condition on the client id. The fix adds `client.getId().equals(...)`.

## Looked useful, are not
- **#49601 → #49604, SAML missing setter** (`setTurnOffChangeSessionIdOnLogin` never called). This is textbook missing-propagation. In the trial, no `sp.isX()` → `deployment.setX()` flow came out, so the missing pair could not be seen. Scored 0. **Corrected by the [review](../review.md#keycloak-49604-run-before-and-after-casts):** the trial blamed the unmodelled `TYPE_CAST`, and that was wrong. With casts modelled the flows are still missing. `sp` comes from `adapter.getSps().get(0)`, an object with no tracked origin, so each field read is a gap and its value is dropped. Those gap rows still name 13 of `SP`'s 14 fields as read in `build`; the one missing is `turnOffChangeSessionIdOnLogin`. A `readers(field)` query finds the bug today. The score was not revised.
- **#53545, session-note disclosure CVE.** The note name comes from runtime mapper config strings, so no static key exists to slice on.
- **#54339 / #54176, auth and client notes lost or mixed.** These are string-keyed notes in Infinispan, where writer and reader are different requests. The FORCEAUTHN fix (#54369) only moves `setAuthNote` to `setClientNote`. Without key-aware heap slots, both are opaque `EXTERNAL` calls.
- **#55028 / #54414, credential update races.** The bugs are about transactions and isolation, not dataflow.
- **#51137, missing enum case in a switch.** It looks like a "no path" fact, but the gap is a constant nobody wrote. A graph has nothing to show.

## Surprises
- **D2a is 0 everywhere.** Keycloak authors already know their own flow. The work is in tests, migrations and backports.
- **Mapping bugs go through string-keyed maps, not typed fields.** Auth/client/user-session notes, `contextData`, `token.getOtherClaims()`, rep/client attributes and component config are all keyed by string constants. The "field not copied" pattern the study expected (entity ↔ rep ↔ model) appeared once with typed setters (#49604), and the cast hid it there.
- **SPI interfaces cost less than expected inside one method, more across methods.** Most bugs are local to one provider class, where codeflow reads well. Every hop out (`session.users()`, `RealmModel`, `UserSessionModel`) is `EXTERNAL`, so no cross-class question about where a value ends up gets an answer. That is correct behaviour (no guessing), but it caps D4.
- **Security review is mostly policy, not flow.** Of 5 security comments, 1 is a reachability question. The rest are enumeration, string semantics (dotless ı) and permissions.

## Tool problems seen (not guessed)
- **TYPE_CAST is not modelled.** It does not appear in `app/src/main/kotlin`. Every cast is an UNMODELLED node and the run exits 1. OIDCIdentityProvider has 18 casts. Reads through a cast object give "read on an object with no tracked origin" gaps. This blocked the SAML case.
- **No classpath.** 2177 of 3049 references in OIDCIdentityProvider are unresolved. Fine for local flow, but almost every call is `EXTERNAL`.
- **Summaries merge control and data dependence.** In OIDCIdentityProvider, 25 sources reach `put@508`, too many to point at anything. The `--json` graph separates them (CONDITION vs FLOW) but has to be read by hand.
- **No `--from` for a method reached only via DI.** Roots have to be named one by one. `scripts/smoke.sh` helps for sweeps, not for questions.

## Capability that would most raise scores
1. **Constant-keyed map slots.** Treat `setAuthNote/getAuthNote`, `setClientNote/getNote`, `contextData.put/get`, `getOtherClaims().put`, `attributes.put/getAttribute` and config `get` with a compile-time constant key as a named heap slot, and give the session store (Infinispan/JPA adapters) typed boundary slots. Then a `slice_fwd` from a note to a token claim is possible. About 9 of 32 issues (#53545, #54339, #54176, #53518, #53029, #52173, #53684, #54369, #53559) turn on such a slot, so D3/D4 there would go from 0–1 to 1–2.
2. **Model TYPE_CAST** as a pass-through with the cast type as the dispatch hint. It is cheap and unblocks the one typed missing-propagation case and most of OIDCIdentityProvider.
3. **The `diverges` query** (PLANNED). Four of the best cases (#55027, #54990, #53518, #55116) are "this path does what its sibling forgets".
