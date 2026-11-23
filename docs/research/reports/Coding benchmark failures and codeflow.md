# Frontier agents miss scope, not files

**Answer.** Frontier coding agents now find the right *file* most of the time. What they still get wrong is
**scope and logic**: the fix needs 3+ files or several look-alike sites, the patch is long, the spec is
missing, or the task runs over many dependent steps. On those tasks scores fall to roughly 0–30%. A
dataflow tool like codeflow targets three of these failures: **edits that miss sibling sites**,
**regressions outside the edited code**, and **checking that a value really reaches where the fix says it
does**. The published evidence is thin and mixed. Graph tools that the agent has to call itself add about
+2 to +6 points, mostly to localization, and the gain shrinks as models get stronger. Strong models barely
call them. The one clear win is **pushed context**: TDAD handed the agent a code→test impact list and cut
regressions by 70%, but in a small study on small models. Codeflow's own measured study fits this picture.
It *confirms* a bug that is already located (6 of 44 Fineract bugs) and has 0 of 168 decisive "prevents a
bug" cases. So today it would add little to an agent on a benchmark. With three planned pieces built, it
fits the failures that remain: `diverges` for sibling sites, a before/after effect diff for regressions,
and single-implementation dispatch so DI-heavy Java is not one big `EXTERNAL`. No one has measured that
yet. Multi-SWE-bench Java and SWE-PolyBench Java are the places to do it.

Sources: research notes of 2026-11-22 (benchmark results, failure modes, graph tools) and codeflow's own
study ([README](../2026-11-22-codeflow-usefulness/README.md),
[review-2](../2026-11-22-codeflow-usefulness/review-2.md), both 2026-11-22). Numbers carry their source and
date. **Aggregator** = Morph, Vals.ai, llm-stats or BenchLM, not the benchmark's authors. **Unverified** =
seen once, or only in a search snippet.

## Frontier agents fail at scope, not at finding files

**What still fails, ranked by how strongly the evidence predicts failure:**

| Failure mode | Evidence (source, date) |
|---|---|
| **3+ files edited** | SWE-bench Pro: stable on single-file tasks, "sharp declines as file count increases"; open models near 0% at 10+ files ([paper](https://arxiv.org/pdf/2509.16941), Sep 2025). SWE-PolyBench: below 10% once 3+ files change ([paper](https://arxiv.org/pdf/2504.08703v1), Apr 2025). |
| **Long patch** | Multi-SWE-bench: resolve rate falls sharply above ~1k patch tokens, "even reaching zero for Java" ([paper](https://arxiv.org/html/2504.02605v1), Apr 2025). |
| **New components (features)** | FEA-Bench, DeepSeek-R1: 18.96% with 1 new function, 5.47% with 3+; best overall ~10% ([paper](https://arxiv.org/html/2503.06680v2), Mar 2025). |
| **Missing spec** | SWE-bench Pro without requirements and interface: GPT-5 25.9% → 8.4%, Opus 4.1 22.7% → 8.2% ([paper, Table 3](https://arxiv.org/pdf/2509.16941), Sep 2025). |
| **Long horizon** | Multi-step SWE: best of 9 frontier models 18.5%; 38 of 69 tasks solved by none; "early design decisions and errors carry forward" ([arXiv 2611.07166](https://arxiv.org/abs/2611.07166), Nov 2026, preprint). SWE-Marathon: under 30% ([arXiv 2606.07682](https://arxiv.org/abs/2606.07682), Jun 2026, preprint). |
| **Hard algorithms** | LiveCodeBench Pro, Hard tier: 0% pass@1 for the best model without tools ([NeurIPS 2025](https://proceedings.neurips.cc/paper_files/paper/2025/hash/9712b78386cebdc3db7f1a48c2d20edb-Abstract-Datasets_and_Benchmarks_Track.html)). A BenchLM 90.8% figure (2026-09-27) names no tier and conflicts with this: **aggregator, unverified**. |
| **Security** | BaxBench: about half of functionally correct backends are exploitable ([alphaXiv](https://www.alphaxiv.org/benchmarks/eth-zurich/baxbench), undated). |

**Which stage breaks.** The bottleneck has moved from finding code to writing the right fix and checking it:
- **Localization fails least** for Claude 4.5 Sonnet, Gemini 3 Pro and GPT-5. "Strategy formulation and
  logic synthesis" fails most ([Jiang et al., arXiv 2605.12270](https://arxiv.org/abs/2605.12270), May 2026;
  the abstract gives no percentages).
- **The file is found; the function often is not.** File hit rate is 92–97% in successful runs and 59–81%
  in failed ones. Function hit rate is only 21–34% even in successes
  ([Majgaonkar et al., arXiv 2511.00197](https://arxiv.org/html/2511.00197v1), Nov 2025).
- **Iteration and validation** is the largest failure phase for agent tools: 49.1% of failed issues for
  OpenHands, 51.7% for Tools+Claude. For OpenHands it rises from 27% on easy tasks to 64% on hard ones
  ([Liu et al., arXiv 2509.13941](https://arxiv.org/html/2509.13941v1), Sep 2025).
- **Large models submit wrong solutions; small models break syntax and tools.** On SWE-bench Pro, the
  wrong-solution share of submitted failures is Opus 4.1 50.3% and GPT-5 39.5%. Syntax errors dominate for
  Gemini 2.5 (56.5%) and Qwen3 (48.7%). The paper's prose and table disagree (prose gives Opus 35.9%)
  ([arXiv 2509.16941](https://arxiv.org/html/2509.16941v2), Sep 2025).
- **Context overflow and endless reading**: Sonnet 4 failures are 35.6% context overflow and 17.0% endless
  file reading ([same](https://arxiv.org/pdf/2509.16941)).

**Sibling and multi-site fixes are the failure that benchmarks hide.**
- 12 of 13 multi-file human fixes were "passed" by single-file agent patches, and only 6 of 497 successful
  Agentless patches touched more than one file ([Liu et al.](https://arxiv.org/html/2509.13941v1), Sep 2025).
- On SWE-bench Verified, 29.6% of plausible patches behave differently from the developer's fix, and
  reported resolve rates are inflated by 6.2 points. SWE-bench runs only the test files the fix PR touched
  ([Wang et al., ICSE 2026](https://arxiv.org/abs/2503.15223)).
- Hunk4J (372 Java multi-hunk bugs): unfixed bugs have more divergent hunks. Claude Code's median
  divergence is 0.41 on fixed bugs and 0.58 on unfixed ones. On the most dispersed class ("Fragment"),
  Claude Code fixes 81.8% and Qwen Code 0% ([arXiv 2511.11012](https://arxiv.org/html/2511.11012v1), Nov 2025).
- **No study measures "the fix was needed in N analogous places and the agent applied it in k<N".** It
  exists only as a taxonomy category (Liu B3 "incomplete repair") and as case studies.

**Semantic reasoning is still weak on real code.** On runtime-behaviour questions (coverage, path, state),
average accuracy is 44.4% ([REval, ICSE 2025](https://arxiv.org/pdf/2403.16437)). On aliasing, loop counts and
value propagation over real Python/C/Java projects, 14 LLMs show a "clear performance gap"
([CodeSense, ICLR 2026](https://arxiv.org/html/2506.00750v2)). No 2026 frontier-model numbers were found for
either.

**Benchmark noise is large.**
- OpenAI audited 138 SWE-bench Verified problems that o3 did not solve consistently. 59.4% had material
  flaws, and all frontier models tested reproduced gold patches for some tasks
  ([OpenAI, 2026-02-23](https://openai.com/index/why-we-no-longer-evaluate-swe-bench-verified/)).
- About 30% of SWE-bench Pro tasks are broken, and OpenAI withdrew its recommendation to adopt it
  ([OpenAI, 2026-07-08](https://openai.com/index/separating-signal-from-noise-coding-evaluations/)).
- Vendor-reported Pro scores (for example Opus 5.5 at 89.9%) run 10–30 points above Scale's standardised
  runs: **aggregator** ([Morph via llm-stats](https://www.morphllm.com/swe-bench-pro), read 2026-09/10).
- So a few-point gain on any board is noise. A tool study needs paired runs and a stricter check than the
  PR's own tests.

## Java resolves at about half Python's rate, and its tasks are bigger

| Benchmark (date) | Java | Python | Note |
|---|---|---|---|
| Multi-SWE-bench, best per language (Apr 2025) | **22.66%** | 52.20% | Java hard tasks (≥1 h): **0.00%** ([paper](https://arxiv.org/html/2504.02605v1)) |
| Multi-SWE-bench Java board (2025-12-08) | **33%** (iSWE + Claude 4.5 Sonnet) | "~70–80%" (no exact figure) | most Java agents "in the 20s and 30s" ([IBM](https://research.ibm.com/blog/ibm-software-engineering-agent-tops-the-multi-swe-bench-leaderboard-for-java)) |
| SWE-PolyBench (Apr 2025) | 11–16% | 20–24% | Java edits 3.6 files on average vs Python 2.0 ([paper](https://arxiv.org/pdf/2504.08703v1)) |
| SWE-bench Multilingual (undated board) | 53.49% (23/43) | n/a | small set, 17 of 43 tasks are Lombok ([swebench.com](https://www.swebench.com/multilingual.html)) |

- **The gap tracks task shape.** 66% of SWE-PolyBench Java tasks change both classes and functions, and
  such "mixed" changes succeed only 8–15%. The authors add that complexity "alone" does not explain it and
  point at training-data mix ([paper](https://arxiv.org/pdf/2504.08703v1)).
- **Wrong localization dominates Java failures** on Multi-SWE-bench, in all three agent methods
  ([paper](https://arxiv.org/html/2504.02605v1)). In this one respect Java looks more like the 2024 Python
  picture than the 2026 one.
- **Gap: no 2026 frontier-model Java vs Python comparison on the same harness exists.** All Java
  per-language numbers above use 2025-era models.

## Graph tools gain 2–6 points, and less for stronger models

**What the ablations show (same model, tool on vs off):**

| Tool (date) | Result | Source |
|---|---|---|
| RepoGraph def/ref graph (ICLR 2025) | +2 to +2.7 pts on SWE-bench Lite agents (e.g. Agentless 27.33 → 29.67). **2-hop flattened context: 26.00, below no graph** | [arXiv 2410.14684](https://arxiv.org/html/2410.14684v2) |
| CodeAnchor call-topology tags (2026) | Func@5 +2.2 pts, Pass@1 +3.4 pts on medium repos, **run-to-run variance roughly halved**, ~+10% tokens | [arXiv 2606.26979](https://arxiv.org/abs/2606.26979) |
| FlowPlan per-function CFG/DFG (Nov 2026) | **no significant change in resolve rate** in 6 settings; tokens 0.32–0.47× (Haiku 4.5), 0.72–0.76× (Sonnet 5) | [arXiv 2611.13241](https://arxiv.org/abs/2611.13241) |
| iSWE Java tools on CLDK (Mar 2026) | **localization equal to bash** (file recall/precision 63/83 vs 62/82, Claude 4.5 Sonnet); cheaper ($1.77 vs $2.06) | [arXiv 2603.11356](https://arxiv.org/html/2603.11356) |
| LSP vs grep (2026, tiny n) | reference precision 1.00 vs 0.76, recall flat; **edits: grep 0.83 vs both tools 0.58** | [arXiv 2608.13568](https://arxiv.org/html/2608.13568) |
| Small-model graph study (Oct 2026) | gain over grep 17–21 pts at 1.7–4B, **4–5 pts at 32B**; about half the gain from 8B up is output format, not finding | [arXiv 2610.33958](https://arxiv.org/html/2610.33958v1) |
| **TDAD code↔test impact graph (Mar 2026)** | **regressions 6.08% → 1.82% (−70%)**; TDD instructions alone raised them to 9.94% | [arXiv 2603.17973](https://arxiv.org/abs/2603.17973) |

**Strong models rarely pull graph tools:**
- Opus 4.8 made **0 LSP calls** in localization when it could choose (50 greps); Sonnet 4%, Haiku 6%
  ([arXiv 2608.13568](https://arxiv.org/html/2608.13568), 2026).
- iSWE on Java, Claude 4.5 Sonnet: `get_inheritance_hierarchy` used **2 of 849** calls; callers and call
  chain together 6% ([iSWE](https://arxiv.org/html/2603.11356), Mar 2026). That is the structure dispatch
  needs, and the agent did not ask for it.
- **0 of 263** neighbour queries asked for 2 hops (Qwen3-32B,
  [arXiv 2610.33958](https://arxiv.org/html/2610.33958v1), Oct 2026).
- Bash alone reaches **>74%** on Verified with mini-SWE-agent
  ([GitHub](https://github.com/SWE-agent/mini-swe-agent)). Across Claude Code and Codex CLI, changing the
  tool surface moved pass rates by under 3 points, not significant
  ([arXiv 2607.10569](https://huggingface.co/papers/2607.10569.md), Jul 2026, read via Hugging Face).

**Pushed context did better than pulled tools:**
- TDAD shipped its graph as a static text "skill" listing affected tests. That is the only controlled
  positive result outside localization. It is **small n (100 and 25 instances) and Qwen models only**; on
  another model and framework the skill also raised resolution from 24% to 32%
  ([arXiv 2603.17973](https://arxiv.org/abs/2603.17973)).
- KGCompass feeds multi-hop paths into the repair prompt as a pipeline. It reports +50.8% relative for
  Claude-4 Sonnet over a pure-LLM baseline. It is not a graph-only ablation
  ([arXiv 2503.21710](https://arxiv.org/abs/2503.21710), 2025).
- CodeAnchor's inline tags were followed only slightly more often (link-following 0.15–0.18 → 0.21–0.24),
  yet they still halved variance ([arXiv 2606.26979](https://arxiv.org/abs/2606.26979)).

**How to reconcile the two findings.** Pulled tools compete with grep at the one stage frontier models
already do well: finding the file. Pushed context lands at the stages they do badly: "what else does this
change touch" and "did my fix break something". It is a different stage, not a better tool. The reading is
mine; no paper tests push against pull on the same tool.

## Codeflow maps onto three failure modes, and two need planned work

What codeflow's own study measured (2026-11-22,
[README](../2026-11-22-codeflow-usefulness/README.md), [review-2](../2026-11-22-codeflow-usefulness/review-2.md)):
- **Decisive on 6 of 44 Fineract bugs** (one in seven) after runs. Kafka 1 of 47; Keycloak 1 of 32 (not
  re-run).
- **In every case the answer was known first**: the tool confirms an already-located bug, a wrong value
  inside one method or class. It does not locate it.
- **Decisive "prevents a bug": 0 of 168** scored items.
- `diverges` on six Kafka sibling cases: **3 found, 1 split visible, 2 missed**, with the siblings named. With
  a name stem (`stem:`) it gives 1,398 rows and flags the buggy handler for 0 sinks.
- The PR check on its own acceptance case (Fineract #6846) gives **0 caller rows today** (17 `dispatch`
  gaps), and 6 with single-implementation dispatch simulated.
- One scorer, a model; scores are rough proportions, not measurements (the study's own caveat).

**Mapping:**

| Benchmark failure mode | Codeflow today | Planned | Fit, and the evidence for it |
|---|---|---|---|
| **Incomplete repair: missed sibling or analogous sites** (Liu B3; Hunk4J divergence) | `scripts/diverges.py` over `--summaries`, with declared families (`overrides:`, `fields:`, named methods) | the per-root effect summary; `callers:` families | **Best fit, unmeasured for agents.** 3 of 6 Kafka cases found. No agent study of sibling tools exists. Benchmarks hide this failure (12 of 13), so it shows only under stricter tests. |
| **Regression outside the edit** (PatchDiff 29.6% divergent; the PR's own tests only) | `--summaries` per method, diffed by hand-written scripts | blastRadius PR check, **after** single-implementation dispatch (improvement 4) and roots that *reach* a changed method | **Strong analogue (TDAD −70%), but codeflow fails its own acceptance case today** (0 rows on #6846). A test-impact list is a different output from an effect diff. |
| **Wrong solution: value from the wrong origin, a dead branch, an unused parameter** (strategy/logic stage) | back-slices on `--json`; `--summaries` (`P1 → RET.timeTo`; "`policy` reaches nothing") | crossed-name and impossible-gate checks (review-2 candidates) | **Matches codeflow's measured mode** (confirm a located wrong value). No agent evidence. Benefit = catching the agent's *own* wrong patch before submitting. |
| **Function-level localization** (21–34% hit rate) | a graph from a root the agent names | root discovery (improvement 8) | **Weak.** Codeflow did not locate bugs in its study. iSWE's Java tools matched bash. Agent uptake of such tools is near zero. |
| **Cross-module / DI chains** (Java "mixed" changes; Doc2Harness, snippet only) | `EXTERNAL` at every interface-typed injected field (~1,928 in Fineract) | improvement 4, drawn as an *assumed implementation* | Needed before any of the rows above work on Spring-style code. |
| **Context overflow, endless reading** | small `--summaries` JSON | MCP queries with small JSON and file:line (improvement 12) | **Cost, not accuracy** (FlowPlan, iSWE). But raw codeflow output is big: 10,812 nodes for one Fineract method, 6,167 NEW rows on one PR. Too much structure lowered RepoGraph's score below no graph. |

## Where codeflow would not help

- **Most benchmarks are not Java.** SWE-bench Verified is 500 Python tasks. SWE-bench Pro has no Java
  ([paper](https://arxiv.org/pdf/2509.16941)). SWE-PolyBench is 1,746 JS/TS tasks of 2,110. Terminal-Bench 2.0
  is shell work (top 73.2% on Vals.ai, 2026-06-04, **aggregator**, archived as saturated). Codeflow reads Java
  only. Its own study puts a generic Python port at ~24% of the value.
- **Missing spec** (Pro 25.9% → 8.4%): no graph says what the code *should* do.
- **Features and greenfield** (FEA-Bench ~10%, Commit0 no full library): most of the code does not exist
  yet, so there is nothing to trace.
- **Hard algorithms** (LiveCodeBench Pro Hard 0%): reasoning, not code structure.
- **Long-horizon design errors** (Multi-step SWE 18.5%): a per-step effect diff could flag drift, but nothing
  tests that.
- **Environment, tool-use and syntax failures** (Gemini 2.5 tool errors 38.8%, syntax 30.5% on Pro): the
  compiler and linter cover these. A compile check moved iSWE node recall/precision from 36/59 to 44/68
  ([iSWE](https://arxiv.org/html/2603.11356)).
- **Codeflow's own blind spots** ([never-sees.md](../2026-11-22-codeflow-usefulness/never-sees.md)):
  concurrency, SQL, config and security DSLs, string semantics, iteration order. BaxBench-style security
  needs taint over frameworks; IRIS used CodeQL for that ([arXiv 2405.17238](https://arxiv.org/abs/2405.17238v3)).
- **Lombok and generated code** need preprocessing. 17 of the 43 Multilingual Java tasks are in Lombok
  itself ([swebench.com](https://www.swebench.com/multilingual.html)).

## What would make codeflow help an agent

1. **Push, don't wait to be called.** Inject a short codeflow note at two moments: after localization ("the
   siblings of this method, and what they do that it does not") and after the edit ("flows this patch
   added or removed, per affected root"). Strong models do not call graph tools (Opus 4.8: 0 LSP calls), and
   TDAD's static list worked.
2. **Build single-implementation dispatch first** (improvement 4). Without it, a DI-heavy Java repo is
   `EXTERNAL` at every service boundary, and the PR check shows 0 rows on its acceptance case.
3. **A post-edit effect diff that keeps the "unknown, not absent" rule.** Without the rule, #6846 reports 14
   removed `saveAll` calls. An agent given a wrong absence will act on it, and that is worse than no note.
4. **Small output with file:line.** Cap it at a few rows and rank them. 2-hop flattened context *lowered*
   RepoGraph below baseline, and 1,398 `diverges` rows are useless to an agent.
5. **Families the code gives** (`overrides:`, `callers:`, `fields:`), not name stems: three measured hits
   against zero.
6. **Optionally, an affected-test list**, the TDAD output, from the same reachability. It is the one shape
   with a controlled result.

## How to measure it

- **Benchmarks:**
  - **Multi-SWE-bench Java**, 128 issues in 9 repos. The best is 33.6% (iSWE + Claude 4.5 Sonnet) and 39.1%
    (InfCode + GPT-5.2) ([iSWE](https://arxiv.org/html/2603.11356), Mar 2026), so there is room.
  - **SWE-PolyBench Java**, 165 tasks with 3.6 files edited on average, the multi-file regime.
  - **Hunk4J** (372 multi-hunk Java bugs) for the sibling claim specifically.
- **Step 0, coverage before any agent run.** Run codeflow on each task's pre-fix repo. Count the
  `EXTERNAL`/`dispatch`/`unmodelled` gaps on the methods the gold patch touches. Then the oracle question,
  as in review-2: does the output contain the gold fact? This bounds the gain cheaply, and if it is low,
  stop there.
- **Arms**, same model and harness (Claude Code or mini-SWE-agent with bash):
  - baseline;
  - codeflow as a pulled MCP tool;
  - codeflow pushed at the two moments in item 1;
  - control: the same instructions with no codeflow output (TDAD's prompt-only arm made regressions worse).
- **Metrics:**
  - resolve rate on the official tests;
  - **regressions on the full test suite**, not just the PR's tests;
  - **PatchDiff-style differential tests** against the gold patch;
  - **hunk/site coverage** vs gold, which is where sibling help would show;
  - tokens, cost, tool-call counts and run-to-run variance.
- **Stratify** by files and hunks touched (1, 2, 3+). The hypothesis is that any gain sits at 3+ files and
  high hunk divergence.
- **Power.** On 128 tasks at ~35%, one run's standard error is about 4 points (my arithmetic). Use ≥3 seeds,
  paired per-task tests (McNemar), and expect ~30% task noise on these boards. Treat a gain under ~5 points
  as not shown.
- **Success bar:** fewer regressions or more complete multi-site patches at equal resolve rate counts as a
  win. The benchmarks do not reward those today, so the resolve rate alone would miss it.

## Conclusion

The question has shifted. In 2024 the question was whether a code graph helps an agent *find* the bug. For
2026 frontier models the answer is mostly no: bash already finds the file. The open question is whether
deterministic evidence helps an agent see the *scope* of a fix: the sibling it forgot and the caller it
broke. Benchmarks under-measure exactly that, because they run only the PR's tests. Codeflow's measured
strength (confirming a wrong value once it is located) and its planned features (sibling divergence, effect
diff) both sit at that stage. That is a better match than "a graph tool for localization". It is also
untested for agents.

The cheapest next experiment is not an agent run. Run the coverage and oracle check on Multi-SWE-bench Java
first. If codeflow's output holds the gold fact on few tasks, as the 6-of-44 Fineract rate suggests it
might, the agent study is not worth running until improvement 4 lands.
