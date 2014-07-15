package prisoners_dilemma

import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import Package._
import scala.concurrent.duration._

trait TestPlayer {
  def name: Name
  def strategy: RoundStrategy

  def alwaysCooperates: Boolean
  def startsFriendly: Boolean
  def isTitForTat: Boolean = false

  val player = Player(name, new Strategizer {
    def newGame() = strategy
  })

}

sealed trait Instructions
case object MakeAMove extends Instructions
case object Failinate extends Instructions
case class Wait(d: Duration) extends Instructions

case class SlowStrategy(inner: RoundStrategy, instructions: Stream[Instructions]) extends RoundStrategy
{
  private val doThese = instructions.iterator
   val currentMove = inner.currentMove
   def next(m: Move) = {
     doThese.next() match {
       case MakeAMove => inner.next(m)
       case Failinate => throw new Exception("bird poop")
       case Wait(d) => Thread.sleep(d.toMillis); next(m)
     }
   }
}

case class SlowTestPlayer(wrapped: TestPlayer,
  newPlayerInstructions: Seq[Instructions],
  birdInstructions: Seq[Seq[Instructions]]) extends TestPlayer {
  def name: Name = wrapped.name
  def strategy = ???
  def alwaysCooperates = wrapped.alwaysCooperates
  def startsFriendly = wrapped.startsFriendly
  override def isTitForTat = wrapped.isTitForTat

  private[this] def nextBird() = this.synchronized {birdIter.next() }

  override val player = Player(name, new Strategizer {
    def newGame() = nextBird()()
  })

  // I'm being strange here to make this thread-safe. Have a better idea?
  var birdIter: Iterator[() => SlowStrategy] = null

  def initThisManyBirds(numBirds:Int) = {
    // TODO: use instructions to make this sometimes slow
    val birdInstructionStream = Stream.continually(birdInstructions.toStream).flatten
    val requestedBirds = for {
      i <- 1 to numBirds
      instrs <- birdInstructionStream
    } yield {
      val p = SlowStrategy(wrapped.strategy, Stream.continually(instrs.toStream).flatten)
      () => p
    }
    birdIter = requestedBirds.iterator

  }



}


case class ConstantTestPlayer(name: Name, move: Move) extends TestPlayer {
  def alwaysCooperates = move == Cooperate
  def startsFriendly = alwaysCooperates
  def strategy = new RoundStrategy {
     val currentMove = move
     def next(x: Move) = this

     override def toString = s"always $move"
  }
}

case class ConsistentTestPlayer(
  name: Name,
  firstMove: Move,
  moveFunc: Move => Move) extends TestPlayer {
  def startsFriendly = firstMove == Cooperate
  def alwaysCooperates = startsFriendly &&
    moveFunc(Cooperate) == Cooperate &&
    moveFunc(Defect) == Cooperate
  override def isTitForTat = startsFriendly &&
    moveFunc(Cooperate) == Cooperate &&
    moveFunc(Defect) == Defect

  private def nextStrategy(nextMove: Move):RoundStrategy =
    new RoundStrategy {
    val currentMove = nextMove
    def next(o: Move) = nextStrategy(moveFunc(o))
  }

  def strategy = nextStrategy(firstMove)
}


object TestPlayer {
  val nameGen = NameGenerator.pronounceableStr
  val constantMovePlayer: Gen[TestPlayer] =
    for {
      name <- nameGen
      move <- Arbitrary.arbitrary[Move]
    } yield ConstantTestPlayer(name, move)

  val consistentMovePlayer: Gen[TestPlayer] =
    for {
      name <- nameGen
      firstMove <- Arbitrary.arbitrary[Move]
      moveFunc <- Arbitrary.arbitrary[Move => Move]
    } yield ConsistentTestPlayer(name, firstMove, moveFunc)

  val someStandardPlayer: Gen[TestPlayer] =
    Gen.oneOf(constantMovePlayer, consistentMovePlayer)

  val someStandardPlayers: Gen[Seq[TestPlayer]] =
    someOf(Gen.choose(3, 20), someStandardPlayer)


}
