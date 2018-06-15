# Configuring zpst

There are several capabilities in zpst that can be configured
with an options map, and which will accept an options map when
called.  All of these options maps are actually individual sub-maps
of a single default options map.

You can see the current defaults options map with `(zpst :explain)`:

```clojure
(zpst :explain)

{:analyze {},
 :collect {:hook-fns nil, :ns [zprint.zfns]},
 :commands
   {:arg-clean-fn nil,
    :arg-vec-options-fn #'zpst.clean/zprint-options,
    :args? true,
    :call? true,
    :color? true,
    :docstring? false,
    :frame? false,
    :name? true,
    :params? true,
    :stack? true,
    :zprint-args {:max-length [20 5 2 0]},
    :zprint-source {:output
                      {:elide "[...]", :focus {:surround [8 2]}, :lines [0]}}},
 :epst
   {:dbg? false, :elide-lines-with #{"apply" "doInvoke" "hook"}, :elide? true},
 :version "zpst-0.1.1",
 :zpst
   {:arg-clean-fn nil,
    :arg-vec-options-fn #'zpst.clean/zprint-options,
    :args? true,
    :call? true,
    :color? true,
    :depth 20,
    :docstring? false,
    :frame? true,
    :name? true,
    :params? true,
    :stack? true,
    :zprint-args {:max-length [20 5 2 0]},
    :zprint-source {:output
                      {:elide "[...]", :focus {:surround [8 2]}, :lines [0]}}}}
```
This options map can be altered in two different ways:

### .zpstrc

The first time a zpst function that uses a value from the options map is called,
the file `~/.zpstrc` is examined, and if it contains an options map, that
map is integrated into the default zpst options map.

### set-options!

You can change the default options map with:

```clojure
(set-options! <options-map>)
```
## Configuration Options:

The detailed options are explained in these places:

  * [zpst](zpst.md#zpst_configuration "")
  * [commands](commands.md#Command_configuration "")
  * [collect](collectanalyze.md#Collect "")
  * [epst](epst.md#Analyze "")

The options for all of these are all contained in the single
options map.  In either the options map in `~/.zpstrc` or
the `<options-map>` in `(set-options! <options-map>)`, the
individual options maps for each of these capabilities are 
contains in the a key with the equivalent name:

```clojure
{:zpst {}
 :commands {}
 :collect {}
 :epst {})
```
Of course, you don't have to specify any information in the
options map that you don't want to change.

To recap -- when adding an options map to a function, you would
use just the options map for that function -- for example, `collect` or
`zpst` or `commands`.  When configuring options using `~/.zpstrc` or `(set-options! <options-map>)`, you would place any options for, say `commands` in a map which is the value of key `:commands`.

