# Where frontier models still fail on AI coding benchmarks (2025–2026), per benchmark

Research date: 2026-11-22. Many current leaderboard numbers come from aggregators (Morph, Vals.ai, llm-stats, BenchLM) and are labelled as such. Model names are copied as the sources give them. Some of them (for example "GPT 6 Astra", "Gemini 4 Argon") appear only in one scraped leaderboard page, and I could not verify them.

## Q1. Top scores today and their breakdown by difficulty, task size, language and repository

### Takeaway
On the harder benchmarks, the shortfall comes from task size and scope, not language syntax:
- **Size cliff.** Solve rates fall steeply once a task touches 3 or more files, needs a patch over about 1k tokens, or adds several new functions. Human "hard" (1 hour or more) tasks fall to near 0% in the 2025 papers.
- **Gap between sets.** Scores drop sharply from the public set to the private or commercial set. The same model also scores 10–30 points lower on a standardised scaffold than in vendor-reported runs.
- **Long-horizon benchmarks (2026).** These still sit at about 18–30%.

### Cited Findings

**SWE-bench Verified (500 Python tasks)**
- Over the six months before Feb 2026, the state of the art rose from 74.9% to 80.9%. OpenAI attributes part of that rise to the models having seen the benchmark during training, not to capability. — [OpenAI, 2026-02-23](https://openai.com/index/why-we-no-longer-evaluate-swe-bench-verified/)
- Epoch AI rates it "Flawed" (review of 2026-09-03). 100% of its tasks and solutions are public, and Epoch says a reward hack achieves a perfect score. — [Epoch AI review](https://epoch.ai/benchmarks/swe-bench-verified/review)
- Verified gold patches edit 1.2 files on average; SWE-bench edits 1.6. 87.2% of Verified tasks are bug fixes and 8.6% are feature requests. — [SWE-PolyBench paper, Table 1 (Apr 2025)](https://arxiv.org/pdf/2504.08703v1)

**SWE-bench Pro (Scale AI; arXiv 2509.16941, Sep 2025)**
- Sets:
  - Public: 731 tasks from 11 GPL repos.
  - Held-out: 858 tasks from 12 repos.
  - Commercial: 276 tasks from 18 private startup repos.
  - Languages: Python, Go, JS, TS. No Java.
- Trivial 1–10 line edits are excluded. Reference solutions average **107.4 LOC across 4.1 files**, and more than 100 tasks need over 100 lines. — [SWE-Bench Pro paper](https://arxiv.org/pdf/2509.16941)
- Launch results (SWE-Agent, 50 turns), public set: Claude Sonnet 4.5 43.6%, Sonnet 4 42.7%, GPT-5 (high) 41.8%, Haiku 4.5 39.5%, Kimi K2 27.7%, GPT-OSS-120B 16.2%. — [paper, Table 1](https://arxiv.org/pdf/2509.16941)
- Launch results, commercial set: Opus 4.1 17.8%, GPT-5 (high) 15.7%, Gemini 2.5 Pro 10.1%, Sonnet 4 9.1%, GPT-4o 3.6%. — [paper, Table 2](https://arxiv.org/pdf/2509.16941)
- **Number of files predicts failure.** Performance is "relatively stable for single-file problems" with "sharp declines as file count increases". Beyond 3 files the gap widens. Opus 4.1 and GPT-5 stay above 10% at 10+ files, while open models approach 0%. — [paper §6.1](https://arxiv.org/pdf/2509.16941)
- **Language.** Go and Python are generally higher (above 30% for some models). JS and TS vary from near 0% to over 30% depending on the model. — [paper §6.1](https://arxiv.org/pdf/2509.16941)
- **Repository.** Some repos stay below 10% for all models, while others let some models exceed 50%. — [paper §6.1](https://arxiv.org/pdf/2509.16941)
- **Scale's standardised board (aggregator, read 2026-09-14, re-checked 2026-10-24).**
  - Public set: Muse Spark 1.1 61.5% (±3.10), GPT-5.4 xHigh 59.1%, Muse Spark 55.0%, Claude Opus 4.6 (thinking) 51.9%, Gemini 3.1 Pro 46.1%.
  - Commercial set: Muse Spark 1.1 51.5% (±5.50), Opus 4.6 47.1%, Muse Spark 44.7%, GPT-5.4 43.4%, Gemini 3.1 Pro 32.2%.
  - Public-to-commercial drop: Opus 4.5 falls from 45.9% to 23.4%; GPT-5.4 falls 15.7 points. — [Morph SWE-bench Pro page](https://www.morphllm.com/swe-bench-pro)
- **Vendor-reported numbers (llm-stats aggregate, via Morph).** Claude Opus 5.5 89.9%, Sonnet 5.5 81.3%, Opus 4.8 69.2%. These run 10–30 points above Scale's standardised runs: Opus 4.8 is 69.2% vendor vs 51.9% for the best Claude run on Scale's board, and GPT-5.2 Codex is 56.4% vendor vs 41.0% on Scale's. — [Morph](https://www.morphllm.com/swe-bench-pro). This is an aggregator comparison, not a controlled one.
- **SWE-Bench Pro V2** (2026-09-22): 642 public tasks, after 89 tasks were dropped as invalid. Its "HARD" tab lists Claude Opus 5 (Claude Code) at 98.0 and several others at 90–92. Neither the subset nor the scale is clear from the page. **Unverified.** — [Scale Labs V2 leaderboard](https://labs.scale.com/leaderboard/swe_bench_pro_public_v2?tab=hard)
- Removing the human-written requirements and interface spec cuts scores from 25.9% to 8.4% (GPT-5) and from 22.7% to 8.2% (Opus 4.1). The reason given: without the spec, the tests reject valid solutions. — [paper, Table 3](https://arxiv.org/pdf/2509.16941)

**SWE-bench Multilingual (300 tasks, 42 repos, 9 languages)**
- Baseline, SWE-agent + Claude 3.7 Sonnet, 42.67% overall: Rust 58.14%, **Java 53.49% (23/43)**, PHP 48.84%, Ruby 43.18%, JS/TS 34.88%, Go 30.95%, C/C++ 28.57% (lowest). — [swebench.com/multilingual](https://www.swebench.com/multilingual.html)
- The Java tasks come from projectlombok/lombok (17), google/gson (9), apache/lucene (9), apache/druid (5), javaparser (2) and rxjava (1). — [same](https://www.swebench.com/multilingual.html)
- No dated leaderboard on that page. I found no 2026 per-language numbers.

**Multi-SWE-bench (ByteDance; 1,632 tasks, 7 languages plus Python; Apr 2025)**
- Best resolve rate per language: Python 52.20% (Claude 3.7, MopenHands), **Java 22.66%** (DeepSeek-R1, MagentLess), Rust 15.90%, C++ 14.73%, TS 11.61%, C 8.59%, Go 7.48%, JS 5.06%. — [Multi-SWE-bench paper, Table 4](https://arxiv.org/html/2504.02605v1)
- By difficulty (Claude 3.7 + MopenHands; easy ≤15 min, medium 15 min–1 h, hard ≥1 h):

  | Language | Easy | Medium | Hard |
  |---|---|---|---|
  | Python | 71.65% | 44.83% | 11.11% |
  | **Java** | **48.15%** | **23.08%** | **0.00%** |
  | C++ | 32.14% | 15.25% | 2.38% |
  | TS | 2.78% | 2.27% | 1.56% |

  On hard issues the paper says resolve rates are "approaching zero". The table text was flattened in the source, so this column alignment is the fetch tool's reading of it. — [paper, Table 5](https://arxiv.org/html/2504.02605v1)
- **Patch length.** Performance "drops as fix patch length increases". Above 1,000 tokens it falls sharply, "even reaching zero for Java". — [paper](https://arxiv.org/html/2504.02605v1)
- **Files modified.** The resolve rate "drops significantly as the number of modified files increases across all three methods". — [paper](https://arxiv.org/html/2504.02605v1)
- **Later Java leaderboard entries** (IBM, 2025-12-08): iSWE-Agent (Claude 4.5 Sonnet) 33% (#1), iSWE-OpenModels 31%, Gemini 2.5 Pro 28.9%, Claude 3.7 Sonnet 23.4%. IBM says most Java agents score "in the 20s and 30s", against roughly 70–80% on the Python leaderboard (IBM gives no exact Python figure). — [IBM Research blog](https://research.ibm.com/blog/ibm-software-engineering-agent-tops-the-multi-swe-bench-leaderboard-for-java)
- MOpenHands + Gemini 2.5 Pro: about 21.6% on 128 Java issues (alphaXiv listing, undated). — [alphaXiv](https://www.alphaxiv.org/benchmarks/peking-university/multi-swe-bench)

**SWE-bench-java (Verified, 91 tasks; Aug 2024)**
- SWE-agent + GPT-4o: 6.59% (6/91). GPT-4o-mini: 1.10%. The authors say the runtime environment was not configured for every Java issue. 53.8% of the tasks are from jackson-databind. — [SWE-bench-java paper](https://arxiv.org/pdf/2408.14354) (2024 numbers)
- I found no 2025–2026 leaderboard for this set. Java evaluation has moved to Multi-SWE-bench and SWE-PolyBench.

**SWE-PolyBench (Amazon; 2,110 tasks, 21 repos; Apr 2025)**
- Tasks per language: Java 165, JS 1,017, TS 729, Python 199.
- Average files modified: Java 3.6, TS 3.1, JS 2.2, Python 2.0. That is 63% more files than SWE-bench.
- Task mix: 74.5% bug fix, 21.94% feature, 2.94% refactor. — [SWE-PolyBench paper](https://arxiv.org/pdf/2504.08703v1)
- Best agent (Aider-PB + Sonnet 3.5), 14.1% overall: Java 15.8%, JS 12.6%, TS 13.0%, Python 24.1%. SWE-Agent-PB reaches 16.4% on Java. — [paper, Table 3](https://arxiv.org/pdf/2504.08703v1)
- Summary across agents: Python 20–24%, Java 11–16%, TS 5–13%.
- Java is structurally the most complex: 66.06% of its tasks change both classes and functions ("mixed"), with 9.81 node modifications on average. — [paper](https://arxiv.org/pdf/2504.08703v1)
- By change type: Mixed changes 8–15%; Class-only and Single-class changes 25–40%. Once three or more files must be edited, success "start[s] falling below ten percent". — [paper](https://arxiv.org/pdf/2504.08703v1)

**SWE-Lancer (OpenAI; Feb 2025, updated July 2025)**
- Over 1,400 Upwork tasks worth $1M, ranging from $50 bug fixes to $32,000 features. Manager tasks ask the model to choose between implementation proposals. — [OpenAI](https://openai.com/index/swe-lancer/)
- Diamond set, Claude 3.5 Sonnet: 26.2% on IC SWE vs 44.9% on SWE Manager. It earned $208,050 of $500,800, and over $400k of $1M on the full set. — [SWE-Lancer paper](https://pdf.arxiv.org/pdf/2502.12115)
- Other models: o1 20.3% IC / 46.3% Manager; GPT-4o 8.6% IC / 38.7% Manager. — [devops.com summary](https://devops.com/?p=175815) (secondary source)

**Terminal-Bench 2.0 (89 tasks; paper Jan 2026, ICLR 2026)**
- At publication, frontier models and agents scored below 65%. — [Terminal-Bench paper abstract](https://arxiv.org/abs/2601.11868)
- Vals.ai (2026-06-04, Terminus 2 harness): GPT-5.5 73.20%, Claude Opus 4.8 70.04%, Opus 4.7 68.54%, Gemini 3.5 Flash 67.42%, GPT-5.3 Codex 64.05%.
- At launch no model scored above 50% on the hard tasks. Vals.ai has since archived the benchmark "because performance has saturated". — [Vals.ai](https://www.vals.ai/benchmarks/terminal-bench-2)
- Other boards give higher numbers: llm-stats lists GPT-5.5 at 82.7%, and Snorkel lists NexAU-AHE + GPT-5.5 at 84.7%. — [llm-stats](https://llm-stats.com/benchmarks/terminal-bench-2), [Snorkel](https://snorkel.ai/leaderboard/terminal-bench-2-0/). The spread reflects different harnesses.

**FEA-Bench (feature implementation; Mar 2025)**
- 1,401 tasks from 83 repos. A gold patch averages 2.62 files and 128.5 lines; 67.8% of edited lines are new components, and it adds 4.49 functions. — [FEA-Bench paper](https://arxiv.org/html/2503.06680v2)
- Best result: DeepSeek-R1 9.92% (Oracle context, detailed hint). o1 7.28%, GPT-4o 6.14%. — [same](https://arxiv.org/html/2503.06680v2)
- **Number of new functions predicts failure** (DeepSeek-R1): 18.96% with 1 added function, 8.24% with 2, 5.47% with 3 or more. — [same](https://arxiv.org/html/2503.06680v2)
- Raising the retrieved context from 27K to 40K tokens *lowered* GPT-4o from 5.28% to 4.78%. — [same](https://arxiv.org/html/2503.06680v2)

**Commit0 (library generation from scratch; Dec 2024, ICLR 2025)**
- 54 libraries; the lite split has 16.
- A frontier LLM without feedback passes 17% of unit tests on the easier libraries and 6% on the harder ones. With unit-test feedback it reaches 26% on the easier ones. No agent fully reproduced a library. — [Commit0 paper](https://arxiv.org/html/2412.01769v1)
- Claude 3.5 Sonnet (SDE-I agent): 29.30% unit-test pass rate on lite, 6.12% on the full set. — [alphaXiv](https://alphaxiv.org/benchmarks/cohere/commit0) (secondary source)
- No 2026 numbers found.

**LiveCodeBench Pro (Codeforces, ICPC and IOI problems; Jun 2025, NeurIPS 2025)**
- Best model without tools (o4-mini-high): 53% pass@1 on Medium and **0% on Hard**. Hard means problems rated above 3000, which fewer than 0.1% of human contestants solve live. — [NeurIPS abstract](https://proceedings.neurips.cc/paper_files/paper/2025/hash/9712b78386cebdc3db7f1a48c2d20edb-Abstract-Datasets_and_Benchmarks_Track.html)
- Models do well on "implementation-heavy" problems but fail at "nuanced algorithmic reasoning and complex case analysis", often giving confidently wrong justifications. — [paper](https://arxiv.org/abs/2506.11928v1)
- BenchLM (2026-09-27) lists Sakana Fugu-Ultra at 90.8% without saying which tier. **Unverified, and it conflicts with the 0%.** — [BenchLM](https://benchlm.ai/md/benchmarks/livecodebench-pro.md)

**BaxBench (secure backends; 392 tasks; Feb 2025, ICML 2025)**
- At launch, no flagship LLM exceeded 35% "correct and secure". — [BaxBench paper](https://arxiv.org/abs/2502.11844)
- A later leaderboard figure (undated): GPT-5 at 53.8% correct and secure. About half of the functionally correct programs contain an exploitable vulnerability. — [alphaXiv BaxBench](https://www.alphaxiv.org/benchmarks/eth-zurich/baxbench)

**Newer 2026 long-horizon benchmarks (arXiv preprints, not peer-reviewed)**
- **SWE-Marathon** (2026-06-05): 20 multi-hour tasks. Frontier agents solve fewer than 30%. Failures come from poor self-verification, self-reported infeasibility and stopping too early. 13.8% of rollouts show reward hacking. — [arXiv 2606.07682](https://arxiv.org/abs/2606.07682)
- **Multi-step SWE** (2026-11-04): 69 tasks, each a chain of 5–15 dependent steps on one codebase. The best of nine frontier models solves 18.5%. 38 of the 69 tasks were solved by no model. "Early design decisions and errors carry forward." — [arXiv 2611.07166](https://arxiv.org/abs/2611.07166)
- **DeepSWE** (2026-07-08): 113 tasks written from scratch, 5 languages, 91 repos. Its reference solutions touch 5.5× more code than SWE-Bench Pro's, with prompts about half as long. An LLM judge disagreed with its verifier on 1.4% of tasks, against 32.4% for SWE-Bench Pro's inherited tests. — [arXiv 2607.07946](https://arxiv.org/abs/2607.07946)
- **Doc2Harness** (long-horizon harness engineering): top model 53.05% on 328 test cases. Failures cluster where cross-cutting functionality is split across modules. — [arXiv 2610.20344](https://arxiv.org/html/2610.20344v1) (search snippet only, not fetched)

### Inferences
- On every benchmark that reports a breakdown, the steepest predictors are the same: the number of files or structural nodes edited, the patch length, and the number of new components. Bug fix vs feature matters less than size. SWE-PolyBench's Sonnet 3.5 results show feature and refactor rates close to bug-fix rates for the same agent.
- The real frontier for "can't do it yet" is now long-horizon, multi-step and private-code work, where scores sit at about 18–30%. Single-issue Python patches are no longer that frontier.

### Gaps
- No 2026 per-language numbers for Multi-SWE-bench, SWE-PolyBench or SWE-bench Multilingual from frontier models (GPT-5.x, Claude 4.6+ and newer).
- No 2025–2026 SWE-Lancer numbers from current models. The Jul 2025 update's numbers were not retrieved.
- No current LiveCodeBench Pro hard-tier figure. The official site was not reachable.
- Terminal-Bench 1.0 results and Terminal-Bench 2 per-category solve rates were not found. The paper's failure taxonomy (Appendix H) was not retrieved because fetches failed.
- Not covered: Terminal-Bench 2 per-category data, and difficulty tiers for SWE-bench Verified (Epoch's page did not contain them).

## Q2. Which task attributes predict failure

### Takeaway
In order of strength, from the evidence found:
1. Number of files or AST nodes edited, with a cliff at 3 or more files.
2. Patch length, with a cliff above about 1k tokens or about 100+ LOC.
3. Number of new functions or components (feature work).
4. Missing specification: no requirements or interface given.
5. Long-horizon dependency, where an early error carries forward.

Issue description length has no consistent effect. Extra context can hurt.

### Cited Findings
- **Files.**
  - SWE-bench Pro: stable on single-file tasks, sharp decline as file count rises, open models near 0% at 10+ files. — [SWE-Bench Pro](https://arxiv.org/pdf/2509.16941)
  - SWE-PolyBench: below 10% at 3 or more files. — [SWE-PolyBench](https://arxiv.org/pdf/2504.08703v1)
  - Multi-SWE-bench: resolve rate drops "significantly" as modified files increase. — [Multi-SWE-bench](https://arxiv.org/html/2504.02605v1)
- **Structural spread.** Mixed class-and-function changes succeed 8–15%, against 25–40% for single-class changes. Single-function changes are only about 15%, so small scope alone does not guarantee success. — [SWE-PolyBench](https://arxiv.org/pdf/2504.08703v1)
- **Patch length.** Above 1k tokens the resolve rate drops sharply, reaching 0% for Java on some methods. — [Multi-SWE-bench](https://arxiv.org/html/2504.02605v1)
- **New code (feature vs fix).**
  - FEA-Bench: 18.96% with 1 added function, 5.47% with 3 or more. Best overall about 10%. — [FEA-Bench](https://arxiv.org/html/2503.06680v2)
  - SWE-PolyBench, Aider-PB + Sonnet 3.5: bug fix 13.8%, feature 15.1%, refactor 12.9%. This shows no simple feature penalty once size is matched. — [SWE-PolyBench, Table 4](https://arxiv.org/pdf/2504.08703v1)
- **Human time estimate.** Hard (≥1 h) tasks score 0–15% in every language; Java is 0.00%. — [Multi-SWE-bench, Table 5](https://arxiv.org/html/2504.02605v1)
- **Issue description length.** "No consistent relationship" with resolve rate. Python resolves worse with longer issues, Go better. The paper's introduction contradicts this section. — [Multi-SWE-bench](https://arxiv.org/html/2504.02605v1)
- **Missing specification.** Without requirements and an interface, scores fall from about 23–26% to about 8% on SWE-bench Pro. — [SWE-Bench Pro, Table 3](https://arxiv.org/pdf/2509.16941)
- **Underspecified prompts.** Underspecified prompts are one of OpenAI's four categories of broken task on SWE-Bench Pro. — [OpenAI, 2026-07-08](https://openai.com/index/separating-signal-from-noise-coding-evaluations/)
- **More context can hurt.** Raising FEA-Bench's context from 27K to 40K tokens lowered GPT-4o's score. — [FEA-Bench](https://arxiv.org/html/2503.06680v2)
- **Failure modes on SWE-bench Pro unresolved tasks** (GPT-5 used as judge):
  - Opus 4.1: wrong solution 35.9% and syntax error 24.2% (the paper's text figures).
  - Sonnet 4: context overflow 35.6%, endless file reading 17.0%.
  - Gemini 2.5: tool errors 38.8%, syntax 30.5%, wrong solution 18.0%.
  - Qwen3-32B: tool errors 42.0%.
  — [SWE-Bench Pro §6.3](https://arxiv.org/pdf/2509.16941)
- **Localisation is necessary but not sufficient.** SWE-Agent-PB's best Java file recall is 51.6%. High retrieval is "a necessary but not sufficient condition for high pass rates". — [SWE-PolyBench](https://arxiv.org/pdf/2504.08703v1)
- **Finding vs fixing.** SWE-Lancer: Manager selection scores 44.9% against 26.2% for IC implementation (Claude 3.5 Sonnet). Picking a good proposal is easier than producing one. — [SWE-Lancer paper](https://pdf.arxiv.org/pdf/2502.12115)
- **Hard algorithmic reasoning.** LiveCodeBench Pro: models do well on implementation-heavy problems and fail at case analysis and nuanced reasoning. — [LiveCodeBench Pro](https://arxiv.org/abs/2506.11928v1)
- **Long horizon.** Early errors propagate: 18.5% best, and 38 of 69 tasks unsolved by any model. — [Multi-step SWE](https://arxiv.org/abs/2611.07166)
- **Self-verification and premature stops.** These are SWE-Marathon's named failure modes. — [SWE-Marathon](https://arxiv.org/abs/2606.07682)
- **Cross-module splits.** Failures cluster where cross-cutting functionality is split across modules. — [Doc2Harness](https://arxiv.org/html/2610.20344v1) (snippet)
- **Security.** About half of functionally correct backends are exploitable. — [alphaXiv BaxBench](https://www.alphaxiv.org/benchmarks/eth-zurich/baxbench)

### Inferences
- The common thread is not knowing which code a change must reach: multi-file, cross-module and mixed class+function changes, and passing a value through code the model has not read. Weak localisation (about 50% file recall) and context overflow or endless reading are the mechanisms. This points to a value for tools that give agents an accurate cross-file data and control-flow picture. That is my inference, not a finding in any paper.
- "Need for domain knowledge" was not measured directly by any benchmark found. The repository spread (below 10% on some repos, above 50% on others, in SWE-bench Pro) is the nearest signal.

### Gaps
- No benchmark found reports resolve rate by **hunk count**.
- None reports by **reproduction-step presence**. SWE-bench-style "missing repro" analysis was not found for 2025–2026.
- SWE-bench Pro's per-bucket figures (by LOC and by file count) exist only as plots in its Figure 3, so I have no exact per-bucket numbers.

## Q3. Java and other statically typed languages vs Python on the same benchmark family

### Takeaway
On the same harness, Java resolves at about half to two-thirds of Python's rate in 2025:
- Multi-SWE-bench: 22.7% vs 52.2% (best per language).
- SWE-PolyBench: 11–16% vs 20–24%.
- Hard Java tasks: 0%.

Java is not the worst language. TS, JS, Go and C/C++ often score lower. On the small SWE-bench Multilingual set, Java (53.5%) ranks second. The Java gap tracks task size: Java tasks touch more files and nodes. Strong typing does not show up as an advantage on its own.

### Cited Findings
- Multi-SWE-bench:
  - Best per language: Python 52.20% vs Java 22.66%.
  - "Java ranks second after Python, though with a noticeable gap."
  - Easy/medium/hard: Python 71.65/44.83/11.11%, Java 48.15/23.08/0.00%.
  — [Multi-SWE-bench](https://arxiv.org/html/2504.02605v1)
- Java leaderboard, Dec 2025: best 33% (iSWE + Claude 4.5 Sonnet), against roughly 70–80% on the Python leaderboard. IBM suggests Python agents may be "overfitting to Python" and that the Python board may be contaminated. — [IBM Research](https://research.ibm.com/blog/ibm-software-engineering-agent-tops-the-multi-swe-bench-leaderboard-for-java)
- SWE-PolyBench:
  - Java 11–16%, Python 20–24%, TS 5–13%.
  - Java's average files changed is 3.6 vs Python's 2.0, and its node modifications 9.81 vs Python's lower count.
  - Java still beats TS despite TS's lower complexity (11.39% mixed changes, 2.06 modifications).
  - The authors say results "cannot be explained by [complexity] alone" and point to the language mix in pretraining data.
  — [SWE-PolyBench](https://arxiv.org/pdf/2504.08703v1)
- SWE-bench Multilingual (Claude 3.7): Java 53.49%, second of 7, against a 42.67% overall. C/C++ 28.57% and Go 30.95% are lowest. — [swebench.com/multilingual](https://www.swebench.com/multilingual.html)
- SWE-bench Pro has no Java. Among its languages, Go and Python are higher and JS/TS vary from near 0% to over 30%. The paper lists missing Java and C++ as a limitation. — [SWE-Bench Pro](https://arxiv.org/pdf/2509.16941)
- SWE-bench-java Verified (2024): 6.59% with GPT-4o. — [SWE-bench-java](https://arxiv.org/pdf/2408.14354)

### Inferences
- Java's gap on the same benchmark family looks like **task-shape** (more files, more class-level edits, longer patches), plus a possible Python-specific bias from training and contamination. Static typing does not show up as a help, and none of these benchmarks gives agents type-aware or compiler-backed navigation by default.
- The Multilingual Java set is small (43 tasks, 17 of them Lombok) and mostly single-issue, so its 53.5% is weak evidence against the larger Multi-SWE-bench gap.

### Gaps
- No 2026 frontier-model Java vs Python comparison on the same harness was found.
- No study separates "static typing" from "task size" as causes.

## Q4. Saturation and contamination, and what the harder benchmarks show instead

### Takeaway
SWE-bench Verified is treated as saturated and contaminated:
- OpenAI stopped reporting it on 2026-02-23. Epoch rated it "Flawed" on 2026-09-03.
- SWE-bench+ found solution leakage in about 33% of resolved instances and weak tests behind about 31% of them (2024).

But SWE-bench Pro, the suggested replacement, has its own problems: about 30% of tasks broken (OpenAI, 2026-07-08), plus reported leakage of the fix through git history. On uncontaminated or private sets, scores fall sharply: Pro commercial, DeepSWE, Multi-step SWE and SWE-Marathon all sit in roughly the 18–50% range.

### Cited Findings
- **SWE-Bench Illusion (Jun 2025).** o3 identifies the files to change on SWE-bench Verified with up to 76% accuracy without seeing the repository, against below 53% on outside repositories. The authors read this as instance-specific and repository-bias memorisation. — [arXiv 2506.12286](https://arxiv.org/pdf/2506.12286)
- A 2026 audit argues that the gap has no uncertainty interval and may be confounded by issue quality. Verified's issues were filtered for being well specified. — [arXiv 2610.05830](https://arxiv.org/html/2610.05830)
- **OpenAI, 2026-02-23.** OpenAI audited 138 Verified problems that o3 did not consistently solve. 59.4% had material flaws: narrow tests 35.5%, tests beyond the issue ("wide") 18.8%, other 5.1%. — [OpenAI](https://openai.com/index/why-we-no-longer-evaluate-swe-bench-verified/)
- In the same post, GPT-5.2, Claude Opus 4.5 and Gemini 3 Flash each reproduced gold patches or verbatim task details for some tasks (for example django-11451, astropy-13236 and django-11099). "All frontier models tested" did so. — [OpenAI](https://openai.com/index/why-we-no-longer-evaluate-swe-bench-verified/)
- **Epoch.** At least 16.4% of Verified tasks are broken, and Epoch has "reasonable confidence that over 20%" have scoring issues. — [Epoch](https://epoch.ai/benchmarks/swe-bench-verified/review)
- **SWE-bench+ (Oct 2024).** The solution sat in the issue or its comments for 32.67% of resolved instances. 31.08% of "resolved" patches were wrong or incomplete but passed weak tests. Filtering both out cut SWE-Agent + GPT-4 from 12.47% to 3.97%. — [SWE-Bench+](https://arxiv.org/html/2410.06992v2)
- **SWE-Bench Pro problems.**
  - OpenAI on 2026-07-08 estimates "~30%" of tasks are broken: 27.4% flagged automatically, 34.1% by human annotation. The categories are overly strict tests, underspecified prompts, low-coverage tests (9.4% per humans) and misleading prompts. OpenAI "retract[s] our earlier recommendation to adopt SWE-Bench Pro". — [OpenAI](https://openai.com/index/separating-signal-from-noise-coding-evaluations/)
  - Scale's V2 dropped 89 of 731 tasks as invalid. — [Scale Labs](https://labs.scale.com/leaderboard/swe_bench_pro_public_v2?tab=hard)
  - A mini-swe-agent issue reports agents reading the gold fix from git history: a 21% exploitation rate on 100 tasks with Opus 4.6. A Datacurve audit flagged more than 12% of reviewed tasks. Neither is confirmed by Scale. — [Morph](https://www.morphllm.com/swe-bench-pro)
- **DeepSWE.** On tasks written from scratch, its verifier disagrees with an LLM judge 1.4% of the time, against 32.4% for SWE-Bench Pro's inherited tests. — [DeepSWE](https://arxiv.org/abs/2607.07946)
- **Harder or less contaminated benchmarks:**
  - Pro commercial (private code): best about 51.5% standardised, and below 20% at launch. — [Morph](https://www.morphllm.com/swe-bench-pro), [paper](https://arxiv.org/pdf/2509.16941)
  - Multi-step SWE: 18.5%. — [arXiv](https://arxiv.org/abs/2611.07166)
  - SWE-Marathon: below 30%. — [arXiv](https://arxiv.org/abs/2606.07682)
  - LiveCodeBench Pro Hard: 0% in 2025. — [NeurIPS](https://proceedings.neurips.cc/paper_files/paper/2025/hash/9712b78386cebdc3db7f1a48c2d20edb-Abstract-Datasets_and_Benchmarks_Track.html)
- **Terminal-Bench 2.** Vals.ai has archived it as "saturated" (top score 73.2%). — [Vals.ai](https://www.vals.ai/benchmarks/terminal-bench-2)

### Inferences
- The scores that can be trusted most come from benchmarks with private code, tasks written from scratch, or human-verified tests. They put frontier agents at roughly 20–50% on realistic multi-file or long-horizon work. Public Python leaderboards at 80–90% overstate the ability to solve code the model has never seen.
- Because about 30% of tasks are broken even on "hard" benchmarks, leaderboard differences of a few points are noise.

### Gaps
- I found no independent reproduction of OpenAI's audit numbers on Verified or Pro.
- Scale has not confirmed the git-history leakage rate.
