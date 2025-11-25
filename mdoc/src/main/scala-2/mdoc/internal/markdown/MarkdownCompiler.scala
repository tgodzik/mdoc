package mdoc.internal.markdown

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional
import mdoc.Reporter
import mdoc.document.Document
import mdoc.document._
import mdoc.internal.document.MdocNonFatal
import mdoc.internal.pos.PositionSyntax
import mdoc.internal.pos.PositionSyntax._
import mdoc.internal.pos.TokenEditDistance
import scala.collection.JavaConverters._
import scala.collection.Seq
import scala.meta._
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.{Position => GPosition}
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.io.VirtualDirectory
import scala.annotation.implicitNotFound
import mdoc.internal.worksheets.Compat._

class MarkdownCompilerImpl private (
    classpath: String,
    override val scalacOptions: String,
    target: AbstractFile
) extends mdoc.internal.markdown.MarkdownCompiler {

  def this(classpath: String, scalacOptions: String) = {
    this(classpath, scalacOptions, new VirtualDirectory("(memory)", None))
  }
  private val settings = new Settings()
  settings.Yrangepos.value = true
  settings.deprecation.value = true // enable detailed deprecation warnings
  settings.unchecked.value = true // enable detailed unchecked warnings
  settings.outputDirs.setSingleOutput(target)
  settings.classpath.value = classpath
  settings.exposeEmptyPackage.value = true
  // enable -Ydelambdafy:inline to avoid future timeouts, see:
  //   https://github.com/scala/bug/issues/9824
  //   https://github.com/scalameta/mdoc/issues/124
  settings.Ydelambdafy.value = "inline"
  settings.processArgumentString(scalacOptions)

  def classpathEntriesSeq: Seq[Path] = global.classPath.asURLs.map(url => Paths.get(url.toURI()))

  private val sreporter = new FilterStoreReporter(settings)
  var global = new Global(settings, sreporter)
  private def reset(): Unit = {
    global = new Global(settings, sreporter)
  }
  private val appClasspath: Array[URL] = classpath
    .split(File.pathSeparator)
    .map(path => new File(path).toURI.toURL)
  private val appClassLoader = new URLClassLoader(
    appClasspath,
    this.getClass.getClassLoader
  )

  private def clearTarget(): Unit =
    target match {
      case vdir: VirtualDirectory => vdir.clear()
      case _ =>
    }

  private def toSource(input: Input): BatchSourceFile = {
    new BatchSourceFile(input.filename, new String(input.chars))
  }

  def shutdown(): Unit = {
    global.close()
  }

  def fail(edit: TokenEditDistance, input: Input, sectionPos: Position): String = {
    sreporter.reset()
    val g = global
    val run = new g.Run
    run.compileSources(List(toSource(input)))
    val out = new ByteArrayOutputStream()
    val ps = new PrintStream(out)
    sreporter.infos.foreach { case sreporter.Info(pos, msgOrNull, gseverity) =>
      val msg = nullableMessage(msgOrNull)
      val mpos = toMetaPosition(edit, pos)

      if (sectionPos.contains(mpos) || gseverity == sreporter.ERROR) {
        val severity = gseverity.toString.toLowerCase
        val formatted = PositionSyntax.formatMessage(mpos, severity, msg, includePath = false)
        ps.println(formatted)
      }
    }
    out.toString()
  }

  def hasErrors: Boolean = sreporter.hasErrors
  def hasWarnings: Boolean = sreporter.hasWarnings

  def compileSources(
      input: Input,
      vreporter: Reporter,
      edit: TokenEditDistance,
      fileImports: List[FileImport]
  ): Unit = {
    clearTarget()
    sreporter.reset()
    val g = global
    val run = new g.Run
    val inputs = input :: fileImports.map(_.toInput)
    run.compileSources(inputs.map(toSource))
    report(vreporter, input, edit, fileImports)
  }

  def compile(
      input: Input,
      vreporter: Reporter,
      edit: TokenEditDistance,
      className: String,
      fileImports: List[FileImport],
      retry: Int = 0
  ): Option[Class[_]] = {
    reset()
    compileSources(input, vreporter, edit, fileImports)
    if (!sreporter.hasErrors) {
      val loader = new AbstractFileClassLoader(target, appClassLoader)
      try {
        Some(loader.loadClass(className))
      } catch {
        case _: ClassNotFoundException =>
          if (retry < 1) {
            reset()
            compile(input, vreporter, edit, className, fileImports, retry + 1)
          } else {
            vreporter.error(
              s"${input.syntax}: skipping file, the compiler produced no classfiles " +
                "and reported no errors to explain what went wrong during compilation. " +
                "Please report an issue to https://github.com/scalameta/mdoc/issues."
            )
            None
          }
      }
    } else {
      None
    }
  }

  private def toMetaPosition(edit: TokenEditDistance, pos: GPosition): Position = {
    def toOffsetPosition(offset: Int): Position = {
      edit.toOriginal(offset) match {
        case Left(_) =>
          Position.None
        case Right(p) =>
          p.toUnslicedPosition
      }
    }
    if (pos.isDefined) {
      if (pos.isRange) {
        (edit.toOriginal(pos.start), edit.toOriginal(pos.end - 1)) match {
          case (Right(start), Right(end)) =>
            Position.Range(start.input, start.start, end.end).toUnslicedPosition
          case (_, _) =>
            toOffsetPosition(pos.point)
        }
      } else {
        toOffsetPosition(pos.point)
      }
    } else {
      Position.None
    }
  }

  private def nullableMessage(msgOrNull: String): String =
    if (msgOrNull == null) "" else msgOrNull

  private def report(
      vreporter: Reporter,
      input: Input,
      edit: TokenEditDistance,
      fileImports: List[FileImport]
  ): Unit = {
    val infos = sreporter.infos.toSeq.sortBy(_.pos.source.path)
    infos.foreach {
      case sreporter.Info(pos, msgOrNull, severity) =>
        val msg = nullableMessage(msgOrNull)
        val actualEdit =
          if (pos.source.file.name.endsWith(".sc")) {
            fileImports
              .collectFirst {
                case fileImport if fileImport.path.toNIO.endsWith(pos.source.file.name) =>
                  fileImport.edit
              }
              .flatten
              .getOrElse(edit)
          } else {
            edit
          }
        val mpos = toMetaPosition(actualEdit, pos)
        val actualMessage =
          if (mpos == Position.None) {
            val line = pos.lineContent
            if (line.nonEmpty) {
              formatMessage(pos, msg)
            } else {
              msg
            }
          } else {
            msg
          }
        reportMessage(vreporter, severity, mpos, actualMessage)
      case _ =>
    }
  }

  private def reportMessage(
      vreporter: Reporter,
      severity: sreporter.Severity,
      mpos: Position,
      message: String
  ): Unit = {
    import sreporter._
    if (severity == sreporter.ERROR)
      vreporter.error(mpos, message)
    else if (severity == sreporter.WARNING)
      vreporter.warning(mpos, message)
    else if (severity == sreporter.INFO)
      vreporter.info(mpos, message)
  }
  private def formatMessage(pos: GPosition, message: String): String =
    new CodeBuilder()
      .println(s"${pos.source.file.path}:${pos.line + 1} (mdoc generated code) $message")
      .println(pos.lineContent)
      .println(pos.lineCaret)
      .toString

  // Java interface implementation methods
  override def compile(
      input: Object,
      reporter: Object,
      edit: Object,
      className: String,
      fileImports: Object
  ): Optional[Class[_]] = {
    val result: Option[Class[_]] = compile(
      input.asInstanceOf[Input],
      reporter.asInstanceOf[Reporter],
      edit.asInstanceOf[TokenEditDistance],
      className,
      fileImports.asInstanceOf[List[FileImport]]
    )
    result match {
      case Some(cls) => Optional.of(cls.asInstanceOf[Class[_]])
      case None => Optional.empty[Class[_]]()
    }
  }

  override def compileSources(
      input: Object,
      reporter: Object,
      edit: Object,
      fileImports: Object
  ): Unit = {
    compileSources(
      input.asInstanceOf[Input],
      reporter.asInstanceOf[Reporter],
      edit.asInstanceOf[TokenEditDistance],
      fileImports.asInstanceOf[List[FileImport]]
    )
  }

  override def fail(
      edit: Object,
      input: Object,
      sectionPos: Object
  ): String = {
    fail(
      edit.asInstanceOf[TokenEditDistance],
      input.asInstanceOf[Input],
      sectionPos.asInstanceOf[Position]
    )
  }

  override def classpathEntries(): java.util.List[Path] = {
    scala.jdk.CollectionConverters.SeqHasAsJava(classpathEntriesSeq.toList).asJava
  }

  override def newInstance(classpath: String, scalacOptions: String): MarkdownCompiler = {
    new MarkdownCompilerImpl(classpath, scalacOptions)
  }

}
