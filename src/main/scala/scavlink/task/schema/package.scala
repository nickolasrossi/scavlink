package scavlink.task

import scala.reflect.runtime.universe._

package object schema {
  type SchemaDef = Either[Pointer, Schema]

  def definitionNameOf(typ: Type): String = typ.typeSymbol.name.decodedName.toString
  def propertyNameOf(typ: Type): String = lowercaseName(definitionNameOf(typ))
  def propertyNameOf[T: TypeTag]: String = propertyNameOf(typeOf[T])
  def lowercaseName(s: String): String = if (s.isEmpty) s else s.head.toLower + s.tail
}
