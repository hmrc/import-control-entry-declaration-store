/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.entrydeclarationstore.validation.business

import groovy.lang.{Closure, GroovyClassLoader, GroovyRuntimeException}
import org.codehaus.groovy.control.CompilationFailedException
import play.api.Logging
import uk.gov.hmrc.entrydeclarationstore.validation.business.Assert.CompilationContext

object AssertEvaluator extends Logging {

  abstract class ContextHelper(ctx: LocalNode) {
    self =>

    def applyRegex(path: String, regex: String): Boolean =
      ctx(path).headOption.exists(node => regex.r.pattern.matcher(node.text).matches)

    def getValue(path: String): String =
      ctx(path).headOption.map(_.text).getOrElse("")

    def trim(path: String): String = getValue(path).trim

    def number(path: String): Double =
      ctx(path).headOption.map(node => toDouble(node.text)).getOrElse(Double.NaN)

    def intOrElse(path: String, default: Int): Int =
      ctx(path).headOption.flatMap(node => toIntOption(node.text)).getOrElse(default)

    def substringAfter(value: String, targetChar: String): String =
      value.dropWhile(_ != targetChar.head).drop(1)

    def sum(path: String): Number = ctx(path).map(s => toDouble(s.text)).sum

    def count(path: String): Int = ctx(path).length

    def countEquals(path: String, comparison: String): Int =
      ctx(path).count(node => node.text.trim == comparison)

    def countDistinctChildCount(path: String): Int = {
      val childLengths = ctx(path).map(_.children.length)

      childLengths.distinct.length
    }

    def countDistinct(path: String, trim: Boolean): Int = {
      val texts = ctx(path)
        .map { n =>
          if (trim) n.text.trim else n.text
        }

      texts.distinct.length
    }

    def exists(path: String, f: Closure[java.lang.Boolean]): Boolean =
      ctx(path).exists(localNode => f.call(Array(self, LocalContextHelper(localNode)): _*))

    def exists(path: String): Boolean = ctx(path).nonEmpty

    def not(path: String): Boolean = !exists(path)

    def existsForAll(pathParent: String, path: String, comparison: String): Boolean =
      ctx(pathParent).forall(localNode => localNode(path).exists(_.text.trim == comparison))

    // Checks that the values are 1,2,3,... in order
    def areIndices(path: String): Boolean = {
      val values = ctx(path).map(_.text)

      values.map(toIntOption) == (1 to values.length).map(Some(_))
    }

    private def toIntOption(s: String): Option[Int] =
      try Some(s.trim.toInt)
      catch {
        case _: NumberFormatException => None
      }

    private def toDoubleOption(s: String): Option[Double] =
      try Some(s.trim.toDouble)
      catch {
        case _: NumberFormatException => None
      }

    private def toDouble(s: String): Double =
      toDoubleOption(s).getOrElse(Double.NaN)
  }

  case class LocalContextHelper(ctx: LocalNode) extends ContextHelper(ctx)

  val shell = new GroovyClassLoader()

  /**
    * Evaluates an assert in a specific context. Subclassed for each assert.
    */
  abstract class ContextualAssertEvaluator(ctx: ContextNode) extends ContextHelper(ctx) {
    def evaluate: Boolean
  }

  def createAssertEvaluator(assert: Assert)(implicit compilationContext: CompilationContext): AssertEvaluator = {
    val className = s"ContextualAssertEvaluator_${compilationContext.classId}"

    val classSource =
      s"""
         |import ${classOf[ContextHelper].getCanonicalName}
         |
         |@groovy.transform.CompileStatic
         |class $className extends ${classOf[ContextualAssertEvaluator].getName} {
         |  $className(${classOf[ContextNode].getName} context) {
         |    super(context)
         |  }
         |
         |  boolean evaluate() {
         |    ${assert.test}
         |  }
         |}
         |""".stripMargin

    val clazz =
      try shell.parseClass(classSource).asInstanceOf[Class[ContextualAssertEvaluator]]
      catch {
        case e: CompilationFailedException =>
          logger.error(s"Compilation error in ${compilationContext.ruleName} for assert test:\n${assert.test}")
          throw e
      }

    val constructor = clazz.getConstructor(classOf[ContextNode])

    { ctx: ContextNode =>
      val evaluator: ContextualAssertEvaluator = constructor.newInstance(ctx)

      try evaluator.evaluate
      catch {
        // Because groovy supports dynamic methods, may not see some code problems until runtime...
        case e: GroovyRuntimeException =>
          logger.error(s"Error evaluating in ${compilationContext.ruleName} for assert test:\n${assert.test}")
          throw e
      }
    }
  }
}
