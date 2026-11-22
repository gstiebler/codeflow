/*
 * A call chained on a type that is nowhere in the analysed sources: `widget.frame().width()`.
 *
 * javac marks everything on an error type the same way - an `Element` of kind `CLASS`, typed
 * `ERROR` - whether it is the type's own name, a call on it, a field of it or a `new` of it. The
 * receiver check asked for the kind and nothing else, so `widget.frame()` was taken for a type name
 * the way `Math` is in `Math.abs(x)`: never evaluated, nothing flowing into `width`, no gap counted.
 * A chain on a library type is most of a real method body, and each one lost everything before its
 * last call.
 *
 * Only a name can name a type. `Widget.create(size)` and its fully qualified spelling are here
 * beside the chains because they are what has to stay as it was: a type name there, and no value.
 */
package unresolvedChain;

import com.example.missing.Widget;

public class App {
    public static void main(String[] args) {
        int size = 5;
        Widget widget = Widget.create(size);
        Widget other = com.example.missing.Widget.create(size);
        int viaCall = widget.frame().width();
        int viaField = widget.panel.height();
        int viaNew = new Widget(size).border().depth();
        int viaParens = (widget.edge()).length();
    }
}
