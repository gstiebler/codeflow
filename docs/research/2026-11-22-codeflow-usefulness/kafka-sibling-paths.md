# Kafka: "this path forgets what its sibling path does" (2026-11-22)

Back to the [main report](README.md).

**Answer.** Six Kafka fixes, all verified against the pre-fix source. Five are a direct sibling omission and one
is a variant. In every case the rule is an **effect**: a refreshed index, an emitted record, a timer with the
right argument, a listener notification. The path that misses it is the **rarer** one: classic-to-consumer,
load, downgrade, not-in-group. Finding them needs a `diverges` query over per-root effect sets; reachability
alone is not enough for #4.

**Method.**
- Every PR was squash-merged, so the pre-fix code is the merge commit's parent, fetched with
  `gh api repos/apache/kafka/contents/<path>?ref=<parent>`.
- Line numbers refer to that ref.
- Dropped from the earlier pass: #24153. It is a performance refactor, not a fix. Its review exposed a latent
  NPE in `StreamsGroup.removeMember` that nobody fixed.

**Abbreviations:**
- `GMM` = `group-coordinator/src/main/java/org/apache/kafka/coordinator/group/GroupMetadataManager.java`
- `CG` = `…/group/modern/consumer/ConsumerGroup.java`
- `SG` = `…/group/modern/share/ShareGroup.java`

## The four forms

| Form | Example |
|---|---|
| An effect missing entirely | #1, #6 |
| An extra effect in one override | #2 |
| One sink fed from different origins | #3, #5 |
| The same effect, gated in one sibling by a condition that is dead there | #4 |

---

## 1. A classic heartbeat to a consumer group skips the metadata refresh and the assignment update

- PR [#24108](https://github.com/apache/kafka/pull/24108). JIRA
  [KAFKA-21431](https://issues.apache.org/jira/browse/KAFKA-21431) and
  [KAFKA-21458](https://issues.apache.org/jira/browse/KAFKA-21458).
- Pre-fix ref `146cf78`, file `GMM`.

**Path A: `consumerGroupHeartbeat`** (l.2704–2729). `classicGroupJoinToConsumerGroup` and `shareGroupHeartbeat`
do the same.
```java
        if (bumpGroupEpoch || group.hasMetadataExpired(currentTimeMs)) {
            UpdateSubscriptionMetadataResult result = updateSubscriptionMetadata(
                group, bumpGroupEpoch, member, updatedMember, records);
            // …
        }
        UpdateTargetAssignmentResult<Assignment> updateTargetAssignmentResult = maybeUpdateTargetAssignment(
            group, groupEpoch, member, updatedMember, subscriptionType, records);
```

**Path B: `classicGroupHeartbeatToConsumerGroup`** (l.8794–8826)
```java
        ConsumerGroupMember member = validateConsumerGroupMember(group, memberId, instanceId);
        throwIfMemberDoesNotUseClassicProtocol(member);
        throwIfGenerationIdUnmatched(memberId, member.memberEpoch(), request.generationId());
        scheduleConsumerGroupSessionTimeout(groupId, memberId, member.classicProtocolSessionTimeout().get());
        Errors error = Errors.NONE;
        if (member.memberEpoch() < group.assignmentEpoch() || /* … */)
        return new CoordinatorResult<>(
            List.of(),
            new HeartbeatResponseData().setErrorCode(error.code())
        );
```
B never calls `updateSubscriptionMetadata` or `maybeUpdateTargetAssignment`, and it emits no records. If every
member uses the classic protocol, nothing recomputes the assignment.

**Fix:**
```diff
+        List<CoordinatorRecord> records = new ArrayList<>();
+        if (group.hasMetadataExpired(time.milliseconds())) {
+            UpdateSubscriptionMetadataResult result = updateSubscriptionMetadata(group, false, member, member, records);
+        UpdateTargetAssignmentResult<Assignment> updateTargetAssignmentResult = maybeUpdateTargetAssignment(
+            group, groupEpoch, member, member, subscriptionType, records);
-        if (member.memberEpoch() < group.assignmentEpoch() ||
+        if (member.memberEpoch() < updateTargetAssignmentResult.targetAssignmentEpoch() ||
-            List.of(),
+            records,
```

**How it was found.**
- The PR fixed the missing assignment update.
- The reviewer then found the missing refresh by comparing B with its siblings: "like `consumerGroupHeartbeat`
  and `classicGroupJoinToConsumerGroup` do?"
- The same review found the same shape in a test helper: `sendClassicGroupHeartbeat` "does not replay the
  returned records, unlike `consumerGroupHeartbeat`".

**What `diverges` compares.** The in-source callees each handler reaches, across the four heartbeat/join
handlers. Three reach `hasMetadataExpired`, `updateSubscriptionMetadata` and `maybeUpdateTargetAssignment`; one
reaches none. A majority vote over the family flags B. A plain A/B diff would drown it in protocol-specific
noise (regex, static members, assignors).

**Today?** Mostly yes. The receivers are concrete classes, so dispatch resolves. `records.add` is a `java.util`
sink. The generated `*RequestData` classes go `EXTERNAL`, which does not matter for this comparison.

---

## 2. `ShareGroup.getOrMaybeCreateMember` inserts the member; `ConsumerGroup`'s only builds it

- PR [#24128](https://github.com/apache/kafka/pull/24128) (MINOR, no JIRA).
- Pre-fix ref `8e75faf`.
- Both methods override the abstract `ModernGroup#getOrMaybeCreateMember`.

**Path A: `CG#getOrMaybeCreateMember`** (l.244–270)
```java
    /** Gets or creates a new member but without adding it to the group. … */
    public ConsumerGroupMember getOrMaybeCreateMember(String memberId, boolean createIfNotExists) {
        ConsumerGroupMember member = members.get(memberId);
        if (member != null) return member;
        // … throw UnknownMemberIdException if !createIfNotExists
        return new ConsumerGroupMember.Builder(memberId).build();
    }
```

**Path B: `SG#getOrMaybeCreateMember`** (l.207–225)
```java
    public ShareGroupMember getOrMaybeCreateMember(String memberId, boolean createIfNotExists) {
        ShareGroupMember member = members.get(memberId);
        if (member != null) return member;
        // …
        member = new ShareGroupMember.Builder(memberId).build();
        updateMember(member);      // members.put + three maybeUpdate* calls
        return member;
    }
```
B writes timeline state outside record replay. If the heartbeat then throws, the member stays in the group with
no session timeout, and it counts toward `group.share.max.size`.

**Fix:**
```diff
-        member = new ShareGroupMember.Builder(memberId).build();
-        updateMember(member);
-        return member;
+        return new ShareGroupMember.Builder(memberId).build();
```

**How it was found.** By the author, from the PR body: "inserts the new member into the group, unlike
`ConsumerGroup`".

**What `diverges` compares.** The effects on `this` of every override of one method. B mutates `this.members`
and reaches three `maybeUpdate*` methods; A only reads.

**Today?** Very nearly. Summaries are keyed per receiver class, so each override has its own. The catch:
`TimelineHashMap` is in `server-common`, so without those sources the write is `ext-arg:put` on
`this.members`, not a field effect. The query must count "an opaque mutator on a `this.<field>` receiver" as a
write.

---

## 3. Group load schedules the consumer-protocol timeout for classic members

- PR [#23856](https://github.com/apache/kafka/pull/23856), JIRA
  [KAFKA-21303](https://issues.apache.org/jira/browse/KAFKA-21303).
- Pre-fix ref `a3444d8`, file `GMM`.

**Path A: the classic-member call sites** of `scheduleConsumerGroupSessionTimeout` pass the member's own
timeout:
```java
// throwIfClassicMemberCannotJoinConsumerGroup l.1493, classicGroupSyncToConsumerGroup l.8540,
// classicGroupHeartbeatToConsumerGroup l.8772:
scheduleConsumerGroupSessionTimeout(groupId, memberId, member.classicProtocolSessionTimeout().get());
// classicGroupJoinToConsumerGroup l.2957, in the appendFuture.whenComplete lambda:
scheduleConsumerGroupSessionTimeout(groupId, response.memberId(), sessionTimeoutMs);
```

**Path B: `onLoaded`**, consumer-group case (l.6932–6934). It iterates over all members, classic ones included:
```java
                    consumerGroup.members().forEach((memberId, member) -> {
                        scheduleConsumerGroupSessionTimeout(groupId, memberId);   // 2-arg overload
    // …
    private void scheduleConsumerGroupSessionTimeout(String groupId, String memberId) {
        scheduleConsumerGroupSessionTimeout(groupId, memberId, consumerGroupSessionTimeoutMs(groupId));
    }
```
After a coordinator failover, classic members expire too early or too late.

**Fix:**
```diff
-                        scheduleConsumerGroupSessionTimeout(groupId, memberId);
+                        scheduleConsumerGroupSessionTimeout(groupId, memberId,
+                            member.classicProtocolSessionTimeout().orElse(consumerGroupSessionTimeoutMs(groupId)));
```

**How it was found.** From the bug report: `onLoaded()` "ignores this value and applies the consumer protocol
session timeout".

**What `diverges` compares.** Every call site of one callee, per argument: the origin atoms reaching
`sessionTimeoutMs`.
- Five sites are fed by the member's or the request's timeout.
- Two are fed by group config: `consumerGroupHeartbeat`, which is correct because only consumer members reach
  it, and `onLoaded`, which is the bug.
- Telling the two apart needs a population hint: `onLoaded` iterates over all members without branching on
  `useClassicProtocol()`.

**Today?** Partly.
- Argument origins into an `EXTERNAL` sink (`timer.schedule`) are what summaries' `sinks` already record.
- `forEach((id, member) -> …)` lambda parameters bind `Identity.Unknown`, so the fixed code's `member.…` has no
  origin.
- Overloads share one `Class#method` name in `--from`.

---

## 4. Leaving after unsubscribe does not notify the assignment change; the sibling branch does

- PR [#24155](https://github.com/apache/kafka/pull/24155), JIRA
  [KAFKA-21455](https://issues.apache.org/jira/browse/KAFKA-21455).
- Pre-fix ref `62dca4d`, file `clients/…/consumer/internals/AbstractMembershipManager.java`.

**Path A: `leaveGroup`**, not-in-group branch (l.628–636):
```java
        if (isNotInGroup()) {
            if (state == MemberState.FENCED) { clearAssignment(); transitionTo(MemberState.UNSUBSCRIBED); }
            subscriptions.unsubscribe();
            notifyAssignmentChange(Collections.emptySet());
            return CompletableFuture.completedFuture(null);
        }
```

**Path B: `clearAssignmentAndLeaveGroup`** (l.675–683), the in-group case. It relies on `clearAssignment` to
notify:
```java
    private void clearAssignmentAndLeaveGroup() {
        subscriptions.unsubscribe();
        clearAssignment();
        transitionToSendingLeaveGroup(false);
    }
    private void clearAssignment() {
        if (subscriptions.hasAutoAssignedPartitions()) {     // false after unsubscribe()
            subscriptions.assignFromSubscribed(Collections.emptySet());
            notifyAssignmentChange(Collections.emptySet());
        }
```
`unsubscribe()` sets the subscription type to `NONE`, and `hasAutoAssignedPartitions()` is true only for the
`AUTO_*` types. So the guard is dead, the application thread's copy of the assignment is never cleared, and
`close()` fires the rebalance callbacks again.

**Fix:**
```diff
         subscriptions.unsubscribe();
         clearAssignment();
+        notifyAssignmentChange(Collections.emptySet());
```

**How it was found.** From the bug report. In review, the author justified the fix by the siblings: "This is also
how other places that change the assignment do it".

**What `diverges` compares.** The effects that follow a shared anchor call (`unsubscribe()`), **with their
gates**. Reachability alone sees `notifyAssignmentChange` in both and reports nothing. The row that matters is:
ungated in A, gated in B by `hasAutoAssignedPartitions()`, read after `unsubscribe()` on the same receiver.

**Today?** Weakly. `subscriptions` is an interface with two implementations, so its calls are `EXTERNAL` and the
dead guard cannot be derived. The honest output is the gate difference, which `CONDITION` edges can carry if the
query compares gates.

---

## 5. (Variant) Two paths read a classic member's subscription from different protocols

- PR [#24187](https://github.com/apache/kafka/pull/24187), JIRA
  [KAFKA-21467](https://issues.apache.org/jira/browse/KAFKA-21467).
- Pre-fix ref `4b2ad74`.

**Path A: `GMM#deserializeSubscription`** (l.2071–2081), used by `classicGroupJoinToConsumerGroup`:
```java
        try {
            return ConsumerProtocol.deserializeConsumerProtocolSubscription(
                ByteBuffer.wrap(protocols.iterator().next().metadata()));
        } catch (SchemaException e) { /* … */ }
```

**Path B: `CG#fromClassicGroup`** (l.1281–1285), inside `allMembers().forEach(…)`:
```java
            ConsumerProtocolSubscription subscription = ConsumerProtocol.deserializeConsumerProtocolSubscription(
                ByteBuffer.wrap(classicGroupMember.metadata(classicGroup.protocolName().get())));
```
B depends on the group's selected protocol:
- With none selected yet, it throws `NoSuchElementException`.
- When a member does not support the selected one, it throws `IllegalArgumentException`.

Either way the upgrading consumer gets a fatal `UNKNOWN_SERVER_ERROR`.

**Fix:**
```diff
-                ByteBuffer.wrap(classicGroupMember.metadata(classicGroup.protocolName().get()))
+                ByteBuffer.wrap(classicGroupMember.supportedProtocols().iterator().next().metadata())
```

**How it was found.** From the bug report, with reproduction steps. A relayed AI review noted that the rule
"now lives in 2 or 3 places".

**What `diverges` compares.**
- The origin chain of one shared sink's argument: `protocols → iterator → next → metadata` against
  `protocolName → get → metadata(String)`.
- The error handling around the sink: A catches `SchemaException`, B does not.

**Today?** Partly. Sink-argument origins come from summaries. The lambda parameter is `Identity.Unknown`. There
is no exception-edge model, so the `catch` difference is invisible.

---

## 6. Removing a group without replay leaves it in the `groupsByTopics` index

- PR [#23530](https://github.com/apache/kafka/pull/23530), the part that fixes
  [KAFKA-21176](https://issues.apache.org/jira/browse/KAFKA-21176).
- Pre-fix ref `e235a31`, file `GMM`.

**Path A: `replay`** of member records (tail l.6091–6098). Every member removal keeps the index in step:
```java
            consumerGroup.removeMember(memberId);
        }
        updateGroupsByTopics(groupId, oldSubscribedTopicNames, consumerGroup.subscribedTopicNames().keySet());
```

**Path B:** `convertToClassicGroup` (l.1388), `maybeDeleteEmptyConsumerGroup` (l.9885) and
`maybeDeleteEmptyStreamsGroup` (l.9899). They call `removeGroup` without replaying the tombstones:
```java
        // Directly update the states instead of replaying the records …
        removeGroup(consumerGroup.groupId());
// …
    private void removeGroup(String groupId) {
        groups.remove(groupId);
    }
```

**Fix** (excerpt):
```diff
-        groups.remove(groupId);
+        Group group = groups.remove(groupId);
+        if (group instanceof ModernGroup<?> modernGroup) {
+            modernGroup.subscribedTopicNames().keySet()
+                .forEach(topicName -> unsubscribeGroupFromTopic(groupId, topicName));
+        } else if (group instanceof StreamsGroup streamsGroup) { /* … requiredTopics … */ }
```

**How it was found.**
- The downgrade case came from the bug report.
- The streams twin was found in review: "`maybeDeleteEmptyStreamsGroup` removes an empty streams group without
  replaying its tombstones".

**What `diverges` compares.** Here the siblings are not related by name but by a **shared effect**: both end a
group's or member's life. The rule to mine is "whoever removes from `groups` or members also updates
`groupsByTopics`". It holds on replay and is missing on three direct paths.

**Today?** No. The effects are visible as `ext-arg` sinks on `this.groups` and `this.groupsByTopics`, but
pairing the roots needs co-occurrence mining over effects.

---

## Spec sketch: `--diverges`

**Families**, from the most explicit to the most inferred:

| Selector | Siblings are | Example |
|---|---|---|
| `--diverges A B` | two named roots | any |
| `overrides:M` | every override of one method | #2 |
| `callers:M` | every call site of one callee, compared per argument | #3, #5 |
| `stem:*GroupHeartbeat*` | name-stem siblings in one class | #1 |
| `anchor:M` | blocks that call one anchor; compare what follows it | #4 |
| `cowrite:F` | writers of field F; mine effects that co-occur in ≥ k of n | #6 |

**Effect vocabulary per root.** Transitive, read from summaries, instantiated through in-source callees:
1. In-source methods reached, by `Element`.
2. Field effects on `this.<field>`. An opaque mutator (`put`, `remove`, `add`, `computeIfPresent`) on a
   `this.<field>` receiver also counts as a write.
3. Opaque sinks, by label and receiver atom (`timer.schedule`, `records.add`).
4. For a sink the family shares, the origin atoms of each argument.
5. The gate of each effect: the condition atoms whose `CONDITION`/`TRUE`/`FALSE` edges dominate it.

**Output.** One row per effect:

`effect | present in (root, file:line) | absent in (roots) | differs in (origin or gate) | gaps`

- **Ranking.** Effects present in most siblings and absent in few rank first. Effects common across the whole
  codebase (logging, metrics) are down-weighted.
- **Silence rule.** An effect missing from a root that has a `dispatch`, `unmodelled`, `alias` or `body` gap on
  a path that could reach it is reported as **unknown**, never as absent. Otherwise this query becomes the
  silently wrong graph the project exists to avoid.

**Prerequisites for Kafka, cheapest first:**
1. **Generated message classes.** Run Kafka's message generator first, as `scripts/delombok.sh` does for
   Lombok, or accept them as `EXTERNAL`.
2. **Several modules' sources:** `group-coordinator` + `coordinator-common` + `server-common`, or `--boundary`
   on their types.
3. **Lambda parameters of `forEach` over a collection** bind to the collection's element origin. They are
   `Identity.Unknown` today, and this idiom is in #3, #5 and #6.
4. **Interfaces with two implementations** (`subscriptions`) are reported as gaps, not as absence.
5. **Gates are compared, not only reachability.** #4 needs it.
6. **No Scala needed.** Only `core`'s integration tests are Scala.
