import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { Graph, MethodFact, NodeFact } from '../src/types.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { allPairs } from '../src/slice.ts';

/**
 * Callbacks, on a hand-written Graph shaped like what `extract-callbacks.sh` dumped: a lambda is a
 * method of its own whose METHOD_REF has no useful type, `repo::save` is a METHOD_REF typed by the
 * repository, and a callee in the corpus carries the lambda in a parameter typed by the interface.
 * One endpoint per scenario, each trying to reach `ItemRepository.save` only through a callback.
 */

const F = 'app/api/Api.java';
const SAVE = 'app.repo.ItemRepository.save:<unresolvedSignature>(1)';
const CALL_FN = 'app.svc.Callback.call:<unresolvedSignature>(1)';
const RUN = 'app.svc.Runner.run:<unresolvedSignature>(2)';
const lam = (i: number, arity: number) => `app.api.Api.<lambda>${i}:<unresolvedSignature>(${arity})`;

const n = (id: string, label: string, code: string, line: number, method: string, rest: Partial<NodeFact> = {}): NodeFact => ({
  id, label, code, file: F, line, method, ...rest,
});
const method = (fullName: string, params: [string, string, number, string][], returns: string[] = []): MethodFact => ({
  fullName, name: fullName.replace(/^.*\.(<?\w+>?\d*):.*$/, '$1'), typeName: '', typeFullName: fullName.replace(/\.[^.]*:.*$/, ''),
  file: F, line: 1, annotations: [],
  params: params.map(([id, name, index, type]) => ({ id, name, index, line: 1, type })),
  returns,
});
const endpoint = (name: string, id: string, extra: MethodFact['annotations'] = []): MethodFact => ({
  ...method(`app.api.Api.${name}:<unresolvedSignature>(1)`, [[`${id}-this`, 'this', 0, 'app.api.Api'], [`${id}-p`, 'param', 1, 'String']]),
  annotations: [{ name: 'POST' }, { name: 'Path', value: name }, ...extra],
});

/** `repo.save(<arg>)` inside a lambda body: this.repo is the receiver. */
const save = (id: string, line: number, owner: string, arg: string): NodeFact[] => [
  n(`${id}-call`, 'CALL', `this.repo.save(${arg})`, line, owner, {
    name: 'save', methodFullName: SAVE, receiverType: 'app.repo.ItemRepository', args: [`${id}-repo`, `${id}-arg`],
  }),
  n(`${id}-repo`, 'CALL', 'this.repo', line, owner),
  n(`${id}-arg`, 'IDENTIFIER', arg, line, owner),
];

function graph(): Graph {
  const methods: MethodFact[] = [
    endpoint('lambdaParam', 'b'), endpoint('methodRef', 'd'), endpoint('ownCallback', 'e'), endpoint('unused', 'n'),
    method(lam(1, 1), [['b-l-this', 'this', 0, 'Api'], ['b-l-x', 'x', 1, 'ANY']], ['b-l-ret']),
    method(lam(3, 1), [['e-l-this', 'this', 0, 'Api'], ['e-l-v', 'v', 1, 'ANY']], ['e-l-ret']),
    method(lam(4, 1), [['n-l-this', 'this', 0, 'Api'], ['n-l-x', 'x', 1, 'ANY']], ['n-l-ret']),
    method(SAVE, [['s-this', 'this', 0, 'app.repo.ItemRepository'], ['s-v', 'value', 1, 'String']]),
    method(CALL_FN, [['cf-this', 'this', 0, 'app.svc.Callback'], ['cf-v', 'v', 1, 'String']]),
    method(RUN, [['r-this', 'this', 0, 'app.svc.Runner'], ['r-v', 'v', 1, 'String'], ['r-cb', 'cb', 2, 'app.svc.Callback']]),
  ];
  const nodes: NodeFact[] = [
    // b: List.of(param).forEach(x -> repo.save(x))
    n('b-id', 'IDENTIFIER', 'param', 10, methods[0]!.fullName),
    n('b-of', 'CALL', 'of(param)', 10, methods[0]!.fullName, { name: 'of', methodFullName: '<unresolvedNamespace>.of', args: ['', 'b-id'] }),
    n('b-ref', 'METHOD_REF', lam(1, 1), 10, methods[0]!.fullName, { methodFullName: lam(1, 1), typeFullName: lam(1, 1) }),
    n('b-each', 'CALL', 'of(param).forEach(<lambda>)', 10, methods[0]!.fullName, {
      name: 'forEach', methodFullName: '<unresolvedNamespace>.forEach:<unresolvedSignature>(1)', args: ['b-of', 'b-ref'],
    }),
    ...save('b-s', 11, lam(1, 1), 'x'),

    // d: List.of(param).forEach(repo::save)
    n('d-id', 'IDENTIFIER', 'param', 20, methods[1]!.fullName),
    n('d-of', 'CALL', 'of(param)', 20, methods[1]!.fullName, { name: 'of', methodFullName: '<unresolvedNamespace>.of', args: ['', 'd-id'] }),
    n('d-ref', 'METHOD_REF', 'repo::save', 20, methods[1]!.fullName, {
      methodFullName: 'app.repo.ItemRepository.save:<unresolvedSignature>', typeFullName: 'app.repo.ItemRepository',
    }),
    n('d-each', 'CALL', 'of(param).forEach(repo::save)', 20, methods[1]!.fullName, {
      name: 'forEach', methodFullName: '<unresolvedNamespace>.forEach:<unresolvedSignature>(1)', args: ['d-of', 'd-ref'],
    }),

    // e: runner.run(param, v -> repo.save(v)), and Runner.run does cb.call(v)
    n('e-id', 'IDENTIFIER', 'param', 30, methods[2]!.fullName),
    n('e-run-recv', 'CALL', 'this.runner', 30, methods[2]!.fullName),
    n('e-ref', 'METHOD_REF', lam(3, 1), 30, methods[2]!.fullName, { methodFullName: lam(3, 1), typeFullName: lam(3, 1) }),
    n('e-run', 'CALL', 'this.runner.run(param, <lambda>)', 30, methods[2]!.fullName, {
      name: 'run', methodFullName: RUN, args: ['e-run-recv', 'e-id', 'e-ref'],
    }),
    ...save('e-s', 31, lam(3, 1), 'v'),
    n('e-cb', 'IDENTIFIER', 'cb', 40, RUN),
    n('e-cv', 'IDENTIFIER', 'v', 40, RUN),
    n('e-dispatch', 'CALL', 'cb.call(v)', 40, RUN, { name: 'call', methodFullName: CALL_FN, args: ['e-cb', 'e-cv'] }),

    // n: a lambda passed to the same jar call whose parameter is never used with the repository
    n('n-id', 'IDENTIFIER', 'param', 50, methods[3]!.fullName),
    n('n-ref', 'METHOD_REF', lam(4, 1), 50, methods[3]!.fullName, { methodFullName: lam(4, 1), typeFullName: lam(4, 1) }),
    n('n-each', 'CALL', 'of(param).forEach(<lambda>)', 50, methods[3]!.fullName, {
      name: 'forEach', methodFullName: '<unresolvedNamespace>.forEach:<unresolvedSignature>(1)', args: ['n-id', 'n-ref'],
    }),
    n('n-lit', 'LITERAL', '"fixed"', 51, lam(4, 1)),
    ...save('n-s', 51, lam(4, 1), '"fixed"'),
  ];
  // The lambda returns, so the call exception sees a body. Each is `return <save call>`.
  nodes.push(
    n('b-l-ret', 'RETURN', 'return repo.save(x)', 11, lam(1, 1), { args: ['b-s-call'] }),
    n('e-l-ret', 'RETURN', 'return repo.save(v)', 31, lam(3, 1), { args: ['e-s-call'] }),
    n('n-l-ret', 'RETURN', 'return repo.save("fixed")', 51, lam(4, 1), { args: ['n-s-call'] }),
  );
  // Lambda parameters are nodes too, in the real dump; the edges target them by id.
  for (const p of methods.flatMap((x) => x.params)) {
    if (!nodes.some((x) => x.id === p.id)) nodes.push(n(p.id, 'METHOD_PARAMETER_IN', p.name, 1, 'x'));
  }
  return {
    version: 1, commit: 'x', types: [], methods, nodes,
    edges: [
      // Each endpoint's request parameter is what its first expression reads.
      { kind: 'rd', from: 'b-p', to: 'b-id' },
      { kind: 'rd', from: 'd-p', to: 'd-id' },
      { kind: 'rd', from: 'e-p', to: 'e-id' },
      { kind: 'rd', from: 'n-p', to: 'n-id' },
      // Runner.run reads its own parameter before dispatching.
      { kind: 'rd', from: 'r-v', to: 'e-cv' },
      // Each lambda body reads its own parameter.
      { kind: 'rd', from: 'b-l-x', to: 'b-s-arg' },
      { kind: 'rd', from: 'e-l-v', to: 'e-s-arg' },
      { kind: 'rd', from: 'n-l-x', to: 'n-lit' },
    ],
  };
}

function pairs(g: Graph): Set<string> {
  const m = buildModel(g);
  return new Set(allPairs(m, condense(m), boundaries(m)).map((p) => `${p.source.label} -> ${p.sink.label}`));
}
const SINK = 'ItemRepository.save';

test('b: the receiver reaches the parameter of a lambda passed to a jar call', () => {
  const found = pairs(graph());
  assert.ok(found.has(`POST /lambdaParam -> ${SINK}`), [...found].join('\n'));
});

test('d: a method reference to a repository write is the boundary, fed by the call receiver', () => {
  const g = graph();
  const bs = boundaries(buildModel(g));
  assert.ok(bs.some((b) => b.node === 'd-ref' && b.dir === 'out' && b.label === SINK));
  assert.ok(pairs(g).has(`POST /methodRef -> ${SINK}`));
});

test('e: a call through a body-less interface reaches the lambda that was passed for that type', () => {
  assert.ok(pairs(graph()).has(`POST /ownCallback -> ${SINK}`));
});

test('a lambda that never uses its parameter with the repository gives no pair', () => {
  assert.ok(!pairs(graph()).has(`POST /unused -> ${SINK}`));
});

test('dispatch reaches only types that carried a callback', () => {
  const g = graph();
  // Drop the call that passed the lambda to a Callback-typed parameter: nothing is recorded for it.
  g.nodes = g.nodes.filter((x) => x.id !== 'e-run');
  assert.ok(!pairs(g).has(`POST /ownCallback -> ${SINK}`));
});
