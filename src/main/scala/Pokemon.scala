import scala.collection.immutable

object Pokemon extends App {

  def readResource(name: String): String = {
    val resource = getClass.getResourceAsStream(name)
    try {
      io.Source.fromInputStream(resource).mkString
    } finally {
      resource.close()
    }
  }

  lazy val allPokemon: immutable.Set[String] = {
    import spray.json._

    val pokemonArray = readResource("pokemon.json").parseJson
    pokemonArray match {
      case JsArray(elements) => elements.map {
        case JsString(value) => value
        case other => throw new IllegalArgumentException(
          s"Expected array of strings; got $other")
      }.toSet
      case _ =>
        throw new IllegalArgumentException("Bad input format")
    }
  }

  val letters = ('a'.asInstanceOf[Int] until 'z'.asInstanceOf[Int]).inclusive.map { _.asInstanceOf[Char] }
  val lettersSet = letters.toSet

  // Look for Pokemon whose letters all appear in a shorter name and remove them
  def pruneLongLetterSubsets(inputSet: immutable.Set[String]): immutable.Set[String] = {
    val inputByLetterSets = letters.map { letter =>
      letter -> inputSet.filter(_.contains(letter)).toSet
    }.toMap
    def distinctLetters(input: String): immutable.Set[Char] = input.toSet & lettersSet
    def sameLetters(input: String): immutable.Set[String] =
      distinctLetters(input).toSeq match {
        case first +: tail => tail.foldLeft(inputByLetterSets(first))((setSoFar, letter) => setSoFar & inputByLetterSets(letter))
        case _ => throw new IllegalArgumentException("no letters in input")
      }
    def shortestInSet(input: immutable.Set[String]) = input.toList.sortWith { (a, b) =>
      (a.length < b.length) || ((a.length == b.length) && (a < b))
    }.head

    inputSet.map{ p => shortestInSet(sameLetters(p)) }.toSet
  }

  val pokemon = pruneLongLetterSubsets(allPokemon)
  println(s"Reduced ${allPokemon.size} to ${pokemon.size} pokemon")

  val pokemonByLetter = letters.map { letter =>
    letter -> pokemon.filter(_.contains(letter)).toList.sortBy(_.length)
  }.toMap

  def totalLetters(input: immutable.Set[String]): Int =
    input.foldLeft(0)((lengthSoFar, next) => lengthSoFar + next.length)

  val sortedLetters = letters.toList.sortBy(pokemonByLetter(_).size)

  case class Solution(pokemon: immutable.Set[String], alternatives: immutable.Set[immutable.Set[String]] = Set.empty) {
    val size: Int = pokemon.size
    lazy val length: Int = totalLetters(pokemon)

    def withAlternative(other: Solution) = {
      val newAlternatives = alternatives ++ other.alternatives + other.pokemon - pokemon
      if (!newAlternatives.isEmpty) this.copy(alternatives = newAlternatives)
      else this
    }

    override def toString = {
      val thisSet = s"$size, $length : " + pokemon.mkString(", ")
      if (alternatives.isEmpty) thisSet
      else {
        val alternativesString = alternatives.map(_.mkString(", ")).mkString(" or ")
        s"$thisSet ($alternativesString)"
      }
    }
  }

  val allPokemonSolution = Solution(pokemon)

  def shortestSolution(bestSoFar: Solution): Solution = {

    def shorterSolution(a: Solution, b: Solution): Solution =
      if (a.length < b.length) a
      else if (a.length > b.length) b
      else b.withAlternative(a)

    def shortestSolutionRecurse(soFar: immutable.Set[String], soFarLength: Int, letters: immutable.Seq[Char], bestSoFar: Solution): Solution = {
      letters match {
        case letter +: moreLetters =>
          if (soFar.exists(_.contains(letter)))
            shortestSolutionRecurse(soFar, soFarLength, moreLetters, bestSoFar)
          else {
            val newPokemon = pokemonByLetter(letter).filterNot(soFar.contains(_))

            def tryPokemon(newP: immutable.Seq[String], bestSoFar: Solution): Solution = newP match {
              case aPokemon +: morePokemon =>
                val newSolution =
                  if ((soFarLength + aPokemon.length) > bestSoFar.length)
                    bestSoFar
                  else
                    shortestSolutionRecurse(soFar + aPokemon, soFarLength + aPokemon.length, moreLetters, bestSoFar)
                tryPokemon(morePokemon, shorterSolution(newSolution, bestSoFar))
              case Nil =>
                bestSoFar
            }
            tryPokemon(newPokemon, bestSoFar)
          }
        case Nil =>
          if (soFarLength < bestSoFar.length) {
            val newSolution = Solution(soFar)
            println(newSolution)
            newSolution
          } else {
            bestSoFar
          }
      }
    }

    shortestSolutionRecurse(immutable.Set.empty[String], 0, sortedLetters, bestSoFar)
  }

  /**
   * Compare the two sets and return the one that is smaller in the defined
   * Pokemon set ordering, or both solutions if tied.
   *
   * The least is defined as the one with fewest entries, or if tied the one
   * with fewest total letters. If this is still a tie, we return the second
   * solution but with the first attached as an alternative equivalent
   * solution.
   *
   * @param a the first solution to compare; when combining equivalent
   *          solutions this is assumed to be the new candidate solution
   * @param b the second solution to compare; when combining equivalent
   *          solutions this is assumed to be the older existing best solution
   *          so-far
   * @return the better solution, by the problem's comparison rules, or the
   *         second solution with the first attached as a new alternative
   *         answer if equal.
   */
  def leastSolution(a: Solution, b: Solution): Solution =
    if (a.size < b.size) a
    else if (b.size < a.size) b
    else {
      // a.size == b.size
      if (a.length < b.length) a
      else if (b.length < a.length) b
      else {
        // a.length == b.length
        b.withAlternative(a)
      }
    }

  def findLeastSolution(bestSoFar: Solution): Solution = {
    def leastSolutionRecurse(soFar: immutable.Set[String], soFarCount: Int, soFarLength: Int, letters: immutable.Seq[Char], bestSoFar: Solution): Solution = {
      letters match {
        case letter +: moreLetters =>
          if (soFar.exists(_.contains(letter)))
            leastSolutionRecurse(soFar, soFarCount, soFarLength, moreLetters, bestSoFar)
          else if (soFarCount >= bestSoFar.size) {
            // We can't add any more Pokemon to make a better solution
            bestSoFar
          } else {
            val newPokemon = pokemonByLetter(letter).filterNot(soFar.contains(_))

            def tryPokemon(newP: immutable.Seq[String], bestSoFar: Solution): Solution = newP match {
              case aPokemon +: morePokemon =>
                val newSolution =
                  if (soFarCount >= bestSoFar.size) {
                    bestSoFar
                  } else if ((soFarCount == bestSoFar.size) && ((soFarLength + aPokemon.length) > bestSoFar.length))
                    bestSoFar
                  else
                    leastSolutionRecurse(soFar + aPokemon, soFarCount + 1, soFarLength + aPokemon.length, moreLetters, bestSoFar)
                tryPokemon(morePokemon, leastSolution(newSolution, bestSoFar))
              case Nil =>
                bestSoFar
            }
            tryPokemon(newPokemon, bestSoFar)
          }
        case Nil =>
          val newSolution = Solution(soFar)
          val newBest = leastSolution(newSolution, bestSoFar)
          if (newBest != bestSoFar) println(newBest)
          newBest
      }
    }

    leastSolutionRecurse(immutable.Set.empty[String], 0, 0, sortedLetters, bestSoFar)
  }

  val start = System.currentTimeMillis()
  // TODO parallelise across first letter

  // Find the shortest solution, i.e. the one only considering the fewest
  // possible letters and not the number of Pokemon in the solution. I
  // accidentally coded this one first, having not read the problem
  // properly, and whilst it isn't the final answer it is reasonably close and
  // provides a good upper-bound solution for the main search method to avoid
  // searching too deeply for bad solutions.
  val shortest = shortestSolution(allPokemonSolution)

  // Using the shortest solution as an upper bound, search for the true least
  // solution.
  val best = findLeastSolution(shortest)
  
  // Done!
  val end = System.currentTimeMillis()

  val runtime = end - start
  println(s"Took $runtime ms")
  println(best)
}
