package playground

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future}

object ThreadModelLimitations extends App {

  /**
   * OOP:ony valid in single threaded applications
   *
   * synchronization, dead and live locks
   *
   */
  case class BankAccount(private var amount: Int) {
    def add(qty: Int) = amount += qty

    def substract(qty: Int) = amount -= qty

  }

  def executeBank = {
    val bankAccount = BankAccount(2000)
    for (_ <- 0 to 100) {
      new Thread(() => bankAccount.add(1)).start()
    }

    for (_ <- 0 to 100) {
      new Thread(() => bankAccount.substract(1)).start()
    }

    println(bankAccount)
  }

  //executeBank()

  class SaveBankAccount(private var amount: Int) {
    def add(qty: Int) = this.synchronized {

      this.amount += qty
    }

    def substract(qty: Int) = this.synchronized {
      this.amount -= qty
    }

    override def toString: String = "balance " + this.amount.toString
  }

  def executeSafeBank() = {
    val safeBankAccount = new SaveBankAccount(2000)
    for (_ <- 0 to 10) {
      new Thread(() => safeBankAccount.add(1)).start()
    }

    for (_ <- 0 to 10) {
      new Thread(() => safeBankAccount.substract(1)).start()
    }

    println(safeBankAccount)

  }

  //executeSafeBank()

  /**
   * delegating actions to a thread is difficult, and a lot of cons arise
   * how to.
   *  - handle different signals
   *  - multiple backgrounds tasks and threads
   *  - signal originator
   *  - crash of task
   */


  var task: Runnable = null

  val backgroundThread: Thread = new Thread(() => {
    while (true) {
      while (task == null) {
        backgroundThread.synchronized {
          println("[waiting] for new task")
          backgroundThread.wait()
        }
      }
      backgroundThread.synchronized {
        println("[starting] executing task")
        task.run()
        task = null
      }
    }
  })


  def delegateTask(r: Runnable) = {
    if (task == null) task = r

    backgroundThread.synchronized {
      backgroundThread.notify()
    }
  }


  def executeDelegation() = {
    backgroundThread.start()
    delegateTask(() => println(23))
    delegateTask(() => println(53))

    backgroundThread.stop()

  }
  //executeDelegation()
  /**
   * tracing and dealing with errors in multithreading
   */

  //1M numbers in 10 threads

  val futures = (0 to 9)
    .map(e => e * 100000 until 100000 * (e + 1))
    .map(bucket => Future {
     if (bucket.contains(20)) throw new RuntimeException("Invalid Number")
     bucket.sum
    })

  val sumFutures = Future.reduceLeft(futures)(_ + _)

  println(sumFutures.value)
  sumFutures.onComplete(println)

}
