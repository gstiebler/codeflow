# Static analysis, call graphs, code graphs and dataflow tools for LLM coding agents: evidence 2024–2026

Legend: **[ABL]** = an ablation isolates the graph/tool (same model, same harness, tool on/off). **[SYS]** = whole-system number vs other systems (graph confounded with everything else). Lite = SWE-bench Lite (300), Verified = SWE-bench Verified (500). All figures are self-reported by the authors unless noted.

## Q1. Which kind of structure helps which stage (localization, edit, validation)? Is the gain mostly in localization?

### Takeaway
Most of the measured gains are in **localization**, and they are small: about +2 pp end-to-end when a graph is added to a 2024 agent. The one clear win outside localization is **validation/regression**, where a code→test impact graph cut regressions by 70% (small 2026 study). For **editing**, structure does little or harms: grep beat LSP on edits and renames, and RepoGraph's 2-hop context hurt.

### Cited Findings

#### Master table

| Approach (year, venue) | Structure exposed | Stage | Benchmark | Measured result vs baseline | Isolated? | Cost |
|---|---|---|---|---|---|---|
| RepoGraph (ICLR 2025) | line-level def/ref graph, k-hop ego-subgraph per searched symbol | localization context | Lite | RAG 2.67→5.33 (GPT-4); Agentless 27.33→29.67 (GPT-4o); AutoCodeRover 19.00→21.33 (GPT-4); SWE-agent 18.33→20.33 (GPT-4o); "average relative improvement of 32.8%", but most of that comes from RAG's +99.6% | **[ABL]** plug-in on/off, same model | cost +$0.04 to +$0.16/instance (e.g. SWE-agent $2.53→$2.69) — [paper](https://arxiv.org/html/2410.14684v2) |
| RepoGraph subgraph ablation | 1-hop vs 2-hop, flattened vs summarized | localization | Lite / Agentless | 1-hop flat 29.67; 1-hop summarized 28.33; 2-hop summarized 28.67; **2-hop flat 26.00, below the no-graph 27.33** | **[ABL]** | 2-hop flat = 10.5k tokens vs 2.3k — [paper](https://arxiv.org/html/2410.14684v2) |
| RepoGraph on completion | same graph | completion | CrossCodeEval Py | GPT-4o EM 10.5→28.7; DeepSeek-Coder 10.2→19.7 | [ABL] | — [paper](https://arxiv.org/html/2410.14684v2) |
| CodexGraph (2024, NAACL 2025) | code graph DB (Neo4j) queried by LLM-written Cypher | localization / navigation | CrossCodeEval, SWE-bench Lite, EvoCodeBench | Not verified this session (search returned only references) | — | — |
| LocAgent (ACL 2025) | heterogeneous graph (dirs/files/classes/functions; contain/import/invoke/inherit) + search/traverse tools | localization | SWE-bench Lite, Loc-Bench | fine-tuned Qwen2.5-Coder-32B "up to 92.7% accuracy on file-level localization"; downstream resolution +12% Pass@10 | [SYS] (graph tools vs other localizers) | "~86%" cost reduction vs proprietary models — [arXiv](https://arxiv.org/abs/2503.09089) |
| OrcaLoca (ICML 2025) | priority-scheduled actions over a code graph, distance-aware context pruning | localization | Lite | function match 65.33% (open-source SOTA); +6.33 pp resolve rate when plugged into an open-source framework | [SYS]/partial | — [arXiv](https://arxiv.org/abs/2502.00350) |
| CoSIL (ASE 2025) | module call graph (file level), then expanded function call graph searched by the LLM; pruner | localization | Lite, Verified | Top-1 43.3% (Lite), 44.6% (Verified) with Qwen2.5-Coder-32B; "+96.04%" vs SOTA localizers on average; plugged into Agentless, resolution +2.98% to +30.5% | [ABL]-ish (Agentless with vs without CoSIL localization) | no training or indexing — [arXiv](https://arxiv.org/abs/2503.22424) |
| KGCompass (2025) | KG linking issues/PRs → files/classes/functions; multi-hop entity paths fed to repair | localization + repair context | Lite | 58.3% resolved (best open-source single repair model; latest version uses Claude-4 Sonnet); 56.0% function-level localization; gain vs "pure-LLM" baseline: Claude-4 Sonnet +50.8%, Claude-3.5 Sonnet +30.2%, DeepSeek-V3 +115.7%, Qwen2.5-Max +156.4% (relative); 89.7% of localized bugs have no location hint in the issue | [ABL] vs pure-LLM baseline per model (KG-only ablation not in abstract) | ~$0.20/repair — [arXiv](https://arxiv.org/abs/2503.21710) |
| GraphLocator (Dec 2025) | causal issue graph over the call graph | localization | 3 datasets incl. Multi-SWE-bench **Java** (128 issues, 9 repos) | function-level recall +19.49%, precision +11.89% on average vs baselines; downstream resolution +28.74% relative; on Java precision +4.84% to +29.84% over Agentless and CoSIL | [SYS] | — [arXiv](https://arxiv.org/abs/2512.22469), Java detail via [search snippet of PDF](https://arxiv.org/pdf/2512.22469) |
| CGM (NeurIPS 2025) | repository graph injected into attention mask (LoRA fine-tune) + graph-RAG (rewriter/retriever/reranker) | localization + generation | Lite | 43.00% with Qwen2.5-72B (1st among open-weight, May 2025); a later version reports 47.00% (CGM-SWE-PY) | [SYS] | — [arXiv](https://arxiv.org/abs/2505.16901), [NeurIPS](https://proceedings.neurips.cc/paper_files/paper/2025/hash/178ae4ba29022eb7bf509c2e27bc8ab8-Abstract-Conference.html) |
| AutoCodeRover (ISSTA 2024) | AST-level search APIs (search_class / search_method_in_class …) + SBFL when tests exist | localization | Lite | 19% (pass@1), 26% @3 (2024) | [SYS] | $0.43/task — [arXiv](https://arxiv.org/pdf/2404.05427) |
| SpecRover (ICSE 2025) | AutoCodeRover's AST search + spec/intent extraction + reviewer agent | localization + validation | Lite / full | 31% Lite, 19.3% full SWE-bench; ">50% improvement in efficacy over AutoCodeRover" | [SYS] (gain mostly from spec/review, not AST) | $0.65/issue — [arXiv](https://arxiv.org/abs/2408.02232v4) |
| Agentless (FSE 2025) | hierarchical localization: repo tree → files → classes/functions skeleton → edit locations; no agent loop | localization → repair → test-based selection | Lite | 32.00% (96 fixes) (2024) | [SYS] | $0.70 — [arXiv](https://arxiv.org/abs/2407.01489) |
| Moatless Tools (2024–25) | semantic code-block index + class/function search tools | localization + edit | Lite | 26.7% (v0.0.2, $0.17) → 38.3–39% with Claude 3.5 Sonnet (v0.0.3, $0.30; "2.7 resolved instances per dollar"); a related repo reports Claude 4 Sonnet 70.8% at $0.63 (configuration unconfirmed) | [SYS] | — [GitHub](https://github.com/aorwall/moatless-tools), [PyPI](https://pypi.org/project/moatless/) |
| SWE-agent ACI (NeurIPS 2024) | custom search/find_file/viewer/linted edit (no graph) | all | Lite, GPT-4 Turbo | default 18.0; no edit 10.3; edit without linting 15.0; iterative search 12.0; **no search 15.7**; full-file viewer 12.7; 30-line viewer 14.3; shell-only 11.0 | **[ABL]** | successful runs median $1.21 / 12 steps; failed avg $2.52 / 21 steps — [paper](https://arxiv.org/html/2405.15793) |
| Aider repo-map (2024) | AST + call-graph map, "graph optimization on the code's call graph", tailored to the chat | file selection | Lite | 26.3% (GPT-4o + Opus alternating); right file identified in 70.3% of tasks | **No ablation** of the map | — [Aider blog](https://aider.chat/2024/05/22/swe-bench-lite.html) |
| SWE-Master LSP (Feb 2026) | pyright LSP (navigation, dependency analysis, workspace search; no get_declaration/implementation) | navigation | Verified | MiniMax-M2.1 68.4→70.4% (turns 82.0→77.0); GLM-4.7 66.2→67.6% (97.3→94.4); SWE-Master RL→+LSP SFT 61.4→61.0% (turns 111.2→91.7; input tokens −23.7%) | **[ABL]** for base models; the SFT row is confounded with training | — [arXiv](https://arxiv.org/html/2602.03411) |
| LSP token study (2026, preliminary) | LSP vs grep, four arms run (repo-map arm not run) | localization, references, edits | 6 Lite issues + small TS/Py repos | localization: all arms 100% success; LSP **+6% tokens (Opus 4.8), +118% (Sonnet 4.6), −26% (Haiku 4.5)**; reference F1 0.706→0.778 (precision 1.00 vs 0.76, recall flat); single-file edit pass@1 grep 0.83 vs both-tools 0.58; renames grep 1.00 vs LSP 0.67 | **[ABL]** (tiny n) | — [arXiv](https://arxiv.org/html/2608.13568) |
| Small-model code-graph study (Oct 2026) | find_entity + graph_neighbors (T), semantic_search (E), vs grep/read/list (G) | localization (repair fixed) | Verified, depth-stratified 160 + all 500 | full 500, Acc@10: Qwen3-8B G 16.7 → ET 33.2; Gemma4-E4B 16.7 → 29.7; Qwen3-4B 26.8 → T 37.1; **Qwen3-32B 32.0 → 35.9**; repair with fixed Qwen3-8B resolves 6/13/12/13 of 160 (G/E/T/ET), oracle localization 26 | **[ABL]** | — [arXiv](https://arxiv.org/html/2610.33958v1) |
| CodeAnchor / deterministic anchoring (2026) | call/inheritance topology injected as plain-text "tags" (also CALLED_BY, DATA_DEP, CONFIG_USAGE, TEST_REF edges) | localization / navigation | SWE-bench (Codex agent) | Func@5 **+2.2 pp**; −1.6 interaction rounds; Pass@1 +3.4 pp on medium repos; run-to-run variance roughly halved; link-following 0.15–0.18 → 0.21–0.24 | **[ABL]** | ~+10% input tokens — [arXiv](https://arxiv.org/abs/2606.26979) |
| FlowPlan (Nov 2026) | per-function control-flow / data-flow graphs for planning and verification | planning / cost | SWE-bench Pro, Verified, SecBench | **no significant change in resolve rate** in any of 6 settings; tokens 0.32–0.47× of baseline for Haiku 4.5, 0.72–0.76× for Sonnet 5 (0.92–0.94× on SecBench) | **[ABL]** | the gain is cost, not accuracy — [arXiv](https://arxiv.org/abs/2611.13241) |
| TDAD (Mar 2026) | code↔test dependency graph; impact analysis selects tests within 1–3 call hops; shipped as a static text "skill" | **validation / regression** | Verified (100 inst. Qwen3-Coder-30B; 25 inst. Qwen3.5-35B-A3B) | regressions **6.08% → 1.82% (−70%)**; TDD instructions alone raised them to **9.94%**; as a skill on another model/framework, resolution 24% → 32% | **[ABL]** (graph vs prompt-only vs vanilla) | small n — [arXiv](https://arxiv.org/abs/2603.17973) |
| iSWE (Mar 2026) | 7 read-only Java tools on CLDK + Tree-sitter (file/class/method/symbol info, inheritance hierarchy, callers, call chain) + linter + compile check | localization + edit validation | Multi-SWE-bench **Java** (128), SWE-PolyBench Java (165) | MSB-Java 33.6% (Claude-4.5-Sonnet), 25.0% (Claude-3.7-Sonnet) vs MSWE-agent 23.4%, MopenHands 21.8% (same 3.7 model); InfCode (GPT-5.2) 39.1%; PolyBench-Java 33.3% (Opus 4.5) | [SYS]; tool-set ablation (below) shows **localization equal to bash** | $1.21 vs $2.59–$3.72 same model (2–3× cheaper) — [arXiv](https://arxiv.org/html/2603.11356) |
| iSWE tool-set ablation | iSWE tools vs bash vs all tools | localization | MSB-Java, Claude-4.5-Sonnet | file recall/precision 63/83 (iSWE) vs 62/82 (bash) vs 62/81 (all); node 36/59 vs 37/57 vs 37/58 | **[ABL]**: **no localization gain**; cost $1.77 vs $2.06 bash vs $2.63 all | — [arXiv](https://arxiv.org/html/2603.11356) |
| DraCo (ACL 2024) | dataflow-guided retrieval (type-sensitive data-flow graph) | completion | ReccEval (Py) | EM +3.43%, identifier F1 +3.27% vs SOTA retrieval | [ABL] vs other retrievers | — [arXiv](https://arxiv.org/abs/2405.19782) |
| GraphCoder (ASE 2024) | code context graph (control-flow + data/control dependence), coarse-to-fine retrieval | completion | 8k tasks, 20 repos, 5 LLMs | code EM +6.06, identifier EM +6.23 vs RAG | [ABL] vs RAG | less retrieval time and storage — [arXiv](https://arxiv.org/html/2406.07003v2) |
| RepoHyper (2024) | repo-level semantic graph, search-expand-refine | completion | RepoBench | beats baselines (exact numbers not extracted) | [ABL] vs retrievers | — [arXiv](https://arxiv.org/html/2403.06095v4) |
| IRIS (ICLR 2025) | LLM-inferred taint specs + CodeQL dataflow | vuln detection (not an agent loop) | CWE-Bench-**Java** (120 CVEs) | 55 detected (GPT-4) vs CodeQL 27 (v2; v1 said 69); 6 previously unknown vulnerabilities | [ABL] vs CodeQL alone | dataset later grew to 213 CVEs / 49 CWEs — [arXiv](https://arxiv.org/abs/2405.17238v3), [GitHub](https://github.com/iris-sast/iris) |
| LLMDFA (NeurIPS 2024) | LLM-driven dataflow: source/sink extraction, path summaries, synthesized checking scripts | bug detection | synthetic programs, 3 bug types | avg precision 87.10%, recall 80.77%; F1 up to +0.35 vs existing techniques | [SYS] | — [arXiv](https://arxiv.org/abs/2402.10754) |
| Defects4J agents (2024–26) | RepairAgent search tools; FixAgent slicer/locator agents; ReinFix static analysis for repair ingredients | localization + ingredients | Defects4J (**Java**) | RepairAgent 164 fixes, **halves without its search tools**; FixAgent 197 correct / 286 plausible; ReinFix 146 on D4J 1.2 (+32 over baselines) | RepairAgent = [ABL] (search tools as a whole, not static analysis specifically) | — [RepairAgent](https://arxiv.org/pdf/2403.17134), [FixAgent](https://arxiv.org/html/2404.17153v2), [search summary](https://arxiv.org/html/2506.23100v1) |

#### Stage-specific findings
- Localization: correct localization almost fully decides repair. With a fixed Qwen3-8B repairer, 24–27% of correctly localized tasks are resolved vs 1–2% of the rest — [arXiv 2610.33958](https://arxiv.org/html/2610.33958v1).
- Localization is the main failure in Java: in Multi-SWE-bench "a significant number of failures across all three agent methods were due to incorrect fault localization" — [Multi-SWE-bench](https://arxiv.org/html/2504.02605v1).
- Editing: grep beat LSP. Single-file edit pass@1 was 0.83 for grep vs 0.58 for both tools; on renames a location-only LSP missed call sites (missed-site 0.33 vs 0.00), because renames also touch comments and strings — [arXiv 2608.13568](https://arxiv.org/html/2608.13568).
- Editing ACI: the linter on edits is worth +3.0 pp (18.0 vs 15.0) and the edit tool itself +7.7 pp — [SWE-agent](https://arxiv.org/html/2405.15793). These are interface effects, not graph effects.
- Validation: a compile check plus linter in the iSWE edit agent moved node recall/precision from 36/59 (after localization) to 44/68 (after editing) — [iSWE](https://arxiv.org/html/2603.11356).
- Too much structure hurts: RepoGraph's 2-hop flattened context (26.00) scored below the no-graph Agentless baseline (27.33) — [RepoGraph](https://arxiv.org/html/2410.14684v2).
- In CodeAnchor's guidance, inverse-only ("who calls me") edges suit hub-heavy projects, forward edges should be pruned in large repos, and dense tags are for implicit dependencies — [arXiv 2606.26979](https://arxiv.org/abs/2606.26979).

### Inferences
- Measured gains cluster in localization, typically +2 to +6 pp end-to-end on 2024-era models. The large relative numbers (+96%, +156%, +32.8%) come from weak baselines (RAG at 2.67%, Qwen2.5-Max) or from localization-only metrics.
- Callers, call chains and data dependence look most useful for "what else touches this" and for picking tests (TDAD), not for writing the patch.

### Gaps
- No exact numbers verified this session for CodexGraph, OrcaLoca's absolute resolve rate, SpecRover's ablation, RepoHyper's tables, or CodeRAG (bigraph). Searches did not surface CodeRAG.
- No sourced ablation of Aider's repo map. Agentless's ablation of its skeleton step was not fetched.
- The SWE-agent paper does not measure graph tools.

## Q2. Does the gain hold for the strongest 2025–2026 models, or shrink as models improve?

### Takeaway
It shrinks. Every ablation that varies model strength shows a smaller benefit, lower tool uptake or higher token cost for stronger models. Frontier agents reach about 74% on Verified with bash alone. The exceptions: KGCompass still reports +50.8% relative for Claude-4 Sonnet, and LSP keeps a precision edge on noisy reference-finding.

### Cited Findings
- Scale curve: the structured tools' gain over grep is 7–9 pts at 0.6B, rises to 17–21 pts at 1.7–4B, then falls to 4–5 pts at 32B (odds ratio 0.77–0.82 per doubling of parameters). From 8B up, about half the gain comes from the *form* of the submission (exact entity IDs), not from finding the target. find_entity use drops from 96% of episodes at 14B to 45% at 32B — [arXiv 2610.33958](https://arxiv.org/html/2610.33958v1).
- LSP for localization: +6% tokens for Opus 4.8 and +118% for Sonnet 4.6; it saved tokens only for Haiku 4.5 (−26%), where it "acts as a crutch against grep noise". Reference-finding F1 gain is +0.072 for Opus and +0.083 for Sonnet but only +0.013 for Haiku. The authors argue for routing by task class, model strength and lexical noise, not "LSP-always" — [arXiv 2608.13568](https://arxiv.org/html/2608.13568).
- FlowPlan dataflow-graph planning: no resolve-rate change for either model; token savings are larger for the weaker model (Haiku 4.5 0.32–0.47×) than the stronger one (Sonnet 5 0.72–0.76×) — [arXiv 2611.13241](https://arxiv.org/abs/2611.13241).
- LSP on 2026 open frontier-ish models: +2.0 pp (MiniMax-M2.1) and +1.4 pp (GLM-4.7) on Verified — [SWE-Master](https://arxiv.org/html/2602.03411).
- KGCompass relative gains: +50.8% for Claude-4 Sonnet, +30.2% for Claude-3.5 Sonnet, +115.7% for DeepSeek-V3, +156.4% for Qwen2.5-Max. The gain is larger on weaker models but persists on a 2025 frontier model — [arXiv 2503.21710](https://arxiv.org/abs/2503.21710).
- iSWE with Claude-4.5-Sonnet: Java static tools give the same localization as bash (file 63/83 vs 62/82) at lower cost ($1.77 vs $2.06) — [iSWE](https://arxiv.org/html/2603.11356).
- Bash-only baseline: mini-SWE-agent (~100 lines of Python, bash only, no tool-calling interface) scores >74% on Verified; Gemini 3 Pro reaches 74% with it. A "bash only" leaderboard tracks this — [GitHub](https://github.com/SWE-agent/mini-swe-agent).
- Tool surface changes cost, not accuracy: across Claude Code (Sonnet 4.6) and Codex CLI (GPT-5.5), pass-rate differences between baseline, bash_only and code_only were all <3 pp and non-significant. Bash-only was the most expensive arm on SWE-bench/Codex (+4.8%) — [arXiv 2607.10569](https://huggingface.co/papers/2607.10569.md).
- Related work cited in 2610.33958: CABRA (2026) finds agents stay near-perfect on growing call-graph tasks "because they offload to tools such as grep"; SWE-Explore (2026) finds sparse and static-embedding retrievers stay close to random while agentic explorers score much higher — [arXiv 2610.33958](https://arxiv.org/html/2610.33958v1). Secondary citation; the original papers were not read.
- Benchmark ceiling: OpenAI has argued Verified no longer measures frontier capability (reported via search summary) — [swebench.com](https://www.swebench.com/). Not verified at the primary source.

### Inferences
- For frontier models, graph and LSP tools are better framed as **cost, variance and precision** tools than as accuracy tools. The 2026 ablations (CodeAnchor variance halved, FlowPlan tokens, iSWE cost) all point this way.
- Verified tasks are mostly 1–2 hops (CodeAnchor, cited in 2610.33958). This may hide the regime where graphs should matter (≥2 hops), where even graph-equipped small models fail.

### Gaps
- No ablation found of a code graph or call graph with a frontier model (Opus 4.x / GPT-5.x / Gemini 3) on full Verified or SWE-bench Pro.
- The "Thousand-Graph Hypothesis" (95.6% Verified with an entity-only index) is known only through a secondary citation and is unverified.

## Q3. Any results on Java / statically typed codebases specifically?

### Takeaway
There is little Java evidence, and none of it shows a tool-isolated accuracy gain for strong models. iSWE gives Java static-analysis tools to Claude and matches bash on localization at lower cost. GraphLocator reports localization gains on Multi-SWE-bench Java against weaker localizers. IRIS shows CodeQL dataflow with LLM-inferred specs doubling detections on Java CVEs.

### Cited Findings
- Multi-SWE-bench Java baselines: MSWE-agent 17.97% on bug fixes, but 3.91% on new features and 1.56% on optimizations; failures are dominated by wrong localization — [Multi-SWE-bench](https://arxiv.org/html/2504.02605v1).
- iSWE tools: CLDK + Tree-sitter (get_method_info, get_function_callers, get_call_chain, get_inheritance_hierarchy …). Results: 33.6% on MSB-Java (Claude-4.5-Sonnet, $1.86, 598k tokens) vs InfCode 39.1% (GPT-5.2, $4.78, 2.45M tokens). Ablation: no localization gain over bash, about 14% cheaper — [iSWE](https://arxiv.org/html/2603.11356).
- iSWE tool calls on MSB-Java (Claude-4.5-Sonnet, total 849): get_method_info 403, get_symbol_info 181, get_class_info 124, get_file_info 87, get_call_chain 33, get_function_callers 19, **get_inheritance_hierarchy 2**. The agent took 31.5 localization turns vs 1.7 editing turns — [iSWE](https://arxiv.org/html/2603.11356).
- GraphLocator on MSB-Java (128 issues): precision +4.84% to +29.84% over Agentless (MSB version) and an adapted CoSIL — [arXiv 2512.22469](https://arxiv.org/pdf/2512.22469) (via search snippet).
- IRIS on CWE-Bench-Java: 55 vs 27 (CodeQL) — [arXiv](https://arxiv.org/abs/2405.17238v3).
- Defects4J: RepairAgent halves without its search tools; FixAgent's Slicer agent uses static/dynamic analysis, 197 fixes; ReinFix uses static analysis for repair ingredients, 146 fixes on D4J 1.2 — [RepairAgent](https://arxiv.org/pdf/2403.17134), [FixAgent](https://arxiv.org/html/2404.17153v2), [ReinFix](https://arxiv.org/html/2506.23100v1).
- Static vs runtime: TraceRepair (2026 preprint) argues static-only agents miss runtime behavior and claims 392 Defects4J fixes (unverified) — via [search](https://arxiv.org/html/2604.02647).
- Typed vs untyped noise: in the LSP study, LSP benefit tracks grep's false-positive rate, not the language. Clean TypeScript (remeda) showed no gain (0.774→0.774); noisy TypeScript (hono) showed a large one (0.451→0.697, −12% tokens) — [arXiv 2608.13568](https://arxiv.org/html/2608.13568).

### Inferences
- In statically typed code, grep is noisiest on overloaded names, interface dispatch and common method names, where LSP or call-graph tools gain precision. But agents almost never asked for the inheritance hierarchy (2 of 849 calls in iSWE), the very structure that dispatch needs.

### Gaps
- No Java study isolates a dataflow (value-flow) tool, as opposed to call graph or symbol lookup, for an issue-resolving agent.
- The 2024 SWE-bench-java numbers were not fetched.

## Q4. Evidence on tools that answer "what else does this change affect" or "which analogous code paths differ"?

### Takeaway
Impact analysis has one controlled positive result: TDAD, a code→test graph that cut regressions by 70% (small n, small models). Telling the agent to do TDD without the graph made regressions worse. No agent study was found for "which analogous code paths differ" (sibling/divergence comparison).

### Cited Findings
- TDAD: code↔test dependency graph, tests within 1–3 call hops weighted 0.70, delivered as a static text skill. Regressions 6.08% → 1.82%; TDD prompting alone 9.94%; resolution 24% → 32% in transfer — [arXiv 2603.17973](https://arxiv.org/abs/2603.17973), [alphaXiv](https://www.alphaxiv.org/abs/2603.17973).
- Callers lookups for reference completeness: LSP precision 1.00 vs grep 0.76, recall unchanged (0.66 vs 0.67), so neither finds all affected sites — [arXiv 2608.13568](https://arxiv.org/html/2608.13568).
- CodeAnchor includes CALLED_BY, DATA_DEP, CONFIG_USAGE, ENV_VAR and TEST_REF edge types and recommends inverse-only "who-calls-me" links for hub-heavy projects — [arXiv 2606.26979](https://arxiv.org/abs/2606.26979).
- ORACLE-SWE (2026) measures oracle vs agent-extracted signals: reproduction test, **regression test**, edit location, execution context, API usage. Per-signal numbers were not extracted (PDF unreadable) — [arXiv 2604.07789](https://arxiv.org/abs/2604.07789).
- Vendor claim (unverified): FalkorDB's code graph grew into "Auryn", an MCP code graph for checking upstream callers before a change (Nov 2026) — [FalkorDB blog](https://www.falkordb.com/blog/code-graph/).
- KGCompass: 89.7% of localized bugs are reachable only by multi-hop graph traversal, with no location hint in the issue — [arXiv 2503.21710](https://arxiv.org/abs/2503.21710).

### Inferences
- The TDAD ablation (graph helps, the instruction alone hurts) suggests agents benefit from being *handed* the affected set, not from being told to look for it.

### Gaps
- No study found for "sibling / analogous path divergence" tools for agents.
- No frontier-model impact-analysis ablation found. TDAD used Qwen3-Coder-30B and Qwen3.5-35B-A3B only.
- ORACLE-SWE per-signal contributions were not read.

## Q5. How do agents actually use such tools when given them (call frequency, ignored tools, token cost)?

### Takeaway
Strong agents mostly ignore graph and LSP tools for localization and fall back on grep and file reads. They use the tools for exact symbol lookup and one-hop expansion, almost never multi-hop. Structure adds roughly 10–20% tokens unless it replaces long exploration.

### Cited Findings
- Free choice between grep and LSP for localization: 0% LSP calls for Opus 4.8 (50 grep / 0 semantic), 4% for Sonnet, 6% for Haiku. On reference tasks, LSP was used in about 45–57% of episodes. Forcing semantic-first lowered localization success to 89% — [arXiv 2608.13568](https://arxiv.org/html/2608.13568).
- Graph tool uptake (Qwen3-32B): find_entity in 39% of T episodes, graph_neighbors in 31%, semantic_search in 34% of E episodes. **0 of 263** graph_neighbors calls asked for 2 hops. Chained expansions occur in 12–14% of episodes. Qwen3-4B used semantic_search in only 43% of E episodes and 14% of ET episodes. Grep-only Qwen3-8B averaged about 10 greps, hit the step cap in 40% of episodes and never submitted in 41% — [arXiv 2610.33958](https://arxiv.org/html/2610.33958v1).
- iSWE (Claude-4.5-Sonnet, Java): get_method_info is 47% of calls; callers and call chain together are 6%; inheritance 0.2% — [iSWE](https://arxiv.org/html/2603.11356).
- CodeAnchor: link-following rate rises only from 0.15–0.18 to 0.21–0.24 even when edges are injected inline; cost about +10% input tokens — [arXiv 2606.26979](https://arxiv.org/abs/2606.26979).
- Token and dollar costs:
  - RepoGraph: +$0.04–0.16 per instance; SWE-agent 498k → 519k tokens.
  - SWE-Master + LSP: input tokens −23.7% (confounded with SFT).
  - LSP on references: +19% tokens (Opus).
  - [RepoGraph](https://arxiv.org/html/2410.14684v2), [SWE-Master](https://arxiv.org/html/2602.03411), [arXiv 2608.13568](https://arxiv.org/html/2608.13568).
- SWE-agent trajectories open with reproduction (`create`) or localization (`find_file` / `search_dir`); from turn ~5, `edit` and `python` dominate; 51.7% of trajectories had ≥1 failed edit — [SWE-agent](https://arxiv.org/html/2405.15793).
- Output form: with graph tools, 85% of top-5 submissions are exact entity IDs vs 47% with grep only. Part of the measured "localization gain" is formatting — [arXiv 2610.33958](https://arxiv.org/html/2610.33958v1).

### Inferences
- Exposing structure as **pushed context** (tags, impact lists, test sets) may reach strong agents more reliably than **pulled tools** they rarely call. TDAD's static text file and CodeAnchor's tags are both push designs; the two have different effect sizes.

### Gaps
- No published call-frequency data for Serena-style LSP MCP servers or for Claude Code's built-in LSP tool; no rigorous Serena benchmark found.
- No frontier-model call frequencies for code-graph MCP tools on Verified or Pro.
