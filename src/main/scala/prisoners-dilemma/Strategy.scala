package prisoners_dilemma

trait Strategizer {
  def newGame(): RoundStrategy
}

trait RoundStrategy {
  val currentMove: Move
  def next(opponentMove: Move): Strategy

  override def toString: String = s"Next I will $currentMove."
}


class RemoteStrategy(context:ActorContext, firstMove: URI)
extends Strategizer {

  def newGame() = {
     // call first URI
     val (move, uri) = parseResponse("la la ala la ala")
     new RemoteRoundStrategy(context, move, uri)
  }

  private def parseResponse(response: String /* what am I */): (Move, URI) = ???

 private class RemoteRoundStrategy(context:ActorContext, myMove: Move, nextURI: URI) extends Strategy {
  val currentMove = myMove
  def next(m: Move) = {
    // call URI
    val (nextMove, laterURI) = parseResponse("bananas")
    new RemoteRoundStrategy(context, nextMove, laterURI)
  }
 }

}

object Strategy {
  def moves(p1: Strategy, p2: Strategy): Stream[MoveSet] =
    (p1.currentMove, p2.currentMove) #:: moves(p1.next(p2.currentMove), p2.next(p1.currentMove))

  class HistoricalRecord(wrapped: Strategy, history: Seq[Move]) extends Strategy {
    val currentMove = wrapped.currentMove
    def next(m: Move) = new HistoricalRecord(wrapped.next(m), history :+ currentMove)

    override def toString = s"About to $currentMove. From beginning of time: $history"
  }

  def recording(s: Strategy):Strategy = new HistoricalRecord(s, Seq())

  def fromStream(moves: Stream[Move]): Strategy = moves match {
    case head #:: tail => new Strategy {
      val currentMove = head
      def next(x: Move) = fromStream(tail)
    }
  }

  val sucker: Strategy = new Strategy {
     val currentMove = Cooperate
     def next(x: Move) = this

     override def toString = "Sucker!!"
  }

  def chooseBasedOnTheirMove(myMove: Move, choice: Move => Move):Strategy =
    new Strategy {
      val currentMove = myMove
      def next(theirMove: Move) =
        chooseBasedOnTheirMove(choice(theirMove), choice)
    }

  val titForTat: Strategy = chooseBasedOnTheirMove(Cooperate, (m) => m)
}
