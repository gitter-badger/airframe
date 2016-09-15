package wvlet.log

import java.util
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.{logging => jl}

/**
  * Logging using background thread
  */
class AsyncHandler(parent: jl.Handler)
  extends jl.Handler
    with Guard
    with AutoCloseable {

  private[this] val executor = {
    Executors.newCachedThreadPool(
      new ThreadFactory {
        override def newThread(r: Runnable): Thread = {
          val t = new Thread(r, "WvletLogAsyncHandler")
          t.setDaemon(true)
          t
        }
      }
    )
  }

  private val queue      = new util.ArrayDeque[jl.LogRecord]
  private val isNotEmpty = newCondition
  private val closed     = new AtomicBoolean(false)

  // Start a poller thread
  executor.submit(new Runnable {
    override def run(): Unit = {
      while (!closed.get()) {
        val record: Option[jl.LogRecord] = guard {
          if (queue.isEmpty) {
            isNotEmpty.await()
          }
          Option(queue.pollFirst())
        }
        record.map(parent.publish _)
      }
    }
  })

  override def flush(): Unit = {
    val records = Seq.newBuilder[jl.LogRecord]
    guard {
      while (!queue.isEmpty) {
        records += queue.pollFirst()
      }
    }

    records.result.map(parent.publish _)
    parent.flush()
  }

  override def publish(record: jl.LogRecord): Unit = {
    guard {
      queue.addLast(record)
      isNotEmpty.signalAll()
    }
  }

  override def close(): Unit = {
    if (closed.compareAndSet(false, true)) {
      // Wake up the poller thread
      guard {
        isNotEmpty.signalAll()
      }
      executor.shutdown()
    }
  }
}
