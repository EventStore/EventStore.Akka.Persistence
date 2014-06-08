package akka.persistence.eventstore

import java.util.regex.Pattern

object Normalize extends (String => String) {
  private val p1 = Pattern.compile("[/]+")
  private val p2 = Pattern.compile("(^-)|(-$)")

  def apply(x: String): String = p2.matcher(p1.matcher(x).replaceAll("-")).replaceAll("")
}