package mdoc.internal.pprint

import scala.language.implicitConversions
import scala.quoted._
import scala.quoted.runtime.impl.printers.SyntaxHighlight

trait TPrint[T]{
  def render: String
}

object TPrint {
  inline given default[T] as TPrint[T] = ${ TypePrinter.typeString[T] }
}

object TypePrinter{

  def typeString[T](using ctx: Quotes, tpe: Type[T]): Expr[TPrint[T]] = {
    import quotes.reflect._
    val valueType = Type.showShort[T]

    '{  new TPrint[T]{ def render: String = ${ Expr(valueType) } }  }
  }
}
