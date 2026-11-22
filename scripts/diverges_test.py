"""Tests for diverges.py: python3 -m unittest discover -s scripts -p '*_test.py'

The three fixtures under testdata/ are real `--summaries` output, trimmed to the entries each case needs.
Everything else is a hand-made document small enough to read in the test.
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

import diverges
from diverges import Effect, Field

HERE = os.path.dirname(os.path.abspath(__file__))
KAFKA = os.path.join(HERE, 'testdata', 'kafka-24128.summaries.json')
KAFKA_FEEDS = os.path.join(HERE, 'testdata', 'kafka-24187.summaries.json')
KEYCLOAK = os.path.join(HERE, 'testdata', 'keycloak-49604.summaries.json')

MODERN = 'org.apache.kafka.coordinator.group.modern.'
CONSUMER = MODERN + 'consumer.ConsumerGroup#getOrMaybeCreateMember(java.lang.String,boolean)'
SHARE = MODERN + 'share.ShareGroup#getOrMaybeCreateMember(java.lang.String,boolean)'
MEMBERS = Field(MODERN + 'ModernGroup', 'members')
SP = 'org.keycloak.adapters.saml.config.SP'
BUILD = 'org.keycloak.adapters.saml.config.parsers.DeploymentBuilder#build(java.io.InputStream,ResourceLoader)'


def doc(*methods):
    return {'format': 'codeflow-summaries', 'version': 1, 'methods': list(methods)}


def method(name, flows=(), gaps=()):
    return {'method': name, 'at': 'X.java:1:1', 'flows': [list(f) for f in flows], 'gaps': list(gaps)}


def gap(detail, kind='unmodelled'):
    return {'kind': kind, 'at': 'X.java:5:9', 'detail': detail}


def compare(document, *selectors):
    families = diverges.select(document, list(selectors))
    assert len(families) == 1, families
    return diverges.compare(document, families[0])


def row(rows, effect):
    found = [r for r in rows if r.effect == effect]
    assert len(found) == 1, f'{effect} is in {len(found)} rows of {[r.effect for r in rows]}'
    return found[0]


class Kafka24128(unittest.TestCase):
    """ShareGroup#getOrMaybeCreateMember stores the member it creates; ConsumerGroup's forgot to."""

    def setUp(self):
        self.rows = compare(diverges.load(KAFKA), 'overrides:getOrMaybeCreateMember')

    def test_put_on_members_is_in_share_group_and_firmly_absent_in_consumer_group(self):
        put = row(self.rows, Effect('call', 'put', MEMBERS))
        self.assertEqual([SHARE], put.present)
        self.assertEqual([CONSUMER], put.absent)
        self.assertEqual([], put.unknown)
        self.assertEqual({SHARE: ['ShareGroup.java:236:38']}, put.at)

    def test_consumer_group_does_nothing_share_group_does_not(self):
        self.assertTrue(self.rows)
        self.assertEqual({(SHARE,)}, {tuple(r.present) for r in self.rows})

    def test_what_both_do_is_not_a_row(self):
        effects = [r.effect for r in self.rows]
        self.assertNotIn(Effect('call', 'get', MEMBERS), effects)
        self.assertNotIn(Effect('read', None, MEMBERS), effects)

    def test_mutator_calls_come_before_other_calls_and_reads(self):
        effects = [r.effect for r in self.rows]
        put = effects.index(Effect('call', 'put', MEMBERS))
        self.assertLess(put, effects.index(Effect('call', 'isEmpty', MEMBERS)))
        self.assertLess(put, effects.index(Effect('read', None, Field(MODERN + 'share.ShareGroup', 'state'))))
        self.assertIn(effects[0].label, ('compute', 'put', 'set'))


class Keycloak49604(unittest.TestCase):
    """DeploymentBuilder#build copies an SP into a deployment field by field, and skips one field."""

    def setUp(self):
        self.found = diverges.fields(diverges.load(KEYCLOAK), 'SP')

    def test_build_is_the_only_method_reported(self):
        self.assertEqual([BUILD], [f.method for f in self.found])

    def test_build_misses_exactly_the_field_the_fix_added(self):
        build = self.found[0]
        self.assertEqual([Field(SP, 'turnOffChangeSessionIdOnLogin')], build.missed)
        self.assertEqual(13, len(build.touched))

    def test_the_miss_is_not_firm_because_build_has_gaps(self):
        self.assertEqual(30, self.found[0].gaps)


class Effects(unittest.TestCase):
    def effects(self, flows=(), gaps=(), others=()):
        m = method('a.C#m()', flows, gaps)
        document = doc(m, *[method(o) for o in others])
        return diverges.effects(m, diverges.class_names(document))

    def test_a_write_is_a_write_whoever_holds_the_object(self):
        for target in ('this.a.C.f', 'P0.a.C.f', 'RET.a.C.f'):
            with self.subTest(target):
                self.assertIn(Effect('write', None, Field('a.C', 'f')), self.effects([('P1', target)]))

    def test_a_flow_out_of_a_field_is_a_read(self):
        for source in ('this.a.C.f', 'P0.a.C.f', 'static.a.C.f'):
            with self.subTest(source):
                self.assertEqual({Effect('read', None, Field('a.C', 'f')): []}, self.effects([(source, 'RET')]))

    def test_a_field_path_is_split_on_the_classes_the_document_names(self):
        effects = self.effects([('this.a.C.x.a.C.Inner.f', 'RET')], others=['a.C.Inner#get()'])
        self.assertEqual({Effect('read', None, Field('a.C', 'x')), Effect('read', None, Field('a.C.Inner', 'f'))},
                         set(effects))

    def test_a_write_deep_in_a_path_reads_the_fields_on_the_way(self):
        effects = self.effects([('P0', 'this.a.C.x.a.C.Inner.f')], others=['a.C.Inner#get()'])
        self.assertEqual({Effect('read', None, Field('a.C', 'x')), Effect('write', None, Field('a.C.Inner', 'f'))},
                         set(effects))

    def test_a_class_the_document_does_not_name_is_split_at_the_last_dot(self):
        self.assertEqual({Effect('read', None, Field('b.Unseen', 'f'))}, set(self.effects([('this.b.Unseen.f', 'RET')])))

    def test_a_class_the_document_does_not_name_ends_a_path_a_named_one_starts(self):
        self.assertEqual({Effect('read', None, Field('a.C', 'x')), Effect('read', None, Field('b.Unseen', 'g'))},
                         set(self.effects([('this.a.C.x.b.Unseen.g', 'RET')])))

    def test_a_class_the_document_does_not_name_stops_where_a_named_one_starts(self):
        self.assertEqual({Effect('read', None, Field('b.Unseen', 'f')), Effect('read', None, Field('a.C', 'g'))},
                         set(self.effects([('this.b.Unseen.f.a.C.g', 'RET')])))

    def test_a_call_is_keyed_by_label_and_reaching_field_and_keeps_its_positions(self):
        effects = self.effects([('this.a.C.f', 'ext-arg:put@C.java:3:5'), ('P0', 'ext-arg:put@C.java:3:5'),
                                ('P0', 'ext-arg:put@C.java:8:5')])
        self.assertEqual(['C.java:3:5'], effects[Effect('call', 'put', Field('a.C', 'f'))])
        self.assertEqual(['C.java:3:5', 'C.java:8:5'], effects[Effect('call', 'put', None)])

    def test_the_field_reaching_a_call_is_the_last_of_its_path(self):
        effects = self.effects([('this.a.C.x.a.C.Inner.f', 'ext-arg:put@C.java:3:5')], others=['a.C.Inner#get()'])
        self.assertIn(Effect('call', 'put', Field('a.C.Inner', 'f')), effects)
        self.assertNotIn(Effect('call', 'put', Field('a.C', 'x')), effects)

    def test_a_gap_naming_a_field_read_or_written_is_that_read_or_write(self):
        effects = self.effects(gaps=[
            gap('a.C.f read on an object with no tracked origin'),
            gap('a.C.g written on an object with no tracked origin'),
            gap('this.a.C.h read by C#getH on no object this can name'),
            gap('a.C.i written by C#setI on no object this can name'),
        ])
        self.assertEqual({
            Effect('read', None, Field('a.C', 'f')): ['X.java:5:9'],
            Effect('write', None, Field('a.C', 'g')): ['X.java:5:9'],
            Effect('read', None, Field('a.C', 'h')): ['X.java:5:9'],
            Effect('write', None, Field('a.C', 'i')): ['X.java:5:9'],
        }, effects)

    def test_any_other_gap_is_no_effect(self):
        self.assertEqual({}, self.effects(gaps=[gap('DECONSTRUCTION_PATTERN'), gap('C#f on this', 'dispatch'),
                                                gap('RET.a.C.x.a.C.y.a.C.z', 'depth')]))


class Compare(unittest.TestCase):
    W = Effect('write', None, Field('a.C', 'w'))
    R = Effect('read', None, Field('a.C', 'r'))

    def test_an_absence_behind_a_gap_is_unknown_and_one_behind_none_is_absent(self):
        rows = compare(doc(method('a.C#one()', [('P0', 'this.a.C.w')]),
                           method('a.C#two()', gaps=[gap('C#f on this', 'dispatch')]),
                           method('a.C#three()')),
                       'a.C#one', 'a.C#two', 'a.C#three')
        only = row(rows, self.W)
        self.assertEqual(['a.C#one()'], only.present)
        self.assertEqual(['a.C#three()'], only.absent)
        self.assertEqual(['a.C#two()'], only.unknown)

    def test_what_one_sibling_forgets_comes_before_what_one_sibling_adds(self):
        read = ('this.a.C.r', 'RET')
        rows = compare(doc(method('a.C#one()', [read, ('P0', 'this.a.C.w')]),
                           method('a.C#two()', [read]),
                           method('a.C#three()')),
                       'a.C#one', 'a.C#two', 'a.C#three')
        self.assertEqual([self.R, self.W], [r.effect for r in rows])

    def test_a_firm_absence_comes_before_an_unknown_one(self):
        rows = compare(doc(method('a.C#one()', [('this.a.C.r', 'RET'), ('P0', 'this.a.C.w')]),
                           method('a.C#two()', [('this.a.C.r', 'RET')], gaps=[gap('C#f on this', 'dispatch')]),
                           method('a.C#three()', [('P0', 'this.a.C.w')])),
                       'a.C#one', 'a.C#two', 'a.C#three')
        self.assertEqual([(self.R, ['a.C#three()'], []), (self.W, [], ['a.C#two()'])],
                         [(r.effect, r.absent, r.unknown) for r in rows])

    def test_rows_lacked_by_the_same_siblings_stay_together(self):
        one = [('this.a.C.r', 'RET'), ('P0', 'this.a.C.w'), ('P0', 'this.a.C.v'), ('this.a.C.q', 'RET')]
        rows = compare(doc(method('a.C#one()', one),
                           method('a.C#two()', [('P0', 'this.a.C.w'), ('this.a.C.q', 'RET')]),
                           method('a.C#three()', [('this.a.C.r', 'RET'), ('P0', 'this.a.C.v')])),
                       'a.C#one', 'a.C#two', 'a.C#three')
        self.assertEqual([('v', 'two'), ('r', 'two'), ('w', 'three'), ('q', 'three')],
                         [(r.effect.field.name, r.absent[0][len('a.C#'):-2]) for r in rows])

    def test_calls_at_different_places_on_the_same_field_are_one_effect(self):
        rows = compare(doc(method('a.C#one()', [('this.a.C.w', 'ext-arg:put@C.java:3:5')]),
                           method('a.C#two()', [('this.a.C.w', 'ext-arg:put@C.java:9:5')])),
                       'a.C#one', 'a.C#two')
        self.assertEqual([], rows)

    def test_a_call_no_field_reaches_differs_from_one_a_field_reaches(self):
        rows = compare(doc(method('a.C#one()', [('this.a.C.w', 'ext-arg:put@C.java:3:5')]),
                           method('a.C#two()', [('P0', 'ext-arg:put@C.java:9:5')])),
                       'a.C#one', 'a.C#two')
        self.assertEqual({Effect('call', 'put', Field('a.C', 'w')), Effect('read', None, Field('a.C', 'w'))},
                         {r.effect for r in rows})


class Select(unittest.TestCase):
    DOC = doc(method('a.C#beat(int)'), method('a.D#beat(int)'), method('a.D#beat(int,int)'),
              method('a.D#heartbeat(int)'), method('a.D#other()'))

    def names(self, *selectors):
        return [[m['method'] for m in family] for family in diverges.select(self.DOC, list(selectors))]

    def test_overrides_are_the_methods_sharing_a_name_and_a_parameter_list(self):
        self.assertEqual([['a.C#beat(int)', 'a.D#beat(int)']], self.names('overrides:beat'))

    def test_overrides_of_a_name_declared_once_is_an_error(self):
        with self.assertRaisesRegex(ValueError, 'heartbeat'):
            self.names('overrides:heartbeat')

    def test_a_stem_matches_the_simple_name_as_a_glob(self):
        self.assertEqual([['a.C#beat(int)', 'a.D#beat(int)', 'a.D#beat(int,int)', 'a.D#heartbeat(int)']],
                         self.names('stem:*beat'))
        self.assertEqual([['a.C#beat(int)', 'a.D#beat(int)', 'a.D#beat(int,int)']], self.names('stem:b*'))

    def test_methods_named_one_by_one_are_one_family(self):
        self.assertEqual([['a.D#heartbeat(int)', 'a.D#other()']], self.names('heartbeat', 'D#other'))

    def test_a_name_matching_several_methods_is_an_error_listing_them(self):
        with self.assertRaisesRegex(ValueError, r'a\.D#beat\(int\).*a\.D#beat\(int,int\)'):
            self.names('D#beat', 'other')

    def test_a_name_matching_nothing_is_an_error(self):
        with self.assertRaisesRegex(ValueError, 'missing'):
            self.names('missing', 'other')

    def test_one_method_is_not_a_family(self):
        with self.assertRaises(ValueError):
            self.names('other')


class Fields(unittest.TestCase):
    # C's four fields are known from its constructor, which writes them all and so is never reported.
    C = [method('a.C#<init>(int,int,int,int)', [(f'P{n}', f'this.a.C.{f}') for n, f in enumerate('fghi')])]

    def user(self, name, *fields):
        return method(name, [(f'P0.a.C.{f}', 'RET') for f in fields])

    def test_a_method_touching_most_fields_and_missing_some_is_reported_with_no_gaps(self):
        found = diverges.fields(doc(*self.C, self.user('a.U#copy(a.C)', 'f', 'g', 'h')), 'C')
        self.assertEqual(['a.U#copy(a.C)'], [f.method for f in found])
        self.assertEqual([Field('a.C', 'i')], found[0].missed)
        self.assertEqual(0, found[0].gaps)

    def test_a_method_touching_every_field_is_not_reported(self):
        self.assertEqual([], diverges.fields(doc(*self.C, self.user('a.U#copy(a.C)', 'f', 'g', 'h', 'i')), 'C'))

    def test_a_method_touching_fewer_than_the_share_asked_for_is_not_reported(self):
        document = doc(*self.C, self.user('a.U#peek(a.C)', 'f'))
        self.assertEqual([], diverges.fields(document, 'C'))
        self.assertEqual(['a.U#peek(a.C)'], [f.method for f in diverges.fields(document, 'C', share=0.25)])

    def test_the_method_missing_fewest_comes_first(self):
        found = diverges.fields(doc(*self.C, self.user('a.U#half(a.C)', 'f', 'g'), self.user('a.U#most(a.C)', 'f', 'g', 'h')), 'C')
        self.assertEqual(['a.U#most(a.C)', 'a.U#half(a.C)'], [f.method for f in found])

    def test_a_class_is_named_in_full_or_by_its_simple_name(self):
        document = doc(*self.C, self.user('a.U#copy(a.C)', 'f', 'g', 'h'), method('a.AC#m()'))
        self.assertEqual(['a.U#copy(a.C)'], [f.method for f in diverges.fields(document, 'a.C')])
        self.assertEqual(diverges.fields(document, 'a.C'), diverges.fields(document, 'C'))

    def test_a_class_name_two_classes_share_is_an_error(self):
        with self.assertRaisesRegex(ValueError, r'a\.C.*b\.C'):
            diverges.fields(doc(*self.C, method('b.C#m()')), 'C')

    def test_a_class_with_no_field_in_the_document_is_an_error(self):
        with self.assertRaisesRegex(ValueError, 'U'):
            diverges.fields(doc(*self.C, self.user('a.U#copy(a.C)', 'f')), 'U')


class Kafka24187(unittest.TestCase):
    """Two paths deserialize a classic member's subscription, and one reads it through the group's selected
    protocol: `metadata(protocolName().get())` where the other has `iterator().next().metadata()`."""

    def setUp(self):
        self.found = diverges.sink(diverges.load(KAFKA_FEEDS), 'deserializeConsumerProtocolSubscription')

    def test_both_paths_feed_it_the_wrapped_metadata(self):
        self.assertEqual(['metadata()', 'wrap()'], self.found.common)

    def test_only_from_classic_group_feeds_it_a_get(self):
        self.assertEqual([([], ['deserializeSubscription']), (['get()'], ['fromClassicGroup'])],
                         [(way.feeds, [diverges.simple_name(m) for m in way.methods]) for way in self.found.ways])


class Sink(unittest.TestCase):
    """What reaches one call, method by method."""
    F = 'ext-arg:f@X.java:9:9'

    def ways(self, document, name='f'):
        found = diverges.sink(document, name)
        return found.common, [(way.feeds, [m['method'] for m in way.methods]) for way in found.ways]

    def test_a_feed_is_a_field_a_call_result_or_a_parameter(self):
        document = doc(method('a.C#one()', [('this.a.C.x.a.D.y', self.F), ('ext:g@X.java:2:2', self.F),
                                            ('P3', self.F), ('P0', self.F)]),
                       method('a.D#two()', [('P1', self.F)]))
        self.assertEqual((['a parameter'], [([], ['a.D#two()']), ([Field('a.D', 'y'), 'g()'], ['a.C#one()'])]),
                         self.ways(document))

    def test_methods_fed_alike_are_one_way_wherever_their_calls_are(self):
        document = doc(method('a.C#one()', [('ext:g@X.java:2:2', self.F)]),
                       method('a.C#two()', [('ext:g@X.java:7:7', 'ext-arg:f@X.java:8:8')]),
                       method('a.C#three()', [('ext:h@X.java:3:3', self.F)]))
        self.assertEqual(([], [(['g()'], ['a.C#one()', 'a.C#two()']), (['h()'], ['a.C#three()'])]), self.ways(document))

    def test_a_position_names_one_call_of_that_label(self):
        document = doc(method('a.C#one()', [('ext:g@X.java:2:2', self.F)]),
                       method('a.C#two()', [('ext:h@X.java:7:7', 'ext-arg:f@X.java:8:8')]))
        self.assertEqual((['h()'], [([], ['a.C#two()'])]), self.ways(document, 'f@X.java:8:8'))

    def test_a_method_nothing_reaches_the_call_in_is_not_listed(self):
        document = doc(method('a.C#one()', [('ext:g@X.java:2:2', self.F)]),
                       method('a.C#other()', [('ext:g@X.java:2:2', 'ext-arg:fg@X.java:9:9'), ('ext:g@X.java:2:2', 'RET')]))
        self.assertEqual((['g()'], [([], ['a.C#one()'])]), self.ways(document))

    def test_the_way_adding_least_comes_first(self):
        a, b, g, h = (f'ext:{name}@X.java:2:2' for name in 'abgh')
        document = doc(method('a.C#most()', [(g, self.F), (a, self.F), (b, self.F)]),
                       method('a.C#more()', [(g, self.F), (h, self.F)]),
                       method('a.C#bare()', [(g, self.F)]))
        self.assertEqual((['g()'], [([], ['a.C#bare()']), (['h()'], ['a.C#more()']), (['a()', 'b()'], ['a.C#most()'])]),
                         self.ways(document))

    def test_a_call_nothing_reaches_is_an_error(self):
        with self.assertRaisesRegex(ValueError, r'nothing reaches a call f@X\.java:1:1'):
            diverges.sink(doc(method('a.C#one()', [('P0', self.F)])), 'f@X.java:1:1')


class Load(unittest.TestCase):
    def load(self, document):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = os.path.join(directory.name, 'doc.json')
        with open(path, 'w') as f:
            json.dump(document, f)
        return diverges.load(path)

    def test_a_summaries_document_loads(self):
        self.assertEqual(5, len(diverges.load(KAFKA)['methods']))

    def test_another_format_is_refused(self):
        with self.assertRaisesRegex(ValueError, 'codeflow-graph'):
            self.load({'format': 'codeflow-graph', 'version': 1, 'methods': []})

    def test_another_version_is_refused(self):
        with self.assertRaisesRegex(ValueError, '2'):
            self.load({'format': 'codeflow-summaries', 'version': 2, 'methods': []})


class Text(unittest.TestCase):
    def test_a_row_shows_three_positions_from_the_siblings_having_it_and_counts_the_rest(self):
        document = doc(method('a.C#one()', [('P0', 'ext-arg:f@X.java:1:1'), ('P0', 'ext-arg:f@X.java:2:1')]),
                       method('a.C#two()', [('P0', 'ext-arg:f@Y.java:3:1'), ('P0', 'ext-arg:f@Y.java:4:1'),
                                            ('P0', 'ext-arg:f@Y.java:5:1')]),
                       method('a.C#three()'))
        family = diverges.select(document, ['a.C#one', 'a.C#two', 'a.C#three'])[0]
        text = diverges.family_text('q', family, diverges.compare(document, family), diverges.class_names(document))
        self.assertEqual(['in 1,2, not in 3: 1 effect', '  call f  X.java:1:1 X.java:2:1 Y.java:3:1 +2'], text[-2:])


class CommandLine(unittest.TestCase):
    def run_script(self, *args):
        return subprocess.run([sys.executable, os.path.join(HERE, 'diverges.py'), *args], capture_output=True, text=True)

    def test_the_text_names_each_sibling_and_prints_the_put_row_against_them(self):
        done = self.run_script(KAFKA, 'overrides:getOrMaybeCreateMember')
        self.assertEqual((0, ''), (done.returncode, done.stderr))
        self.assertRegex(done.stdout, r'(?m)^1 = ConsumerGroup#getOrMaybeCreateMember\(java\.lang\.String,boolean\) +ConsumerGroup\.java:255:5 +no gaps$')
        self.assertRegex(done.stdout, r'(?m)^2 = ShareGroup#getOrMaybeCreateMember\(java\.lang\.String,boolean\) +ShareGroup\.java:207:5 +1 gap: 1 dispatch$')
        self.assertRegex(done.stdout, r'(?m)^in 2, not in 1: 24 effects$')
        self.assertRegex(done.stdout, r'(?m)^  call put\(ModernGroup\.members\) +ShareGroup\.java:236:38$')

    def test_an_unknown_absence_is_printed_with_a_question_mark(self):
        done = self.run_script(KAFKA, 'ShareGroup#removeMember', 'ConsumerGroup#getOrMaybeCreateMember')
        self.assertEqual((0, ''), (done.returncode, done.stderr))
        self.assertRegex(done.stdout, r'(?m)^1 = ShareGroup#removeMember')
        firm, unknown = done.stdout.index('\nin 1, not in 2: '), done.stdout.index('\nin 2, not in 1?: ')
        self.assertLess(firm, done.stdout.index('\n  call remove(ModernGroup.members) '))
        self.assertLess(done.stdout.index('\n  call remove(ModernGroup.members) '), unknown)
        self.assertLess(unknown, done.stdout.index('\n  call UnknownMemberIdException '))

    def test_json_carries_the_same_rows(self):
        done = self.run_script(KAFKA, 'overrides:getOrMaybeCreateMember', '--json')
        self.assertEqual((0, ''), (done.returncode, done.stderr))
        families = json.loads(done.stdout)['families']
        put = [r for r in families[0]['rows'] if r['effect'] == 'call put(ModernGroup.members)']
        self.assertEqual([{'effect': 'call put(ModernGroup.members)', 'kind': 'call', 'label': 'put',
                           'field': MODERN + 'ModernGroup.members', 'present': [SHARE], 'absent': [CONSUMER],
                           'unknown': [], 'at': {SHARE: ['ShareGroup.java:236:38']}}], put)

    def test_fields_prints_the_method_and_what_it_misses(self):
        done = self.run_script(KEYCLOAK, 'fields:SP')
        self.assertEqual((0, ''), (done.returncode, done.stderr))
        self.assertRegex(done.stdout, r'(?m)^diverges fields:SP: 1 found \(methods reading or writing at least 0\.5 of its fields, and not all\)$')
        self.assertRegex(done.stdout, r'(?m)^DeploymentBuilder#build\(java\.io\.InputStream,ResourceLoader\) +13 of 14 +30 gaps: 15 depth, 15 unmodelled +turnOffChangeSessionIdOnLogin\?$')

    def test_sink_prints_each_way_the_call_is_fed(self):
        done = self.run_script(KAFKA_FEEDS, 'sink:deserializeConsumerProtocolSubscription')
        self.assertEqual((0, ''), (done.returncode, done.stderr))
        self.assertEqual("""diverges sink:deserializeConsumerProtocolSubscription: 2 methods, fed 2 ways

in all of them: metadata(), wrap()

1 = nothing else
  GroupMetadataManager#deserializeSubscription(JoinGroupRequestProtocolCollection)  GroupMetadataManager.java:2071:5  no gaps

2 = get()
  ConsumerGroup#fromClassicGroup(LogContext,SnapshotRegistry,ClassicGroup,java.util.Map,CoordinatorMetadataImage)  ConsumerGroup.java:1253:5  no gaps
""", done.stdout)

    def test_sink_as_json(self):
        done = self.run_script(KAFKA_FEEDS, 'sink:deserializeConsumerProtocolSubscription', '--json')
        self.assertEqual((0, ''), (done.returncode, done.stderr))
        result = json.loads(done.stdout)
        self.assertEqual(['metadata()', 'wrap()'], result['all'])
        self.assertEqual([[], ['get()']], [way['feeds'] for way in result['ways']])
        self.assertEqual([{'method': 'org.apache.kafka.coordinator.group.GroupMetadataManager'
                                     '#deserializeSubscription(JoinGroupRequestProtocolCollection)',
                           'at': 'GroupMetadataManager.java:2071:5', 'gaps': 0}], result['ways'][0]['methods'])

    def test_a_document_on_stdin_and_the_gaps_of_a_method_a_call_is_fed_in(self):
        document = doc(method('a.C#one()', [('P0', 'ext-arg:f@X.java:9:9')], gaps=[gap('C#g on this', 'dispatch')]))
        done = subprocess.run([sys.executable, os.path.join(HERE, 'diverges.py'), '-', 'sink:f', '--json'],
                              input=json.dumps(document), capture_output=True, text=True)
        self.assertEqual((0, ''), (done.returncode, done.stderr))
        self.assertEqual([{'method': 'a.C#one()', 'at': 'X.java:1:1', 'gaps': 1}], json.loads(done.stdout)['ways'][0]['methods'])

    def test_an_unknown_flag_is_refused(self):
        done = self.run_script(KAFKA, 'overrides:getOrMaybeCreateMember', '--yaml')
        self.assertNotEqual(0, done.returncode)
        self.assertEqual('', done.stdout)


if __name__ == '__main__':
    unittest.main()
