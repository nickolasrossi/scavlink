package scavlink.task

import java.security.SecureRandom

import org.parboiled.common.Base64

sealed trait TaskStatus {
  def id: TaskId
  def message: String
  def data: Map[String, Any]
}

case class TaskProgress(id: TaskId, progress: Int = 0, message: String = "", data: Map[String, Any] = Map.empty)
  extends TaskStatus

case class TaskComplete(id: TaskId, success: Boolean = true, message: String = "",
                        data: Map[String, Any] = Map.empty)
  extends TaskStatus


object TaskComplete {
  def apply(id: TaskId, data: Map[String, Any]): TaskComplete = TaskComplete(id, true, "", data)
  def failed(id: TaskId): TaskComplete = TaskComplete(id, false)
  def failed(id: TaskId, error: String): TaskComplete = TaskComplete(id, false, error)
}


object TaskId {
  private val random = new SecureRandom
  private val base64 = Base64.custom()

  def empty = ""

  def generate(): TaskId = {
    val bytes = new Array[Byte](24)
    random.nextBytes(bytes)
    base64.encodeToString(bytes, false)
  }
}
