package scavlink.task

import java.util.NoSuchElementException

import akka.actor.ActorRef
import org.joda.time.DateTime
import scavlink.coord.{Geo, LatLon}
import scavlink.link.Vehicle
import scavlink.link.operation.OpFlags
import scavlink.message.Mode
import scavlink.message.common.MissionItem

import scala.annotation.StaticAnnotation
import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{currentMirror => cm}


case class TaskData[S, R](status: TypeTag[S] = TypeTag.Object, result: TypeTag[R] = TypeTag.Object) extends StaticAnnotation
case class Ignore() extends StaticAnnotation
case class Description(description: String) extends StaticAnnotation

class NoValidMethodsFoundException extends Exception
class MethodNotFoundException(msg: String) extends Exception(msg)
class ParameterMissingException(msg: String) extends Exception(msg)

/**
 * Reflection view of an API class that provides task execution methods.
 * This reflection view inspects the class, extracts the constructor
 * and any conforming methods, and produces a reflected definition of them.
 * It also provides an invoker that accepts a method name and untyped arguments.
 *
 * A task method's argument list must contain only supported types, and it
 * must include a single ActorRef to which progress and result messages
 * will be returned (just like the implicit sender on an Akka tell).
 * If a method does not meet these criteria, it is not included in the list of
 * available API methods.
 *
 * A task method's parameters may define default values. If defined, values for
 * those parameters are not required to be passed to the invoker; the defaults
 * will be used if they are missing.
 *
 * A task API class may be a singleton object, a class without constructor arguments,
 * or a class with constructor arguments. If the class has constructor arguments,
 * they are treated as arguments to every method, and must only be supported types.
 * A common case for this are APIs provided as implicit classes over the Vehicle object,
 * such as [[scavlink.link.nav.NavTellAPI.Nav]].
 *
 * A task method is expected to asynchronously kick off some actor-based operation,
 * so its immediate return value is unimportant; its type and value are not checked.
 * The task actor may send back progress messages as it executes, but it must
 * send a result message when it completes.
 *
 * @param typ class type
 * @param ctor constructor method
 * @param methods available API methods
 * @author Nick Rossi
 */
case class TaskAPI(typ: Type, ctor: APIMethod, methods: Map[String, APIMethod]) {
  val name = typ.typeSymbol.name.decodedName.toString

  private val ctorMirror: MethodMirror = cm.reflectClass(typ.typeSymbol.asClass).reflectConstructor(ctor.symbol)

  private val singleton: Option[InstanceMirror] = {
    val obj = if (typ.termSymbol.isModule)
      Some(cm.reflectModule(typ.termSymbol.asModule).instance)
    else if (ctor.params.isEmpty)
      Some(ctorMirror())
    else
      None

    obj.map(cm.reflect)
  }


  def isEmpty = methods.isEmpty
  def nonEmpty = methods.nonEmpty
  def isSingleton: Boolean = singleton.nonEmpty

  /**
   * Invoke the named method with the named parameter values.
   * @param method method name
   * @param args parameter values
   * @return return value of the method
   * @throws MethodNotFoundException if method name is not found
   * @throws ParameterMissingException if a required parameter is missing from args and has no default value
   */
  @throws[MethodNotFoundException]
  @throws[ParameterMissingException]
  def invoke(method: String, args: Map[String, Any]): Any = methods.get(method) match {
    case Some(m) => invokeMethod(m, args)
    case None => throw new MethodNotFoundException(s"Unknown method '$method'")
  }

  /**
   * Invoke the method with the named parameter values.
   * @param method method
   * @param args parameter values
   * @return return value of the method
   * @throws ParameterMissingException if a required parameter is missing from args and has no default value
   */
  @throws[MethodNotFoundException]
  @throws[ParameterMissingException]
  def invokeMethod(method: APIMethod, args: Map[String, Any]): Any = {
    try {
      method.symbol.owner
      val api = instance(args)
      val methodMirror = api.reflectMethod(method.symbol)
      val values = paramValues(args, method.params, method.defaults, api)
      methodMirror(values: _*)
    } catch {
      case e: NoSuchElementException => throw new ParameterMissingException(e.getMessage)
      case e: ScalaReflectionException => throw new MethodNotFoundException(s"Unknown method '${method.name}'")
    }
  }


  /**
   * Get an API instance to invoke a method on.
   * If API is a declared object or has a parameterless constructor, a singleton instance is returned.
   * Otherwise, the constructor is invoked with whatever parameters it requires.
   * @param args parameter values for constructor
   * @return API instance
   */
  private def instance(args: Map[String, Any]): InstanceMirror = {
    singleton.getOrElse {
      def companion = cm.reflect(cm.reflectModule(typ.typeSymbol.companion.asModule).instance)
      val values = paramValues(args, ctor.params, ctor.defaults, companion)
      val obj = ctorMirror(values: _*)
      cm.reflect(obj)
    }
  }

  /**
   * Resolve an ordered list of method parameter values from the supplied named arguments.
   * If a required parameter isn't in the args, invokes the default method to get its value.
   */
  private def paramValues(args: Map[String, Any],
                          params: ListMap[String, Type],
                          defaults: Map[String, APIMethod],
                          instance: => InstanceMirror): List[Any] = {
    var argMap = args

    def default(param: String): Any = {
      val defaultMethod = defaults(param)
      val methodMirror = instance.reflectMethod(defaultMethod.symbol)
      val values = paramValues(argMap, defaultMethod.params, Map.empty, instance)
      methodMirror(values: _*)
    }

    params.map { case (arg, _) =>
      argMap.get(arg) match {
        case Some(value) => value
        case None =>
          // must add every default value we get back to the running arg map,
          // since default methods in secondary lists require prior arg values
          val value = default(arg)
          argMap += arg -> value
          value
      }
    }.toList
  }


  override def toString = {
    val sb = new StringBuilder(s"$name\n")
    if (singleton.isEmpty) sb.append(s"  $ctor\n")
    methods.foreach { case (_, m) => sb.append(s"  $m\n") }
    sb.result()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: TaskAPI => this.typ =:= that.typ && this.ctor == that.ctor && this.methods == that.methods
    case _ => false
  }
}


/**
 * Reflection view of an API method.
 * @param name method name
 * @param symbol reflected method
 * @param params parameter list in order by name
 * @param defaults default parameter value generation methods
 * @author Nick Rossi
 */
case class APIMethod(name: String,
                     description: Option[String],
                     symbol: MethodSymbol,
                     params: ListMap[String, Type],
                     defaults: Map[String, APIMethod]) {

  override def toString = {
    def paramToString(p: (String, Type)): String = {
      val default = defaults.get(p._1).map(d => s"(${ d.name })").getOrElse("")
      s"${ p._1 }: ${ p._2 }$default"
    }

    val ps = params.map(paramToString).mkString(", ")
    s"$name ($ps)"
  }
}


object APIMethod {
  def apply(ms: MethodSymbol): APIMethod = {
    val name = ms.name.decodedName.toString
    val descAnno = ms.annotations.find(a => a.tree.tpe =:= typeOf[Description])
    val desc = descAnno.map { a => // todo: pull this out to function
      val args = a.tree.children.tail
      val vals = args.map(_.productElement(0).asInstanceOf[Constant].value)
      vals.head.asInstanceOf[String]
    }

    val params = extractParams(ms)
    val defaults = extractDefaults(ms, params)
    val paramTypes = params.map { case (k, v) => (k, v.typeSignature) }
    APIMethod(name, desc, ms, paramTypes, defaults)
  }


  // presence of this string in a method name indicates a default value method
  private[task] val DefaultValueMarker = "$default$"

  /**
   * Extract an ordered list of parameters from a method.
   */
  private[task] def extractParams(ms: MethodSymbol): ListMap[String, TermSymbol] =
    ListMap(ms.paramLists.flatten.map(p => (p.name.decodedName.toString, p.asTerm)): _*)

  /**
   * Extract default value methods for any parameters having a default.
   */
  private[task] def extractDefaults(ms: MethodSymbol, params: ListMap[String, TermSymbol]): Map[String, APIMethod] = {
    val typ = ms.owner.typeSignature

    def defaultSymbol(name: String, i: Int): Symbol = {
      val termName = TermName(ms.name.encodedName.toString + DefaultValueMarker + (i + 1).toString)
      if (ms.isConstructor) typ.companion.member(termName) else typ.member(termName)
    }

    val defaults = for {
      ((name, p), i) <- params.zipWithIndex if p.isParamWithDefault
      sym = defaultSymbol(name, i) if sym.isMethod
    } yield {
      (name, APIMethod(sym.asMethod))
    }

    defaults.toMap
  }
}


object TaskAPI {
  @throws[NoValidMethodsFoundException]
  def apply(typ: Type): TaskAPI =
    extractContract(typ) match {
      case Some((ctor, methods)) => TaskAPI(typ, ctor, methods)
      case None => throw new NoValidMethodsFoundException
    }

  @throws[NoValidMethodsFoundException]
  def apply[T: TypeTag]: TaskAPI = apply(typeOf[T])

  @throws[NoValidMethodsFoundException]
  @throws[ScalaReflectionException]
  def apply(className: String): TaskAPI = {
    var typ = cm.staticClass(className).toType
    if (typ.members.isEmpty) {
      typ = cm.staticModule(className).typeSignature
    }
    apply(typ)
  }


  // todo: allow Option as non-required field
  val allowedCollectionTypes = List(
    typeOf[List[_]], typeOf[Vector[_]], typeOf[Set[_]], typeOf[Map[String, _]])

  val allowedParamTypes = List(
    typeOf[ActorRef], typeOf[OpFlags], typeOf[Vehicle],
    typeOf[String], typeOf[Double], typeOf[Int], typeOf[Boolean], typeOf[Long], typeOf[Float],
    typeOf[Geo], typeOf[LatLon], typeOf[MissionItem], typeOf[Mode],
    typeOf[DateTime], typeOf[Byte], typeOf[Short]
  )


  private[task] def extractContract(typ: Type): Option[(APIMethod, Map[String, APIMethod])] = {
    val members = typ.members
    val _ctor = members.find(isValidConstructor)
    if (_ctor == None) return None

    val Some(ctor: MethodSymbol) = _ctor
    val ctorMethod = APIMethod(ctor)

    val methodFilter = isValidMethod(ctor.paramLists) _
    val methods = members.collect {
      case ms: MethodSymbol if methodFilter(ms) => APIMethod(ms)
    }

    val methodMap = methods.map(m => m.name -> m).toMap
    if (methodMap.nonEmpty) Some(ctorMethod, methodMap) else None
  }


  /**
   * A valid method must:
   * <ul>
   * <li>be public</li>
   * <li>not be a constructor</li>
   * <li>not be a default argument generator</li>
   * <li>not come from a JDK or Scala SDK class</li>
   * <li>not have the Ignore annotation</li>
   * <li>have a valid parameter list when combined with constructor params</li>
   * </ul>
   */
  private[task] def isValidMethod(constructorParams: List[List[Symbol]])(m: MethodSymbol): Boolean = {
    def params = constructorParams ::: m.paramLists

    m.isPublic &&
    !m.isConstructor &&
    !m.owner.fullName.startsWith("scala.") &&
    !m.owner.fullName.startsWith("java.") &&
    !m.name.decodedName.toString.contains(APIMethod.DefaultValueMarker) &&
    !isIgnore(m) &&
    isAllowedReturnType(m.returnType) &&
    isValidParameterList(params.flatten)
  }

  /**
   * A valid constructor must be the primary constructor and have all allowed parameter types.
   */
  private[task] def isValidConstructor(member: Symbol): Boolean = member match {
    case m: MethodSymbol => m.isPrimaryConstructor && m.paramLists.flatten.forall(p => isAllowedParamType(p.typeSignature))
    case _ => false
  }

  /**
   * Parameters should all be allowed types and contain exactly one ActorRef.
   */
  private[task] def isValidParameterList(params: List[Symbol]): Boolean = {
    var foundActor = false

    params foreach { p =>
      val typ = p.typeSignature
      if (!isAllowedParamType(typ)) {
        return false
      }
      if (typ =:= typeOf[ActorRef]) {
        if (!foundActor) foundActor = true else return false
      }
    }

    foundActor
  }

  private[task] def isAllowedReturnType(typ: Type): Boolean = typ match {
    case t if t =:= typeOf[Unit] => true
    case t if t <:< typeOf[Expect] => true
    case t => isAllowedParamType(t)
  }

  /**
   * Allow our list of types plus collections of those types.
   */
  private[task] def isAllowedParamType(typ: Type): Boolean = typ match {
    case t if allowedParamTypes.exists(t =:= _) => true
    case t if allowedCollectionTypes.exists(t <:< _) => t.dealias.typeArgs.forall(isAllowedParamType)
    case _ => false
  }

  /**
   * Whether the symbol has the Ignore annotation.
   */
  private[task] def isIgnore(sym: Symbol): Boolean = sym.annotations.exists(a => a.tree.tpe <:< typeOf[Ignore])
}
