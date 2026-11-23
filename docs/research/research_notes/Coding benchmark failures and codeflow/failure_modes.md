# Why LLM coding agents fail on repository-level tasks: failure taxonomies and measured shares (2024–2026)

Scope note: 18 tool calls. The primary papers were fetched for: Liu et al. 2509.13941, Majgaonkar et al. 2511.00197, SWE-Bench Pro 2509.16941, Hunk4J agentic follow-up 2511.11012, and the abstract of Jiang et al. 2605.12270. Items marked "(snippet)" were seen only in search-result summaries and were not checked against the full text.

## Q1. What share of failures are localization vs repair (edit) vs validation failures?

### Takeaway
Localization is no longer the main failure for frontier agents. Agents usually find the right **file**: 60–80% of failed trajectories still touch it. They much less often reach the right **function or hunk**. Most remaining failures are wrong repair logic or strategy, or iteration and validation that goes wrong. Localization still dominates for pipeline tools (Agentless) and for weaker models.

### Cited Findings
- **Liu et al., "An Empirical Study on Failures in Automated Issue Solving" (arXiv 2509.13941, Sep 2025).** 3 tools (OpenHands-CodeAct-2.1, Tools+Claude 3.5 Sonnet, Agentless-1.5) on SWE-bench Verified. They studied 150 failed issues and found 342 failure instances. Taxonomy: 3 phases, 9 categories, 25 subcategories.
  - Localization: A1 issue misleading; A2 superficial information matching (keywords in description, referred code, error stack trace).
  - Repair: B1 fix-strategy defects (specific-case overfitting, evasive repair, redundant erroneous implementation); B2 implementation-detail defects (logic, data processing, domain knowledge); **B3 incomplete repair (ignoring explicit dependencies, ignoring semantic dependencies)**; B4 issue interference.
  - Iteration & validation: C1 reproduction or verification failure; C2 iteration anomalies (non-progressive, blind strategy switching, validation retreat); C3 context amnesia. — [arXiv 2509.13941](https://arxiv.org/html/2509.13941v1)
- Same study, shares by phase:
  - **Agentless:** 51.3% of failed issues contain a localization error.
  - **OpenHands:** 49.1% contain an iteration & validation error.
  - **Tools+Claude:** 51.7% contain an iteration & validation error.
  - For OpenHands, iteration & validation failures rise from 27% (Easy) to 64% (Hard). Easy tasks show 41% localization failures.
  - Pipeline tools fail early (localization). Agents fail late, often by looping.
  — [arXiv 2509.13941](https://arxiv.org/html/2509.13941v1)
- Same study, causes: roughly 65% flawed reasoning, 25% knowledge deficiency, 10% environmental friction. Failed issues took about 3.5× more interaction steps than resolved ones. Exact per-subcategory counts appear only in figures 6–10. — [arXiv 2509.13941](https://arxiv.org/html/2509.13941v1)
- **Majgaonkar et al., "Understanding Code Agent Behaviour: An Empirical Study of Success and Failure Trajectories" (arXiv 2511.00197, Nov 2025).** Agents: OpenHands, SWE-agent, Prometheus.
  - **File-level** localization hit rate is 92–97% in successful runs. In failed runs it is 72.5–81.4% on Lite and 59.3–67.1% on Verified.
  - **Function-level** hit rate is 21–34% in successes and 14–27% in failures.
  - **Exact-hunk** hit rate is 2.7–8.4% in successes and 0–1.6% in failures.
  - So successful patches rarely match the gold hunk exactly. Being approximately right is enough.
  — [arXiv 2511.00197](https://arxiv.org/html/2511.00197v1)
- Same study: failed trajectories are longer. On Verified: OpenHands +82.5% steps (54 → 99), Prometheus +50.7%, SWE-agent +18.5%. Variance also grows (Prometheus Lite SD 34.6 → 100.8). Reproduction-test behaviour is described only qualitatively. — [arXiv 2511.00197](https://arxiv.org/html/2511.00197v1)
- **Jiang, Huang, Wang, Chen, Liu, Briand, "Characterizing the Failure Modes of LLMs in Resolving Real-World GitHub Issues" (arXiv 2605.12270, May 2026).** Models: Claude 4.5 Sonnet, Gemini 3 Pro, GPT-5 under mini-SWE-agent on SWE-bench Verified. They studied 243 failed attempts out of 900 trials, using a 5-stage taxonomy.
  - **"Strategy formulation and logic synthesis" is the most error-prone stage for every model.** Problem understanding comes next.
  - **Localization has the lowest failure rate.**
  - The authors also note the harness sometimes misjudges correct patches.
  - The abstract gives no per-stage percentages.
  — [arXiv 2605.12270](https://arxiv.org/abs/2605.12270)
- **SWE-Bench Pro (Scale AI, arXiv 2509.16941, Sep 2025)**, Table 4. An LLM judge (GPT-5) labelled the last 20 turns of each unresolved run.
  - Wrong-solution share of submitted failures: Opus 4.1 50.3%, GPT-4o 45.2%, GPT-5 39.5%, Gemini 2.5 Pro 35.0%, Sonnet 4 25.5%, Qwen3-32B 25.0%.
  - Syntax-error share: Gemini 56.5%, Qwen3 48.7%, Opus 31.3%.
  - Incorrect-file share: Qwen3 19.7%, GPT-4o 11.2%, GPT-5 8.8%, Opus 4.9%.
  - Not-submitted runs were dominated by tool-use errors for most models; Sonnet 4 was dominated by long-context failures (57.4%).
  - Large models fail mostly on semantic or algorithmic correctness in large multi-file edits. Small models fail on syntax, tool use, and context.
  - **Caveat:** the paper's prose figures disagree with its table (prose gives Opus wrong-solution as 35.9%).
  — [arXiv 2509.16941](https://arxiv.org/html/2509.16941v2)
- OpenAI later estimated that about 30% of SWE-Bench Pro tasks are broken (pipeline: 27.4%; human annotation: 34.1%). Some "wrong solution" failures are therefore task defects, not model errors. — [OpenAI, "Separating signal from noise in coding evaluations"](https://openai.com/blog/separating-signal-from-noise-coding-evaluations) (snippet)
- Harness design study (Fan et al., arXiv 2609.20804, Sep 2026): Nemotron-3 30B/120B/550B and Mistral-Medium-3.5 on SWE-bench Verified and Terminal-Bench 2.1, 176 settings.
  - The abstract says context management mainly prevents context-overflow failures.
  - Planning "changes where trajectories stop".
  - The claim "for weaker models more than half of unresolved runs fail at file localization, stronger models reach the right file and lines and fail later" is from a search snippet only. Pages 1–3 of the PDF did not show it.
  — [arXiv 2609.20804](https://arxiv.org/pdf/2609.20804)

### Inferences
- Across studies the bottleneck has moved. The 2024 pipeline tools failed at localization (Agentless about 51%). The 2025–26 frontier agents fail at repair logic, strategy, and validation.
- File-level localization looks largely solved. Function and line localization does not: function-level hit rates are about 30% even in successes.
- Majgaonkar's file hit rate in Verified failures (59–67%) means 33–41% of failed runs never touch the gold file. This matches a snippet claiming 35–40% of failed Verified trajectories fail to locate the file.
- Errors that need cross-function reasoning (B3 "ignoring semantic dependencies", A2.3 patching at the stack-trace site rather than upstream) are reported as categories, but no paper fetched here gives their share.

### Gaps
- Exact per-subcategory shares from Liu et al. (incomplete repair B3, overfitting B1.1) are only in figures that could not be read.
- Jiang et al. 2026 per-stage percentages were not retrieved (full PDF not read).
- Original Agentless (2024) localization accuracy numbers were not fetched in this session.
- No study found that measures "failure to trace a value across functions or files" as its own labelled category with a share. The closest are A2.3 (patching at the stack-trace symptom instead of the upstream root cause, e.g. sympy-12420: patched radsimp.py, root cause in sqrtdenest.py) and B3.2.

## Q2. How often are "resolved" patches actually wrong or incomplete under stricter checking?

### Takeaway
Roughly 7–30% of patches that "pass" differ from developer behaviour. Strict re-evaluation lowers resolve rates by a few to about 6 points absolute, and reorders leaderboards. Older 2024 audits on the original SWE-bench found far larger inflation.

### Cited Findings
- **Wang, Pradel, Liu, "Are 'Solved Issues' in SWE-bench Really Solved Correctly?" (ICSE 2026; arXiv 2503.15223).** On SWE-bench Verified with 3 tools:
  - **29.6% of plausible patches** behave differently from the oracle patch on PatchDiff's generated differential tests.
  - Manual inspection found **28.6% of divergent patches are certainly incorrect**.
  - Reported resolve rates are inflated by **6.2 absolute points**.
  - Root cause: SWE-bench runs only the test files modified in the fix PR, so functionality covered by other test files goes unchecked. This lets regressions elsewhere through.
  - Version caveat: the v1 abstract gave different headline numbers (7.8%).
  — [arXiv 2503.15223](https://arxiv.org/abs/2503.15223); [ICSE 2026 page](https://conf.researchr.org/details/icse-2026/icse-2026-research-track/59/Are-Solved-Issues-in-SWE-bench-Really-Solved-Correctly-An-Empirical-Study)
- **UTBoost (Yu, Zhu, He, Kang, ACL 2025; arXiv 2506.09289).** LLM-generated augmented tests found:
  - 36 task instances with insufficient tests;
  - **345 erroneous patches labelled as passed**;
  - changes to 40.9% of SWE-bench Lite and 24.4% of Verified leaderboard entries, with 18 and 11 ranking changes.
  - The SWE-bench regex log parser also missed many test cases.
  - Counts vary inside the paper (176/169 vs 170/92).
  — [arXiv 2506.09289](https://arxiv.org/abs/2506.09289); [ACL Anthology](https://aclanthology.org/2025.acl-long.189)
- **SWE-Bench+ (Aleithan et al., Oct 2024; arXiv 2410.06992).** On the original SWE-bench (SWE-Agent+GPT-4):
  - about 32.7% of "successful" patches involved solution leakage (the fix was in the issue text);
  - about 31.1% passed because tests were weak.
  - Filtering them cut the resolve rate from about 12.5% to about 4.0%.
  - These figures are recalled from the paper's abstract, not re-fetched this session. Verify before quoting.
  — [arXiv 2410.06992](https://arxiv.org/pdf/2410.06992)
- Liu et al. show plausible-but-incomplete patches pass. **For 13 multi-file human fixes, 12 agent patches passed with single-file edits**, and only 6 of 497 successful Agentless patches touched more than one file. — [arXiv 2509.13941](https://arxiv.org/html/2509.13941v1)
- Overfitting to the issue text (Liu B1.1): django-13551's patch assumed an `email` attribute instead of calling `get_email_field_name()`. It passed the issue's scenario but was wrong in general. — [arXiv 2509.13941](https://arxiv.org/html/2509.13941v1)
- A case in Majgaonkar et al.: an OpenHands sympy patch passed the agent's own tests but failed the `div` unit test, so the root cause was missed. — [arXiv 2511.00197](https://arxiv.org/html/2511.00197v1)

### Inferences
- Benchmark "resolved" is an upper bound. About a third of passing patches do something different from the developer fix, and roughly a quarter to a third of those are clearly wrong. In absolute terms that is about 5–9% of "resolved" patches being incorrect on Verified.
- The validation blind spot is the same in all three audits: only the PR's own tests are run. Regressions in code paths the issue does not mention, and fixes that cover one call site but not its siblings, are exactly what goes unseen.

### Gaps
- No 2026 PatchDiff/UTBoost-style re-audit of the newest frontier models (Claude 4.5+/GPT-5+) was found.
- SWE-bench Verified's own 2024 annotation statistics (underspecified or unfair tests) were not fetched.

## Q3. Multi-hunk / multi-file fixes, and fixes needed in several analogous places

### Takeaway
Resolve rate falls steadily with the number of files and hunks, and with how far apart the hunks are. Agents tend to produce single-location patches even when the human fix spanned several. "Fragment"-class (most dispersed) bugs were unsolved by plain LLMs. Frontier CLI agents (2025) now reach 64–82% on them, but weaker agents stay at 0–18%. No study directly measures the rate of "fixed one instance, missed analogous siblings".

### Cited Findings
- **SWE-Bench Pro:**
  - Gold patches average 107.4 LOC over 4.1 files; every task is at least 10 lines.
  - **Resolve rate falls sharply as file count rises**, and the gap between frontier and open models widens beyond 3 files.
  - On 10+-file tasks, Opus 4.1 and GPT-5 stay above 10%; open models approach 0.
  - Agentless could not handle multi-file editing.
  - Commercial-set resolve rates are low (Opus 4.1 17.8%, GPT-5 15.7%). Public set: Sonnet 4.5 43.6%.
  — [arXiv 2509.16941](https://arxiv.org/html/2509.16941v2)
- **Liu et al.:** multi-file task success was 25.3% (OpenHands), 18.3% (Agentless), 11.3% (Tools+Claude). Category B3 "Incomplete Repair" covers ignoring explicit dependencies (e.g. not updating a parent class or other implementers) and semantic dependencies. Its share is not given in the text. — [arXiv 2509.13941](https://arxiv.org/html/2509.13941v1)
- **Nashid et al., "Characterizing Multi-Hunk Patches: Divergence, Proximity, and LLM Repair Challenges" (ASE 2025; arXiv 2506.04418).**
  - Hunk4J has 372 real Java multi-hunk bugs.
  - Defines **hunk divergence** (lexical, structural, and file-level variation between hunks) and **spatial proximity** classes (Nucleus → Fragment).
  - Across 6 LLMs, success falls as divergence and dispersion rise. **No model fixed any "Fragment" bug** without extra tooling.
  — [arXiv 2506.04418](https://arxiv.org/html/2506.04418v2)
- **"Beyond Accuracy: Behavioral Dynamics of Agentic Multi-Hunk Repair" (arXiv 2511.11012, Nov 2025)**, on Hunk4J (372 bugs):
  - Repair accuracy: Claude Code 93.3%, Codex 87.1%, Gemini-cli 41.7%, Qwen Code 25.8%.
  - Localization: Codex 75.0%, Claude 64.0%, Gemini 46.5%, Qwen 38.2%.
  - Fragment-class repair: Claude 81.8%, Codex 63.6%, Gemini 18.2%, Qwen 0%.
  - Unfixed bugs have higher hunk divergence. Claude's median is 0.41 fixed vs 0.58 unfixed (mean 0.45 vs 0.68).
  - "Localization–repair gap": 5 of the 10 bugs no agent fixed were localized by at least one agent.
  — [arXiv 2511.11012](https://arxiv.org/html/2511.11012v1)
- Same paper, regressions:
  - Repairs introducing net test regressions: Claude 1.3%, Codex 2.2%, Qwen 9.9%, Gemini 10.4%. Worst-case drops for weak agents reached −543 and −488 tests.
  - 59.9% of Qwen's compiling repairs changed no test outcome, which suggests partial or no-op fixes.
  - Failed repairs cost more: 2.3× time on average (670 s vs 287 s) and up to +343% input tokens (Gemini). Gemini's failed runs show more WRITE→WRITE→WRITE thrashing (4.6% → 10.4%).
  — [arXiv 2511.11012](https://arxiv.org/html/2511.11012v1)
- Older base rate: at least 40% of real bugs span several files (Zhong & Su 2015). Defects4J is 62% multi-location and Bugs.jar 76%. A 2026 ACL Findings paper argues that repair methods treat each location independently, and models "Copy" relations between locations (the same patch reused at analogous sites). — [ACL Findings 2026](https://aclanthology.org/2026.findings-acl.1962.pdf) (snippet)
- Analogous sibling paths: a benchmark of LLM repairs for broken access control (arXiv 2610.34290) reports both directions:
  - some LLM patches also protected an unreported sibling path with the same root cause;
  - others did not block the exploit because coverage was incomplete.
  No rates were seen. — [arXiv 2610.34290](https://arxiv.org/pdf/2610.34290) (snippet)

### Inferences
- How dispersed a fix is predicts failure better than its size. Agents miss hunks that look unlike the first hunk (high divergence), which is the sibling and analogous-site problem.
- The SWE-bench Verified results above (12 of 13 multi-file fixes "passed" as single-file patches) show that benchmarks hide this failure: an incomplete multi-site fix often passes the PR's tests.

### Gaps
- No quantitative study found of "the same fix was needed in N analogous places and the agent applied it to k<N". Such cases appear only as a taxonomy category (Liu B3) and as case studies.
- No numbers found on agents failing to trace data flow across functions or files as a measured category. It shows up only implicitly (stack-trace symptom patching, B3.2 semantic dependencies).

## Q4. Test generation for issues: SWT-bench, TDD-Bench Verified, Otter

### Takeaway
Agents write a fail-to-pass reproducing test for about 20% (2024) to 50–63% (late 2025) of issues. About 40–50% of issues still get no valid reproducer. Inability to reproduce is also a named failure category inside repair trajectories.

### Cited Findings
- SWT-bench (2024): the best original system, SWE-Agent+ with GPT-4, reached 19.2% F→P on SWT-bench Lite. Libro reported 19.9% pass@1 on Defects4J. — [TDD-Bench Verified paper](https://arxiv.org/html/2412.02883v1); [SWT-bench](https://arxiv.org/html/2406.12952v3)
- TDD-Bench Verified (Dec 2024): 449 issues from SWE-bench Verified, with extra quality filters. Auto-TDD with GPT-4o reached 23.6% on it and 21.7% on SWT-bench Lite. — [arXiv 2412.02883](https://arxiv.org/html/2412.02883v1)
- Otter / Otter++ (Feb 2025): 37.0% on both SWT-bench Verified and TDD-Bench Verified. — [Otter](https://arxiv.org/html/2502.05368v1); [arXiv 2508.06365](https://arxiv.org/html/2508.06365v1)
- e-Otter++ (execution feedback, 2025): **52.5% on SWT-bench Lite and 63.0% on TDD-Bench Verified** (authors' own evaluation). The SWT-bench leaderboard top, an unpublished Amazon Q Developer Agent, shows 37.7% on Lite and 49.0% on Verified. — [arXiv 2508.06365v3](https://arxiv.org/pdf/2508.06365v3)
- Java (2026): e-Otter++ reaches 43.6% F→P with Claude-Sonnet-4.5 and 46.4% with GPT-5.2. — [arXiv 2605.04320](https://arxiv.org/html/2605.04320) (snippet)
- Within repair trajectories, Liu et al. find:
  - C1.1: reproduction runs fail because of environment problems (Django, Sphinx);
  - **C1.2: reproduction tests check only that code runs, not that the output is correct**;
  - C1.3: reproduction output is misread.
  - Iteration & validation is the single largest failure phase for the two agent tools (about 49–52%).
  — [arXiv 2509.13941](https://arxiv.org/html/2509.13941v1)

### Inferences
- Execution feedback was the step change (37% → 52–63%). Even so, roughly 40% of issues get no valid reproducer, and the agent then validates against weak self-written checks. This is the same gap that PatchDiff and UTBoost expose from the benchmark side.

### Gaps
- No 2026 SWT-bench leaderboard numbers for the newest frontier agents were retrieved.
- No measured share of how often weak self-tests are the cause of plausible-but-wrong patches.

## Q5. Code review / change-impact benchmarks

### Takeaway
Repository-context code-review benchmarks appeared quickly in 2025–26 (SWR-Bench, CR-Bench, SWE-PRBench, AACR-Bench, CRBugBench). Their main motivation is that older datasets (CodeReviewer) are diff-only with no repository context. Comparable model scores could not be retrieved in this session.

### Cited Findings
- **SWR-Bench (Zeng et al., Sep 2025, arXiv 2509.01494):** 1,000 manually verified GitHub PRs with full project context. An LLM judge checks whether the ground-truth issues appear in the generated review. — [arXiv 2509.01494](https://arxiv.org/abs/2509.01494) (snippet)
- **CR-Bench (Mar 2026, arXiv 2603.11078):** focuses on defects rather than style. Reports precision, recall, and F1, plus usefulness rate and signal-to-noise. — [arXiv 2603.11078](https://arxiv.org/html/2603.11078v1) (snippet)
- **SWE-PRBench (Mar 2026):** 350 PRs with ground truth from human reviewer comments. 8 frontier models, judged by an LLM (κ=0.75). — [arXiv 2603.26130](https://arxiv.org/pdf/2603.26130) (snippet)
- **AACR-Bench (Jan 2026):** multilingual, repository-level context. It argues that CodeReviewer offers only diff fragments with no repository context. — [arXiv 2601.19494](https://arxiv.org/pdf/2601.19494v2) (snippet)
- **CRBugBench (Oct 2026, arXiv 2610.13668):** works backward from human-verified bugs to the PRs that introduced them. 1,800 review tasks, 98 OSS + 2 industry repos, 5 languages. Frontier bug-catching rose steadily over 6 months of 2026, and prompt changes were the most effective lever. — [arXiv 2610.13668](https://arxiv.org/pdf/2610.13668) (snippet)
- The PatchDiff finding that SWE-bench runs only the PR's own test files is the change-impact gap seen from the evaluation side: regressions in other test files go unnoticed. — [arXiv 2503.15223](https://arxiv.org/abs/2503.15223)

### Inferences
- What review benchmarks reward (finding the bug a PR introduced) is the same skill repair agents lack in Q2: seeing effects beyond the touched hunk.

### Gaps
- Recall and precision numbers per model for these benchmarks were not retrieved. Full papers were not read.
- No benchmark found that is dedicated to regression or "blast radius" prediction (which tests or callers a change affects) with model scores.

## Q6. Reasoning about program semantics (execution and dataflow reasoning)

### Takeaway
On benchmarks that ask for runtime state, coverage, paths, aliasing or loop counts on real code (not just output prediction on short snippets), LLMs remain weak and inconsistent. This is consistent with the "approximately right" localization and the logic or strategy failures in Q1.

### Cited Findings
- **REval (Chen et al., ICSE 2025; arXiv 2403.16437):** evaluates code coverage, execution path, program state, and output prediction, plus Incremental Consistency across related tasks of rising difficulty. **Average accuracy is 44.4% on runtime-behaviour reasoning, and the average IC score is 10.3.** Models are weak and inconsistent. — [arXiv 2403.16437](https://arxiv.org/pdf/2403.16437); [ICSE 2025](https://conf.researchr.org/details/icse-2025/icse-2025-research-track/32/Reasoning-Runtime-Behavior-of-a-Program-with-LLM-How-Far-Are-We-)
- **CodeSense (Roy et al., ICLR 2026; arXiv 2506.00750):**
  - Fine-grained semantic reasoning on real projects (544 Python, 100 C, 100 Java), with ground truth from execution traces.
  - Tasks at statement, block, and function level: aliasing, loop iteration counts, branch conditions, value propagation.
  - 14 LLMs show a "clear performance gap"; models "lack knowledge of code semantics" even for single statements.
  - Prompting helps only marginally.
  - It criticizes CRUXEval as function-level I/O prediction on short synthetic snippets.
  — [arXiv 2506.00750](https://arxiv.org/html/2506.00750v2); [ICLR 2026](https://iclr.cc/virtual/2026/poster/10008297)
- CRUXEval and CodeMind test only input or output prediction, not intermediate program states. A 2025 follow-up on the coherency of execution reasoning makes this point. — [arXiv 2510.15079](https://www.arxiv.org/pdf/2510.15079) (snippet)
- CRUXEval (Jan 2024, arXiv 2401.03065): GPT-4 with chain of thought scored about 75% on input prediction and 81% on output prediction (pass@1). This is recalled from the abstract and was not re-fetched this session. — [arXiv 2401.03065](https://arxiv.org/abs/2401.03065)

### Inferences
- I/O prediction on small functions is close to saturated for frontier models. Intermediate-state and dataflow questions on real code are not. This is the capability needed to follow a value across functions and to spot analogous sites.

### Gaps
- No 2025–26 per-model CodeSense, REval or CodeMind numbers for current frontier models were retrieved.
- CodeMind's own figures were not found.
- No study found that links low semantic-reasoning scores directly to repository-level repair failures.
