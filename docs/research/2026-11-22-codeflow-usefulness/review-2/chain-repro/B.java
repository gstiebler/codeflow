// Thing and Dto are not in the corpus. The first three methods lose P0 -> RET; the last two keep it.
class B {
    Object getterChain(Thing t) {
        return t.a().b();
    }

    Object fieldThenCall(Thing t) {
        return t.inner.b();
    }

    Object argInChain(Thing t, Object d) {
        return t.a(d).b();
    }

    Object split(Thing t) {
        Dto x = t.a();
        return x.b();
    }

    Object known(String s) {
        return s.trim().length();
    }
}
