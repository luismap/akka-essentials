package akka.testing

import java.io.File

object Utils {
  /**
   * given a path, return an array
   * of files
   *
   * @param path
   * @return
   */
  def fileReader(path: String) = {
    val files = new File(path).listFiles()
    files.filter(_.isFile)
  }


  /**
   * given a file, read its
   *
   * @param files
   * @return
   */
  def fileProcessor(file: File) = {
    for {
      line <- scala.io.Source.fromFile(file).getLines().toList
      if !line.matches(raw"\s*\)*")
    } yield {
      line
    }
  }

  /**
   * given a line, count how many words
   * (base on just white space)
   *
   * @param line
   * @return
   */
  def counter(line: String): Int = line.split(raw"\s+").length
}
