package akka.persistence.eventstore

object UrlEncoder extends (String => String) {
  def apply(x: String): String = java.net.URLEncoder.encode(x, "UTF-8")
}