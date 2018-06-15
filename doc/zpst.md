# zpst

Show a stack trace with source code (and possibly argument values) 
interleaved within the existing stack trace.  It can be used in two
different contexts:

 * after any exception -- will print all of the source it can find from the
 functions that are in the stack backtrace
 * after running `(collect)`, recreating the exception, and running `(analyze)`
 it will show the same information (i.e., the source of the information on
 the stack) and argument values for most of the functions in the backtrace

It will use the current exception, or the database of information supplied
by the `(collect)`, reproduce, `(analyze)` workflow if it exists.

It has several possible calling sequences:

```clojure
(zpst <depth>)
(zpst <exception>)
(zpst <options>)
(zpst <depth> <options>)
(zpst <exception> <options>)
(zpst <exception> <depth> <options>)
```
If an exception is not supplied, it will use `*e`.

# zpst configuration

## What to show?

#### :args? <text style="color:#A4A4A4;"><small>true</small></text>

If argument values are available, because `(collect)` and `(analyze)`
have been called to gather the argument information, then show the
argument values.
`
#### :call? <text style="color:#A4A4A4;"><small>true</small></text>

Show the source of the call to the function above this function on
the stack backtrace.

#### :depth <text style="color:#A4A4A4;"><small>20</small></text>

Show this many of the frames on the stack.

#### :docstring? <text style="color:#A4A4A4;"><small>true</small></text>

When showing the source of a function, include the docstring in the
display.  This is false by default, since frequently you already know
what the function is supposed to do, and it can take up a lot of space.

#### :name? <text style="color:#A4A4A4;"><small>false</small></text>

Show the function name in the stack backtrace.

#### :params? <text style="color:#A4A4A4;"><small>true</small></text>

Show the parameters of the function when showing the source of the function.

#### :stack? <text style="color:#A4A4A4;"><small>true</small></text>

Show the stack trace element.

## How to show it

#### :color? <text style="color:#A4A4A4;"><small>true</small></text>

When using `zprint` to output source and argument values, use ANSI color
escape sequences in the output.  If you are not using this for output to
an ANSI-type terminal, configure the color off:

   `(set-options! {:zpst {:color? false}})`

## How to show the argument values

#### :zprint-args <text style="color:#A4A4A4;"><small>see below</small></text>

This is the options map that is given to zprint when printing the argument
vector.  The idea here is to output enough of the arguments to give a flavor
of what a function is being called with, but also to avoid overwhelming the 
repl with a vast quantity of data.  The value is:

   `{:max-length [20 5 2 0]`

This tells zprint to print 20 things at depth 0, 5 things at depth 1, 2 things
at depth 2, and `##` at depths 3 and greater.  This will usually give you
a pretty reasonable idea of what an argument looked like, without overwhelming
the repl with output.

#### :arg-clean-fn <text style="color:#A4A4A4;"><small>see below</small></text>

In the case where you are committed to using zpst, and the `max-length` argument
to zprint is not sufficient to "clean up" the argument vector prior to 
formatting it for output, you can supply a function which will be called with
the name of the formal parameter and the value of the actual argument. This
function must return an actual argument which will be printed.  Typically,
this will return an edited version of the argument with which it was called.

For example, here is a sample `arg-clean-fn`:
```clojure
(defn clean-value
  "Example of a function that cleans up a value for printing. Takes
  two arguments: 
    local    the name of the parameter
    value    the value of the argument which is bound to that parameter 
             when the functionis called.  
  Returns a cleaned up argument if necessary.  This example gives
  a hint of a zipper (since zippers can be very large), and works
  to avoid showing an entire zprint options map.  Other than that,
  it simply passes the argument value through."
  [local value]
  (cond (zprint.core/zipper? value)
          (str "zipper: " (beginning-str 70 (zprint.zutil/string value)))
        (= local 'options) (str "<options-map>")
        (= (:as local) 'options) (str "<options-map>")
        :else value))
```

#### :arg-vec-options <text style="color:#A4A4A4;"><small>see below</small></text>

Sometimes you might not want to actually get called for every argument,
but you want to influence the options map that is used by `zprint` when
formatting output for the argument binding vector.  In this case, you can 
supply a function which will be called with the entire argument binding
vector, and you can have this function craft a reasonable `zprint` options
map.  Here is a function which simply return the standard `zprint` options
for printing the binding vector:

```clojure
(defn zprint-options
  "Input is a binding vector with params and actual arguments together in
  a vector.  Output is a map of zprint options to use to print the binding 
  vector, or nil for no change to existing options."
  [binding-vec]
  {:max-length [20 5 2 0]})

```
## How to show the source

#### :zprint-source <text style="color:#A4A4A4;"><small>see below</small></text>

When showing the source, the following is the default options map given
to `zprint`:

```clojure
{:output {:elide "[...]", :focus {:surround [8 2]}, :lines [0]}}}})
```

The meaning of these items are as follows:

##### :elide <text style="color:#A4A4A4;"><small>"[...]"</small></text>

A string to use when leaving out some of the source of a function.

##### :focus {:surround [before after]} <text style="color:#A4A4A4;"><small>"[8 2]"</small></text>

Show `before` number of lines before the source, and `after` number of lines
after.  You can change this by using `set-options`,
for example: `(set-options! {:zpst {:output :focus {:surround [8 3]}}}}` would
place 3 lines after the call to the next function.

