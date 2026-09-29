# Martian Robots

Robots move on a bounded rectangular grid following `L`, `R` and `F` instructions. A robot that
moves off the grid is lost, but leaves a 'scent' at its last position that stops later robots being
lost from the same point.

Scala 3, built with sbt. The program reads stdin and writes stdout, like a Unix filter.

## Requirements

- JDK 17 or later
- [sbt](https://www.scala-sbt.org/) 1.x (`brew install sbt`, `sdk install sbt`, or coursier's `cs setup`)

sbt downloads Scala and the test library on first run, so the first build takes a minute or two.

## Build and run

From sbt:

```sh
sbt -error run < samples/sample.txt
```

`-error` suppresses sbt's info-level log lines; on a failed run sbt still prints its own `[error]`
lines to stdout. Do not use `-batch`; it disconnects stdin.

To run without sbt, build the jar once:

```sh
sbt assembly
java -jar target/scala-3.3.6/martian-robots.jar < samples/sample.txt
```

```
1 1 E
3 3 N LOST
2 3 S
```

Each robot's result is written as soon as that robot has been processed, so the program can be used
in a pipeline. Malformed input produces an error on stderr and exit code 1. Robots before the bad
line will already have been written.

## Tests

```sh
sbt test
```

The tests use ScalaTest. IntelliJ can run them directly.

## Design

One source file, `MartianRobots.scala`, about 110 lines including comments. It is in the default
package because nothing imports it.

- `Vec`: an integer 2-vector with `+`, used for both locations and headings. Moving forward is
  `location + facing`.
- `Heading`: a map from symbol to unit vector (`"N" -> (0, 1)` and so on), its inverse for output, an
  extractor so that a symbol can be pattern matched as `Heading(facing)`, and the two turns `left`
  and `right` as `Vec => Vec` functions (90° rotations, `(-y, x)` and `(y, -x)`).
- `Robot`: location and heading, with `forward` and `turn(rotation)`. `toString` produces the output
  line, without the `LOST` suffix.
- `Mars`: the valid coordinate range on each axis (`0 to maxX`, `0 to maxY`).
  `step(scents, robot, instruction)` applies one instruction and returns `Some(robot)`, or `None` if
  the robot has fallen off the grid. `execute` applies an instruction string with a tail-recursive
  loop and returns `Robot | Lost`: the robot if it completed its instructions, or `Lost(robot)` with
  its last position if it fell off first. Neither mutates anything. A lost robot is a separate outcome
  rather than a flag on `Robot`.
- Scents are held separately from `Mars` because they do not change during a robot's run: a robot
  adds a scent only by being lost, which ends its run. `step` and `execute` take the scent set as a
  read-only parameter, and it is updated between robots.
- `MissionControl.run`: takes an iterator of input lines and returns an iterator of output lines. A
  `scanLeft` over the robot pairs carries the scent set from each robot to the next. Each output line
  is produced before the next robot is read, and only the scent set is held between robots.
- `main`: a top-level `@main` entry point connecting stdin and stdout to `MissionControl.run`.
  Malformed input is reported through `reject`, which throws an `IllegalArgumentException`; `main`
  prints the message and exits with code 1. Any other exception is a bug and is left to produce a
  stack trace.

We anticipate that further instruction types may be required. A new instruction is a new case in
`Mars.step`. This assumes instructions remain single characters. An instruction with an argument
(for example `F3`) would require the instruction string to be tokenised before the loop in `execute`
and `step` to match on tokens; the rest of the model would not change.

## Reading the Scala

For readers coming from other languages:

- Blocks are indented rather than braced; a colon at the end of a line opens one. `match` is an
  expression whose arms start with `case` and may carry an `if` guard.
- `case class Vec(x: Int, y: Int)` is Kotlin's `data class`, or a Rust struct deriving `Clone`,
  `PartialEq` and `Debug`: compared by value, copied with `copy(field = ...)`, usable in patterns.
- `object Heading` is a singleton used as a namespace, like Kotlin's `object` or a Rust module.
  `def +(v: Vec)` defines an operator, like `operator fun plus` or `impl Add`.
- `Vec => Vec` is a function type, `(Vec) -> Vec` or `(v: Vec) => Vec`. `v => ...` is a lambda, and
  `_.swap` is a lambda whose single parameter is written `_`, like Kotlin's `it`.
- `Option`, `Some` and `None` are Rust's `Option`. `Robot | Lost` is a union type, as in TypeScript;
  `case robot: Robot =>` matches on the runtime type. `Nothing` is Rust's `!` or TypeScript's `never`.
- An `object` with an `unapply` method can be used as a pattern. `Heading(facing)` matches a string
  that is one of `N`, `E`, `S`, `W` and binds its vector; `AsInt(n)` matches a string that parses as
  an integer. `Array(a, b)` and `Seq(a, b)` match exactly two elements. `x :: rest` matches a
  non-empty list, binding its first element and the remainder (Rust's `[x, rest @ ..]`); `Nil` is
  the empty list.
- `@tailrec` asks the compiler to verify that a recursive call is in tail position and to compile it
  to a loop, like Kotlin's `tailrec`.
- `0 to maxX` is an inclusive range: `0..maxX` in Kotlin, `0..=maxX` in Rust.
- Collections are immutable by default: `scents + location` returns a new set. `grouped(2)` is
  `chunked(2)` or `chunks(2)`. `scanLeft` is a fold that emits every intermediate accumulator, like
  Kotlin's `runningFold`; on an `Iterator` it is lazy, which is what makes `run` stream. `_1` and
  `_2` are tuple fields, like Rust's `.0` and `.1`.
- `s"... $x ..."` is string interpolation. A top-level `def` needs no enclosing class, and `@main`
  marks the entry point.
- In the tests, `"A robot" should "..." in { ... }` names a subject and a behaviour, `shouldBe` is an
  equality assertion, and `Table` with `forAll` runs the same assertion over each row.

## Assumptions

- Scent is per grid point, not per direction. A move off the world 'from a grid point' with a scent
  is ignored, so a scent at a corner covers both outward moves.
- Bounds are inclusive: `5 3` is a 6 × 4 grid of points.
- A lost robot's remaining instructions are discarded, and it reports its last on-grid position
  followed by `LOST`.
- An ignored instruction leaves the robot where it is, and execution continues with the next
  instruction.
- A robot must start on the grid. Starting elsewhere is treated as bad input.
- Input is whitespace-tolerant (blank lines, CRLF, indentation) but case-sensitive, as specified.
- The stated limits (coordinates at most 50, instructions under 100 characters) are assumed rather
  than enforced. Larger values are accepted.
- A robot with no instructions cannot be expressed. Blank lines are skipped so that input with blank
  lines between robots works, which makes an empty instruction line indistinguishable from a blank
  one; the next robot's position line would be read as the instructions and rejected. Blank lines
  between robots seemed the more likely case.
- Bad input stops the run. A malformed grid or position line, an unknown instruction letter, a robot
  without an instruction line and a robot starting off the grid are all rejected with a message
  quoting the offending input. Because the input is streamed, robots before the bad one will already
  have been reported.

## With more time

- A native binary (Scala Native or GraalVM native-image) if JVM start-up time (about 0.3 s) mattered.
  The code has no JVM-specific dependencies.

## Tooling

Scala 3.3.6, sbt 1.10, sbt-assembly 2.3.1, ScalaTest 3.2.19. AI assistance (Claude Code) was used for
drafting and review.
