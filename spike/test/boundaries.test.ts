import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand, KEY } from './corpus.ts';
import { buildModel } from '../src/load.ts';
import { boundaries } from '../src/boundaries.ts';

const bs = boundaries(buildModel(hand()));
// A boundary sits on a graph node, and several nodes share a line - an endpoint's parameters are one
// boundary each. What a reader sees is the distinct set on that line.
const labels = (stmt: string) =>
  [...new Set(bs.filter((b) => b.stmt === stmt).map((b) => `${b.dir} ${b.kind} ${b.label}`))].sort();

test('an endpoint is an input, labelled by verb and full path', () => {
  assert.deepEqual(labels(KEY.endpoint), ['in net POST /loans/{id}/charge']);
});

test('what an endpoint returns is an output', () => {
  assert.deepEqual(labels(KEY.respond), ['out net POST /loans/{id}/charge returns']);
});

test('a repository read is an input and a repository write is an output', () => {
  assert.deepEqual(labels(KEY.findById), ['in db LoanRepository.findById']);
  assert.deepEqual(labels(KEY.save), ['out db LoanRepository.save']);
});

// The headline case the spike has to score on. Fineract 542da12e6 ends getDefaultAllocationRule
// with `...findFirst().get()`: the new way that code fails is Optional.get, and there is no `throw`
// anywhere in the diff. A rule that only reads <operator>.throw nodes scores zero here.
test('an exception the JDK throws is an output even with no throw in the source', () => {
  assert.deepEqual(labels(KEY.optionalGet), ['out throw throws NoSuchElementException in pick']);
});

test('an explicit throw is an output, typed from the source text', () => {
  const g = hand();
  g.nodes.push({
    id: 't1', label: 'CALL', code: 'throw new LoanNotFoundException(id)',
    file: 'app/svc/Svc.java', line: 20, method: 'app.svc.Svc.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)',
    name: '<operator>.throw', methodFullName: '<operator>.throw',
  });
  const found = boundaries(buildModel(g)).filter((b) => b.stmt === 'app/svc/Svc.java:20');
  assert.deepEqual([...new Set(found.map((b) => b.label))], ['throws LoanNotFoundException in apply']);
});

test('a rethrow is an output too, and says so rather than guessing a type', () => {
  const g = hand();
  g.nodes.push({
    id: 't2', label: 'CALL', code: 'throw re', file: 'app/svc/Svc.java', line: 21,
    method: 'app.svc.Svc.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)',
    name: '<operator>.throw', methodFullName: '<operator>.throw',
  });
  const found = boundaries(buildModel(g)).filter((b) => b.stmt === 'app/svc/Svc.java:21');
  assert.deepEqual([...new Set(found.map((b) => b.label))], ['rethrows in apply']);
});

test('a JsonCommand parameter read is an input naming the JSON key', () => {
  const g = hand();
  g.nodes.push(
    {
      id: 'j1', label: 'CALL', code: 'command.bigDecimalValueOfParameterNamed("principal")',
      file: 'app/api/Api.java', line: 30, method: 'app.api.Api.addCharge:jakarta.ws.rs.core.Response(java.lang.Long,java.math.BigDecimal)',
      name: 'bigDecimalValueOfParameterNamed',
      methodFullName: 'org.apache.fineract.infrastructure.core.api.JsonCommand.bigDecimalValueOfParameterNamed:java.math.BigDecimal(java.lang.String)',
      receiverType: 'org.apache.fineract.infrastructure.core.api.JsonCommand', args: ['j2', 'j3'],
    },
    { id: 'j2', label: 'IDENTIFIER', code: 'command', file: 'app/api/Api.java', line: 30, method: 'app.api.Api.addCharge:jakarta.ws.rs.core.Response(java.lang.Long,java.math.BigDecimal)' },
    { id: 'j3', label: 'LITERAL', code: '"principal"', file: 'app/api/Api.java', line: 30, method: 'app.api.Api.addCharge:jakarta.ws.rs.core.Response(java.lang.Long,java.math.BigDecimal)' },
  );
  const found = boundaries(buildModel(g)).filter((b) => b.stmt === 'app/api/Api.java:30');
  assert.deepEqual([...new Set(found.map((b) => `${b.dir} ${b.kind} ${b.label}`))], ['in json json principal']);
});

test('a JDBC query is an input and a JDBC update is an output', () => {
  const g = hand();
  const method = 'app.svc.Svc.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)';
  g.nodes.push(
    { id: 'q1', label: 'CALL', code: 'jdbc.query(sql, mapper)', file: 'app/svc/Svc.java', line: 50, method, name: 'query', methodFullName: 'org.springframework.jdbc.core.JdbcTemplate.query:java.util.List(java.lang.String)', receiverType: 'org.springframework.jdbc.core.JdbcTemplate' },
    { id: 'q2', label: 'CALL', code: 'jdbc.update(sql)', file: 'app/svc/Svc.java', line: 51, method, name: 'update', methodFullName: 'org.springframework.jdbc.core.JdbcTemplate.update:int(java.lang.String)', receiverType: 'org.springframework.jdbc.core.JdbcTemplate' },
  );
  const found = boundaries(buildModel(g));
  assert.deepEqual([...new Set(found.filter((b) => b.stmt === 'app/svc/Svc.java:50').map((b) => b.dir))], ['in']);
  assert.deepEqual([...new Set(found.filter((b) => b.stmt === 'app/svc/Svc.java:51').map((b) => b.dir))], ['out']);
});

test('boundary keys never contain a line number, so they survive a diff', () => {
  const withLine = bs.filter((b) => /:\d+/.test(b.key));
  assert.deepEqual(withLine, []);
});

test('nothing else in the fixture is a boundary', () => {
  const marked = [...new Set(bs.map((b) => b.stmt))].sort();
  assert.deepEqual(marked, [KEY.endpoint, KEY.findById, KEY.save, KEY.respond, KEY.optionalGet].sort());
});

// Joern types a chain off a Lombok getter as `<unresolvedNamespace>`, which is how the headline case
// hides: the rule that reads the receiver's type has no type to read. The call that produced the
// receiver still names it.
test('a JDK throw is found even when Joern lost the receiver type', () => {
  const g = hand();
  const method = 'app.svc.Svc.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)';
  g.nodes.push(
    {
      id: 'u1', label: 'CALL', code: 'loan.getRules().stream().findFirst().get()',
      file: 'app/svc/Svc.java', line: 60, method, name: 'get',
      methodFullName: '<unresolvedNamespace>.get:<unresolvedSignature>(0)', receiverType: '<unresolvedNamespace>', args: ['u2'],
    },
    {
      id: 'u2', label: 'CALL', code: 'loan.getRules().stream().findFirst()',
      file: 'app/svc/Svc.java', line: 60, method, name: 'findFirst',
      methodFullName: '<unresolvedNamespace>.findFirst:<unresolvedSignature>(0)', receiverType: '<unresolvedNamespace>',
    },
  );
  const found = boundaries(buildModel(g)).filter((b) => b.stmt === 'app/svc/Svc.java:60');
  assert.deepEqual([...new Set(found.map((b) => `${b.dir} ${b.kind} ${b.label}`))], ['out throw throws NoSuchElementException in apply']);
});

// `findById(..).orElseThrow(..)` is how Fineract fails more often than any other way. The read and
// the throw are two boundaries on one line, and the throw is named by what the supplier builds.
test('an orElseThrow is an output named after the exception its supplier builds', () => {
  const g = hand();
  const method = 'app.svc.Svc.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)';
  g.nodes.push(
    {
      id: 'e1', label: 'CALL', code: 'repo.findById(id).orElseThrow(() -> new LoanNotFoundException(id))',
      file: 'app/svc/Svc.java', line: 61, method, name: 'orElseThrow',
      methodFullName: '<unresolvedNamespace>.orElseThrow:<unresolvedSignature>(1)', receiverType: '<unresolvedNamespace>', args: ['e2'],
    },
    {
      id: 'e2', label: 'CALL', code: 'repo.findById(id)', file: 'app/svc/Svc.java', line: 61, method, name: 'findById',
      methodFullName: 'app.repo.LoanRepository.findById:java.util.Optional(java.lang.Long)', receiverType: 'app.repo.LoanRepository',
    },
  );
  const found = boundaries(buildModel(g)).filter((b) => b.stmt === 'app/svc/Svc.java:61');
  assert.deepEqual(
    [...new Set(found.map((b) => `${b.dir} ${b.kind} ${b.label}`))].sort(),
    ['in db LoanRepository.findById', 'out throw throws LoanNotFoundException in apply'],
  );
});

test('an orElseThrow with no supplier throws what the JDK throws', () => {
  const g = hand();
  g.nodes.push({
    id: 'e3', label: 'CALL', code: 'find(id).orElseThrow()', file: 'app/svc/Svc.java', line: 62,
    method: 'app.svc.Svc.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)',
    name: 'orElseThrow', methodFullName: '<unresolvedNamespace>.orElseThrow:<unresolvedSignature>(0)',
    receiverType: '<unresolvedNamespace>',
  });
  const found = boundaries(buildModel(g)).filter((b) => b.stmt === 'app/svc/Svc.java:62');
  assert.deepEqual([...new Set(found.map((b) => b.label))], ['throws NoSuchElementException in apply']);
});
