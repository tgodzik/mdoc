package vork.internal.cli

import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.PathMatcher
import metaconfig.Conf
import metaconfig.ConfDecoder
import metaconfig.ConfError
import metaconfig.Configured
import metaconfig.annotation._
import metaconfig.generic
import metaconfig.generic.Surface
import scala.meta.io.AbsolutePath
import scala.meta.io.RelativePath
import vork.StringModifier
import vork.Reporter
import vork.internal.markdown.MarkdownCompiler

case class Settings(
    @Description("The input directory to generate the vork site.")
    @ExtraName("i")
    in: AbsolutePath,
    @Description("The output directory to generate the vork site.")
    @ExtraName("o")
    out: AbsolutePath,
    @Description("The current working directory")
    cwd: AbsolutePath,
    cleanTarget: Boolean = false,
    charset: Charset = StandardCharsets.UTF_8,
    @Description("Optional classpath to compile Scala code examples")
    classpath: String = "",
    @ExtraName("w")
    watch: Boolean = false,
    @Description("Glob to filter which files from --in directory to include.")
    includePath: List[PathMatcher] = Nil,
    @Description("Glob to filter which files from --in directory to exclude.")
    excludePath: List[PathMatcher] = Nil,
    site: Map[String, String] = Map.empty,
    stringModifiers: List[StringModifier] = Nil,
    test: Boolean = false,
    reportRelativePaths: Boolean = false
) {

  def toInputFile(infile: AbsolutePath): Option[InputFile] = {
    val relpath = infile.toRelative(in)
    if (matches(relpath)) {
      val outfile = out.resolve(relpath)
      Some(InputFile(relpath, infile, outfile))
    } else {
      None
    }
  }
  def matches(path: RelativePath): Boolean = {
    (includePath.isEmpty || includePath.exists(_.matches(path.toNIO))) &&
    !excludePath.exists(_.matches(path.toNIO))
  }
  def validate(logger: Reporter): Configured[Context] = {
    if (Files.exists(in.toNIO)) {
      val compiler = MarkdownCompiler.fromClasspath(classpath)
      Configured.ok(Context(this, logger, compiler))
    } else {
      ConfError.fileDoesNotExist(in.toNIO).notOk
    }
  }
  def resolveIn(relpath: RelativePath): AbsolutePath = {
    in.resolve(relpath)
  }

  def resolveOut(relpath: RelativePath): AbsolutePath = {
    out.resolve(relpath)
  }
}

object Settings {
  def default(cwd: AbsolutePath): Settings = new Settings(
    in = cwd.resolve("docs"),
    out = cwd.resolve("out"),
    cwd = cwd
  )
  def fromCliArgs(args: List[String], base: Settings): Configured[Settings] = {
    Conf
      .parseCliArgs[Settings](args)
      .andThen(_.as[Settings](decoder(base)))
  }
  implicit val surface: Surface[Settings] =
    generic.deriveSurface[Settings]
  def decoder(base: Settings): ConfDecoder[Settings] = {
    implicit val cwd = base.cwd
    implicit val PathDecoder: ConfDecoder[AbsolutePath] =
      ConfDecoder.stringConfDecoder.flatMap { str =>
        try {
          Configured.ok(AbsolutePath(str))
        } catch {
          case e: InvalidPathException =>
            ConfError.message(e.getMessage).notOk
        }
      }
    generic.deriveDecoder[Settings](base)
  }

  implicit val pathMatcherDecoder: ConfDecoder[PathMatcher] =
    ConfDecoder.stringConfDecoder.map(glob => FileSystems.getDefault.getPathMatcher("glob:" + glob))
  implicit val CharsetDecoder: ConfDecoder[Charset] =
    ConfDecoder.stringConfDecoder.flatMap { str =>
      try {
        Configured.ok(Charset.forName(str))
      } catch {
        case _: UnsupportedCharsetException =>
          ConfError.message(s"Charset '$str' is unsupported").notOk
        case _: IllegalCharsetNameException =>
          ConfError.message(s"Charset name '$str' is illegal").notOk
      }
    }

}