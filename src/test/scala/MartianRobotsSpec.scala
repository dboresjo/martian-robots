import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import Heading.{direction, left, right}

class MartianRobotsSpec extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks:

  val N = direction("N")
  val E = direction("E")
  val S = direction("S")
  val W = direction("W")

  val mars     = Mars(0 to 5, 0 to 3)
  val headings = List(N, E, S, W)
  val noScents = Set.empty[Vec]

  private def run(input: String): List[String] = MissionControl.run(input.linesIterator).toList

  "A heading" should "rotate anticlockwise on left and clockwise on right" in {
    left(N) shouldBe W
    left(W) shouldBe S
    left(S) shouldBe E
    left(E) shouldBe N
    headings.foreach(h => right(left(h)) shouldBe h)
  }

  it should "return to itself after four turns either way" in {
    headings.foreach { h =>
      Function.chain(Seq.fill(4)(left))(h) shouldBe h
      Function.chain(Seq.fill(4)(right))(h) shouldBe h
    }
  }

  "A robot" should "move one point in its heading on F" in {
    val moves = Table(
      ("facing", "destination"),
      (N, Vec(2, 3)),
      (S, Vec(2, 1)),
      (E, Vec(3, 2)),
      (W, Vec(1, 2)))
    forAll(moves) { (facing, destination) =>
      mars.step(noScents, Robot(Vec(2, 2), facing), 'F') shouldBe Some(Robot(destination, facing))
    }
  }

  it should "fall off an unscented edge, but stay put on a scented one" in {
    mars.step(noScents, Robot(Vec(5, 3), N), 'F') shouldBe None
    mars.step(Set(Vec(5, 3)), Robot(Vec(5, 3), N), 'F') shouldBe Some(Robot(Vec(5, 3), N))
  }

  it should "reach the inclusive edge and turn there safely" in {
    mars.execute(noScents, Robot(Vec(4, 3), E), "F") shouldBe Robot(Vec(5, 3), E)
    mars.execute(noScents, Robot(Vec(0, 0), S), "LLRR") shouldBe Robot(Vec(0, 0), S)
  }

  it should "be Lost at its last pose, with the rest of its instructions discarded" in {
    mars.execute(noScents, Robot(Vec(5, 3), N), "FLFF") shouldBe Lost(Robot(Vec(5, 3), N))
  }

  "A scent" should "make the losing move a no-op in any direction, and let the robot carry on" in {
    val scents = Set(Vec(5, 3))
    mars.execute(scents, Robot(Vec(5, 3), N), "FLF") shouldBe Robot(Vec(4, 3), W)
    mars.execute(scents, Robot(Vec(5, 3), E), "F") shouldBe Robot(Vec(5, 3), E)
  }

  it should "not protect a different point on the same edge" in {
    mars.execute(Set(Vec(5, 3)), Robot(Vec(4, 3), N), "F") shouldBe Lost(Robot(Vec(4, 3), N))
  }

  "Mission control" should "reproduce the sample from the brief" in {
    val input =
      """5 3
        |1 1 E
        |RFRFRFRF
        |3 2 N
        |FRRFLLFFRRFLL
        |0 3 W
        |LLFFFLFLFL
        |""".stripMargin
    run(input) shouldBe List("1 1 E", "3 3 N LOST", "2 3 S")
  }

  it should "pass a lost robot's scent on to the robots after it, even on a 0 0 grid" in {
    run("0 0\n0 0 N\nF\n0 0 W\nFRF\n") shouldBe List("0 0 N LOST", "0 0 N")
  }

  it should "tolerate blank lines, CRLF and indentation" in {
    run("  5 3 \r\n\r\n1 1 E\r\n\tRFRF\r\n\r\n") shouldBe List("0 0 W")
  }

  it should "emit each robot's line before reading the next robot" in {
    val output = MissionControl.run(Iterator("5 3", "1 1 E", "F", "0 0 N"))
    output.next() shouldBe "2 1 E"
    // The incomplete trailing robot only fails when it is pulled.
    an[IllegalArgumentException] should be thrownBy output.next()
  }

  it should "reject malformed input with a message that quotes the offending input" in {
    val rejections = Table(
      ("input", "message"),
      ("", "input is empty; expected the grid size on the first line"),
      ("5\n", "expected '<maxX> <maxY>' for the grid, got '5'"),
      ("5 3 1\n", "expected '<maxX> <maxY>' for the grid, got '5 3 1'"),
      ("5 -3\n", "expected '<maxX> <maxY>' for the grid, got '5 -3'"),
      ("a b\n", "expected '<maxX> <maxY>' for the grid, got 'a b'"),
      ("5 3\n1 E\nF\n", "expected '<x> <y> <N|E|S|W>' for a robot, got '1 E'"),
      ("5 3\n1 1 E extra\nF\n", "expected '<x> <y> <N|E|S|W>' for a robot, got '1 1 E extra'"),
      ("5 3\n1 1 Q\nF\n", "expected '<x> <y> <N|E|S|W>' for a robot, got '1 1 Q'"),
      ("5 3\n1 1 EE\nF\n", "expected '<x> <y> <N|E|S|W>' for a robot, got '1 1 EE'"),
      ("5 3\nx 1 E\nF\n", "expected '<x> <y> <N|E|S|W>' for a robot, got 'x 1 E'"),
      ("5 3\n9 9 N\nF\n", "robot '9 9 N' starts outside the grid"),
      ("5 3\n1 1 E\nRFXF\n", "unknown instruction 'X'"),
      ("5 3\n1 1 E\n", "robot '1 1 E' has no instruction line"))
    forAll(rejections) { (input, message) =>
      the[IllegalArgumentException] thrownBy run(input) should have message message
    }
  }
