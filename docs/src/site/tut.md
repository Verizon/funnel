---
layout: default
title:  "Tut"
section: "home"
---

## Tut

Tut gives us the ability to "compile" our documentation against code
in the other subprojects.  Code in "tut sheds" like this:

    ```tut
    println("hello world")
    List(1,2,3).sum
    ```

Tut compiles all the documentation, replacing the tut sheds with
scala sheds that shows running the original context in a repl
session:
 
    ```scala
    scala> println("hello world")
    hello world

    scala> List(1,2,3).sum
    res1: Int = 6
    ```

Tut uses `.md` files in `docs/src/main/tut` as its source, and outputs
compiled .md files to `docs/target/site/tut`


### Compiling the tut examples

From an sbt prompt:

    sbt> docs/makeSite

Then the directory: (/docs/target/site/tut/indoctrinate.md)[/docs/target/site/tut/indoctrinate.md]` will contain `.md` files
which were compiled by tut. In those `.md` files sections which were
written as:

        ```tut
        1 + 1
        ```

Will be rewritten as:

    ```scala
    scala> 1 + 1
    2
    ```

### Example

You can view [this example](tut/example.html) as a demonstration of using tut to document the usage of the source in the example sub-project
