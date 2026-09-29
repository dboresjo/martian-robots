import java.lang.System.{err, exit}
import scala.annotation.tailrec

/**
 * Rejects malformed input. The entry point turns this into an error message and a non-zero exit.
 * The return type Nothing means "never returns", so a call can stand in for a value of any type.
 */
def reject(message: String): Nothing = throw IllegalArgumentException(message)

/** An integer 2-vector: a grid position, or a unit heading. */
case class Vec(x: Int, y: Int):
  def +(v: Vec): Vec = Vec(x + v.x, y + v.y)

/** The four compass headings as unit vectors, their symbols in the input and output, and the two turns. */
object Heading:
  val direction: Map[String, Vec] = Map(
    "N" -> Vec(0, 1),
    "E" -> Vec(1, 0),
    "S" -> Vec(0, -1),
    "W" -> Vec(-1, 0))

  val symbol: Map[Vec, String] = direction.map(_.swap)   // the inverse map, for output

  /** Makes `Heading(facing)` usable as a pattern: it matches one of the four symbols and binds its vector. */
  def unapply(name: String): Option[Vec] = direction.get(name)

  /** Transformation functions - turning the robot by transforming it's heading vector.  */
  val left: Vec => Vec  = v => Vec(-v.y, v.x)   // rotate 90° anticlockwise
  val right: Vec => Vec = v => Vec(v.y, -v.x)   // rotate 90° clockwise

/** A robot's pose: where it is and which way it faces. */
case class Robot(location: Vec, facing: Vec):
  def nextLocation: Vec               = location + facing
  def forward: Robot                  = copy(location = nextLocation)
  def turn(rotate: Vec => Vec): Robot = copy(facing = rotate(facing))

  override def toString: String = s"${location.x} ${location.y} ${Heading.symbol(facing)}"

/** A robot that fell off the grid, with the last pose it had on it. */
case class Lost(robot: Robot)

/** Mars - a terrain for our robots to traverse */
case class Mars(xAxis: Range, yAxis: Range):

  /** determines if Mars contains the specified point */
  def contains(v: Vec): Boolean = xAxis.contains(v.x) && yAxis.contains(v.y)

  /**
   * Apply an instruction to a robot, returning its new pose, or None if it fell off the grid.
   * A losing instruction never moves the robot, so the caller still holds its last on-grid pose.
   * Additional instruction types are added as cases.
   */
  def step(scents: Set[Vec], robot: Robot, instruction: Char): Option[Robot] =
    instruction match
      case 'L'                                    => Some(robot.turn(Heading.left))
      case 'R'                                    => Some(robot.turn(Heading.right))
      case 'F' if contains(robot.nextLocation)    => Some(robot.forward)
      case 'F' if scents.contains(robot.location) => Some(robot)   // scented point: the move is ignored
      case 'F'                                    => None          // falls off the grid
      case other => reject(s"unknown instruction '$other'")

  /** Run one robot: the robot after its last instruction, or Lost with its last pose if it fell off first. */
  def execute(scents: Set[Vec], robot: Robot, instructions: String): Robot | Lost =
    if !contains(robot.location) then reject(s"robot '$robot' starts outside the grid")
    // @tailrec makes the compiler check that this recursion compiles to a loop.
    @tailrec def loop(robot: Robot, remaining: List[Char]): Robot | Lost =
      remaining match
        case instruction :: rest =>   // the next instruction and the rest of the list
          step(scents, robot, instruction) match
            case Some(moved) => loop(moved, rest)
            case None        => Lost(robot)
        case Nil => robot   // no instructions left
    loop(robot, instructions.toList)

/** Reads the program, runs each robot in turn and reports its final position. */
object MissionControl:
  /**
   * Run a program in the input text format, one output line per robot.
   * Streams: each robot is read, run and emitted before the next is read. The only state carried
   * from one robot to the next is the set of scents, which grows by one point per lost robot.
   */
  def run(lines: Iterator[String]): Iterator[String] =
    val input    = lines.map(_.trim).filter(_.nonEmpty)
    val gridLine = input.nextOption().getOrElse(reject("input is empty; expected the grid size on the first line"))
    val mars = gridLine.split("\\s+") match
      case Array(AsInt(maxX), AsInt(maxY)) if maxX >= 0 && maxY >= 0 => Mars(0 to maxX, 0 to maxY)
      case _ => reject(s"expected '<maxX> <maxY>' for the grid, got '$gridLine'")
    // Robots come as pairs of lines. scanLeft is a fold that emits each intermediate accumulator,
    // lazily: the accumulator is (scents so far, the output line for the robot just run).
    input.grouped(2).scanLeft((Set.empty[Vec], "")) {
      case ((scents, _), Seq(position, instructions)) =>
        val start = position.split("\\s+") match
          case Array(AsInt(x), AsInt(y), Heading(facing)) => Robot(Vec(x, y), facing)
          case _ => reject(s"expected '<x> <y> <N|E|S|W>' for a robot, got '$position'")
        mars.execute(scents, start, instructions) match
          case robot: Robot => (scents, robot.toString)
          case Lost(robot)  => (scents + robot.location, s"$robot LOST")
      case (_, other) => reject(s"robot '${other.mkString(" ")}' has no instruction line")
    }.drop(1).map(_._2)   // skip the seed; keep the output line

  /** Makes `AsInt(n)` usable as a pattern: it matches a string that parses as an integer and binds the value. */
  private object AsInt:
    def unapply(s: String): Option[Int] = s.toIntOption

/** Streams a program from stdin to stdout, one line per robot. Rejected input exits 1 with a message on stderr. */
@main def main(): Unit =
  try MissionControl.run(io.Source.stdin.getLines()).foreach(println)
  catch
    case e: IllegalArgumentException =>
      err.println(s"error: malformed input (${e.getMessage})")
      exit(1)
