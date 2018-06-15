# Commands

After having done a `(collect)`, reproduced the exception, and used
`(analyze)`, zpst creates an internal database of the stack backtrace
from the exception along with the arguments and environment from each
stack backtrace element for which it can find the source.

There are several commands that can be used to query and alter this database:

## Examining frames

### set-frame!

The `set-frame!` command lets you specify a specific frame for other
commands to operate on.  In the absence of specific direction, all of 
the commands will operate on the current frame set by `set-frame!`.

```clojure
(set-frame! <frame-number>)
```

### show

The most frequently used command is show:

```clojure
(show)
(show <things-to-show> <frame-index> <options>)
(show <things-to-show> <start-frame> <end-frame> <options>)
(show <things-to-show> :all <options>)
```

The `show` command will show one or more frames of the stack, in
a way similar to `zpst`.  It allows you to examine just one or a small
number of frames, giving more control than `zpst`, which only allows
you to specify the depth.

All of `<things-to-show>`, `<start-frame>`, `<end-frame>`, `<frame-index>`,
`:all`, and `<options>` are optional.

#### What frames?

If you don't specify a frame, the most recent frame specified to
`(set-frame! <frame-index>)` will be used.

`<start-frame>`, `<end-frame>`, and `<frame-index>` are all positive integers.

##### Single Frame

If you specify a single integer `<frame-index>`, it will be the frame to show.

##### Range of Frames

If you specify a `<start-frame>` and `<end-frame>`, it will show 
the frames from start to end inclusive.

You can specify `:all`, and it will show all of the frames  `:all` cannot
be mixed with any other frame specification.

#### options

`<options>` is described below.

#### things-to-show 

`<things-to-show>` is one or more of the following keywords.  If any
of these keywords appear, then only what is specified by the keywords
will be shows.  If none of the keywords appear, it is as if the
following were used (i.e., this is the default): 

```clojure
    (show :args :call :name :params :stack)
```

##### :args 

If argument values are available, because `(collect)` and `(analyze)`
have been called to gather the argument information, then show the
argument values.

##### :call

Show the source of the call to the function above this function on
the stack backtrace.

##### :docstring

When showing the source of a function, include the docstring in the
display.  This is false by default, since frequently you already know
what the function is supposed to do, and it can take up a lot of space.

##### :name

Show the function name in the stack backtrace.

##### :params

Show the parameters of the function when showing the source of the function.

##### :stack

Show the stack trace element.

#### Examples

```clojure
; Show the docstring (not shown by default):
(show :args :docstring :name :params :call :stack)

; Show just the name and the args:
(show :name :args)

; Show just the stack for a few frames
(show :stack 4 7)

; Show the entire stack
(show :stack :all)
```

#### Note

The first time that you show some arguments, if it is a lazy sequence,
it may not have been forced to a value, and you might see something
like this:

```clojure
>>>>>>>>>>    20 zprint.zprint/contains-nil?
Arguments:

When printing the arguments, encountered this Exception:
ClassCastException java.lang.String cannot be cast to java.lang.Number

The locals and their types were:
[coll clojure.lang.LazySeq]
```
The second time you look at it, it won't do this.  In this case
there was a thread transition from one thread to another, causing
this situation.

 
## Argument examination and manipulation

### get-arg

You can get access to any argument of the current frame with `get-arg`.
It will take a variety of inputs:

```clojure
(get-arg <zero-based-argument-number>)
(get-arg <argument-name-string>)
(get-arg <quoted-argument-symbol>)
```

`get-arg` returns the actual argument data structure.

### set-arg!

You can change the value of an argument with `set-arg!`.  You would only do
this if you were indenting to use `(re-eval)` to call down into the functions
below the current frame to see how they would react to a different argument.
```clojure
(set-arg! <zero-based-argument-number> <new-value>)
(set-arg <argument-name-string> <new-value>)
(set-arg <quoted-argument-symbol> <new-value>)
```
### restore-arg!

If you wish to change an argument back to whatever it was originally, prior
to any `set-arg!` calls, you can use `restore-arg!`:
```clojure
(restore-arg <zero-based-argument-number>)
(restore-arg <argument-name-string>)
(restore-arg <quoted-argument-symbol>)
```
### clear

The `clear` command will release all of the data held in the zpst
internal database (built during the `collect` and `analyze` phases
of zpst operation).  This database contains more (much more) than just
arguments to functions -- it contains considerable environment information
for each frame.  It may be necessary to release it at times so as to
free up the working memory.

The `(clear)` command will release all of the information in the database.

## Re-evaluation of functions

You can reproduce the call down into the functions on the stack backtrace
from almost any frame with `re-eval`:
```clojure
(re-eval)
```
It takes no arguments, and uses whatever arguments are current for this
frame to call the function that was called from this frame before.

In the simple case, this is pretty straightforward, and if you don't
change the arguments with `set-arg!` or change the code, it should produce the same exception
as before.  There are some situations where re-evaluation is not feasible,
but usually you can move one frame up or down the stack and it will work.

You might change the arguments at some frame, and then `(re-eval)`
from there to see if the exception still occurs.  You might also change
the code, and try again to see if the exception still occurs. 

If the argument change or a code change causes the exception to "go
away", and the function returns a value, the value is saved.  It
will be returned as the value of `(get-re-eval-return)`.

### What about the environment?

There are two important things other than the arguments to a function that
can change the code flow -- redefinitions of top level vars, and thread-local
bindings.  zpst attempts to collect this information for every frame, in order
to allow `(re-eval)` to work.

#### Thread Local Bindings

zpst will collect all thread-local bindings for every frame without any
action by the user.

#### Redefinitions of vars

In order to have redefinitions of vars collected, you need to tell `collect`
what namespaces you want collected.  You don't have to specify individual
vars, just namespaces, and zpst will collect all of the values of all of 
the vars in every specified namespace.  It will then redefine all of these
prior to executing `(re-eval)`.

See `(collect)` for how these namespaces are specified.

# Options

This section describes the `<options-map>` recognized by the 
`show` command.


## What to show?

WHen using the `(show)` command, these option values configure the
default for what is shown.  You can change the default values with
`(set-options! <options-map>)`, or with `~/.zpstrc`.  Or you can
specify different defaults with an `<options-map>` on the `show`
command.  Typically, you would use the keywords in `<things-to-show>`
to specify what to show on a single command.

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

This is the options map that is given to zprint when printing the
argument vector.  The idea here is to output enough of the arguments
to give a flavor of what a function is being called with, but also
to avoid overwhelming the repl with a vast quantity of data.  The
value is:

   `{:max-length [20 5 2 0]`

This tells zprint to print 20 things at depth 0, 5 things at depth
1, 2 things at depth 2, and `##` at depths 3 and greater.  This
will usually give you a pretty reasonable idea of what an argument
looked like, without overwhelming the repl with output.

#### :arg-clean-fn <text style="color:#A4A4A4;"><small>see below</small></text>

In the case where you are committed to using zpst, and the `max-length`
argument to zprint is not sufficient to "clean up" the argument
vector prior to formatting it for output, you can supply a function
which will be called with the name of the formal parameter and the
value of the actual argument. This function must return an actual
argument which will be printed.  Typically, this will return an
edited version of the argument with which it was called.

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

Show `before` number of lines before the source, and `after` number
of lines after.  You can change this by using `set-options`, for
example: `(set-options! {:commands {:output :focus {:surround [8
3]}}}}` would place 3 lines after the call to the next function.

