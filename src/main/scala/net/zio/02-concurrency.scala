/**
 * CONCURRENCY
 *
 * ZIO has pervasive support for concurrency, including parallel versions of
 * operators like `zip`, `foreach`, and many others.
 *
 * More than just enabling your applications to be highly concurrent, ZIO
 * gives you lock-free, asynchronous structures like queues, hubs, and pools.
 * Sometimes you need more: you need the ability to concurrently update complex
 * shared state across many fibers.
 *
 * Whether you need the basics or advanced custom functionality, ZIO has the
 * toolset that enables you to solve any problem you encounter in the
 * development of highly-scalable, resilient, cloud-native applications.
 *
 * In this section, you will explore more advanced aspects of ZIO concurrency,
 * so you can learn to build applications that are low-latency, highly-
 * scalable, interruptible, and free from deadlocks and race conditions.
 */
package advancedzio.concurrency

import zio._
import zio.stm._
import zio.test._
import zio.test.TestAspect._
import zio.test.environment.Live
import zio.test.Assertion._
import zio.test.environment.TestClock

/**
 * ZIO queues are high-performance, asynchronous, lock-free structures backed
 * by hand-optimized ring buffers. ZIO queues come in variations for bounded,
 * which are doubly-backpressured for producers and consumers, sliding (which
 * drops earlier elements when capacity is reached), dropping (which drops
 * later elements when capacity is reached), and unbounded.
 *
 * Queues work well for multiple producers and multiple consumers, where
 * consumers divide work between themselves.
 */
object QueueBasics extends DefaultRunnableSpec {
  def spec =
    suite("QueueBasics") {

      /**
       * EXERCISE
       *
       * Using `.take` and `.offer`, create two fibers, one which places
       * 12 inside the queue, and one which takes an element from the
       * queue and stores it into the provided `ref`.
       */
      test("offer/take") {
        for {
          ref   <- Ref.make(0)
          queue <- Queue.bounded[Int](1)
          offer = queue.offer(12)
          take  = queue.take.flatMap(ref.set)
          _     <- offer race take
          v     <- ref.get
        } yield assertTrue(v == 12)
      } +
        /**
         * EXERCISE
         *
         * Create a consumer of this queue that adds each value taken from the
         * queue to the counter, so the unit test can pass.
         */
        test("consumer") {
          for {
            counter <- Ref.make(0)
            queue   <- Queue.bounded[Int](100)
            _       <- ZIO.foreach(1 to 100)(v => queue.offer(v)).forkDaemon
            _       <- ZIO.foreach(1 to 100)(_ => queue.take.flatMap(i => counter.update(_ + i)))
            value   <- counter.get
          } yield assertTrue(value == 5050)
        } +
        /**
         * EXERCISE
         *
         * Queues are fully concurrent-safe on both producer and consumer side.
         * Choose the appropriate operator to parallelize the production side so
         * all values are produced in parallel.
         */
        test("multiple producers") {
          for {
            counter <- Ref.make(0)
            queue   <- Queue.bounded[Int](100)
            _       <- ZIO.foreachPar(1 to 100)(v => queue.offer(v)).forkDaemon
            _       <- queue.take.flatMap(v => counter.update(_ + v)).repeatN(99)
            value   <- counter.get
          } yield assertTrue(value == 5050)
        } +
        /**
         * EXERCISE
         *
         * Choose the appropriate operator to parallelize the consumption side so
         * all values are consumed in parallel.
         */
        test("multiple consumers") {
          for {
            counter <- Ref.make(0)
            queue   <- Queue.bounded[Int](100)
            _       <- ZIO.foreachPar(1 to 100)(v => queue.offer(v)).forkDaemon
            _       <- ZIO.foreachPar(1 to 100)(_ => queue.take.flatMap(v => counter.update(_ + v)))
            value   <- counter.get
          } yield assertTrue(value == 5050)
        } +
        /**
         * EXERCISE
         *
         * Shutdown the queue, which will cause its sole producer to be
         * interrupted, resulting in the test succeeding.
         */
        test("shutdown") {
          for {
            done   <- Ref.make(false)
            latch  <- Promise.make[Nothing, Unit]
            queue  <- Queue.bounded[Int](100)
            _      <- (latch.succeed(()) *> queue.offer(1).forever).ensuring(done.set(true)).fork
            _      <- latch.await
            _      <- queue.takeN(100)
            _      <- queue.shutdown
            isDone <- done.get.repeatWhile(_ == false).timeout(10.millis).some
          } yield assertTrue(isDone)
        }
    }
}

/**
 * ZIO's software transactional memory lets you create your own custom
 * lock-free, race-free, concurrent structures, for cases where there
 * are no alternatives in ZIO, or when you need to make coordinated
 * changes across many structures in a transactional way.
 */
object StmBasics extends DefaultRunnableSpec {
  def spec =
    suite("StmBasics") {
      test("latch") {

        /**
         * EXERCISE
         *
         * Implement a simple concurrent latch.
         */
        final case class Latch(ref: TRef[Boolean]) {
          // FIXME Isn't it active sleeping?
          def await: UIO[Any]   = ref.get.flatMap(STM.check(_)).commit
          def trigger: UIO[Any] = ref.set(true).commit
        }

        def makeLatch: UIO[Latch] = TRef.make(false).map(Latch(_)).commit

        for {
          latch  <- makeLatch
          waiter <- latch.await.fork
          _      <- Live.live(Clock.sleep(10.millis))
          first  <- waiter.poll
          _      <- latch.trigger
          _      <- Live.live(Clock.sleep(10.millis))
          second <- waiter.poll
        } yield assertTrue(first.isEmpty && second.isDefined)
      } +
        test("countdown latch") {

          /**
           * EXERCISE
           *
           * Implement a simple concurrent latch.
           */
          final case class CountdownLatch(ref: TRef[Int]) {
            def await: UIO[Any]     = ref.get.flatMap(v => STM.check(v == 0)).commit
            def countdown: UIO[Any] = ref.update(_ - 1).commit
          }

          def makeLatch(n: Int): UIO[CountdownLatch] = TRef.make(n).map(ref => CountdownLatch(ref)).commit

          for {
            latch  <- makeLatch(10)
            _      <- latch.countdown.repeatN(8)
            waiter <- latch.await.fork
            _      <- Live.live(Clock.sleep(10.millis))
            first  <- waiter.poll
            _      <- latch.countdown
            _      <- Live.live(Clock.sleep(10.millis))
            second <- waiter.poll
          } yield assertTrue(first.isEmpty && second.isDefined)
        } +
        test("permits") {

          /**
           * EXERCISE
           *
           * Implement `acquire` and `release` in a fashion the test passes.
           */
          final case class Permits(ref: TRef[Int]) {
            def acquire(howMany: Int): UIO[Unit] =
              ref.get.flatMap {
                case v if v >= howMany => ref.set(v - howMany)
                case _                 => STM.retry
              }.commit

            def release(howMany: Int): UIO[Unit] =
              ref.update(_ + howMany).commit
          }

          def makePermits(max: Int): UIO[Permits] = TRef.make(max).map(Permits(_)).commit

          for {
            counter <- Ref.make(0)
            permits <- makePermits(100)
            _ <- ZIO.foreachPar(1 to 1000)(_ =>
                  Random.nextIntBetween(1, 2).flatMap(n => permits.acquire(n) *> permits.release(n))
                )
            latch   <- Promise.make[Nothing, Unit]
            fiber   <- (latch.succeed(()) *> permits.acquire(101) *> counter.set(1)).forkDaemon
            _       <- latch.await
            _       <- Live.live(ZIO.sleep(1.second))
            _       <- fiber.interrupt
            count   <- counter.get
            permits <- permits.ref.get.commit
          } yield assertTrue(count == 0 && permits == 100)
        }
    }
}

/**
 * ZIO hubs are high-performance, asynchronous, lock-free structures backed
 * by hand-optimized ring buffers. Hubs are designed for broadcast scenarios
 * where multiple (potentially many) consumers need to access the same values
 * being published to the hub.
 */
object HubBasics extends DefaultRunnableSpec {
  def spec =
    suite("HubBasics") {

      /**
       * EXERCISE
       *
       * Use the `subscribe` method from 100 fibers to pull out the same values
       * from a hub, and use those values to increment `counter`.
       *
       * Take note of the synchronization logic. Why is this logic necessary?
       */
      test("subscribe") {
        for {
          counter <- Ref.make[Int](0)
          hub     <- Hub.bounded[Int](100)
          latch   <- TRef.make(100).commit
          scount  <- Ref.make[Int](0)
          _       <- (latch.get.retryUntil(_ <= 0).commit *> ZIO.foreach(1 to 100)(hub.publish(_))).forkDaemon
          _ <- ZIO.foreachPar(1 to 100)(_ =>
                hub.subscribe.use(queue =>
                  for {
                    _      <- latch.update(_ - 1).commit
                    values <- queue.takeN(100)
                    _      <- counter.update(_ + values.sum)
                  } yield ()
                )
              )
          value <- counter.get
        } yield assertTrue(value == 505000)
      }
    }
}

/**
 * GRADUATION PROJECT
 *
 * To graduate from this section, you will choose and complete one of the
 * following two problems:
 *
 * 1. Implement a bulkhead pattern, which provides rate limiting to a group
 *    of services to protect other services accessing a given resource.
 *
 * 2. Implement a CircuitBreaker, which triggers on too many failures, and
 *    which (gradually?) resets after a certain amount of time.
 */
object Graduation extends DefaultRunnableSpec {
  final case class CircuitBreaker(
    config: CircuitBreaker.Config,
    clock: Clock,
    counter: TRef[Int],
    state: TRef[CircuitBreaker.State]
  ) {
    import CircuitBreaker.State._

    private lazy val onSuccess: UIO[Unit] = state.get.flatMap {
      case Closed => STM.unit // ignore
      case Open   => counter.set(0)
      case HalfOpen =>
        counter.updateAndGet(_ + 1).flatMap {
          case value if value == config.successThreshold =>
            counter.set(0) *> state.set(Open)
          case _ => STM.unit
        }
    }.commit

    private lazy val scheduleHalfOpen: UIO[Unit] =
      (clock.sleep(config.timeout) *> state.set(HalfOpen).commit).forkDaemon.unit

    private lazy val onFailure: UIO[Unit] = state.get.flatMap {
      case Closed => STM.succeed(false)
      case HalfOpen =>
        counter.set(0) *> state.set(Closed) *> STM.succeed(true)
      case Open =>
        counter.updateAndGet(_ + 1).flatMap {
          case value if value == config.failureThreshold =>
            counter.set(0) *> state.set(Closed) *> STM.succeed(true)
          case _ => STM.succeed(false)
        }
    }.commit.flatMap(scheduleHalfOpen.when(_))

    def attempt[E, A](zio: IO[E, A])(rejected: IO[E, A]): IO[E, A] =
      state.get.commit.flatMap {
        case Closed => rejected
        case HalfOpen | Open =>
          zio.tapBoth(
            _ => onFailure,
            _ => onSuccess
          )
      }
  }

  object CircuitBreaker extends Accessible[CircuitBreaker] {
    final case class Config(
      successThreshold: Int,
      failureThreshold: Int,
      timeout: Duration
    )

    def attempt[E, A](zio: IO[E, A])(rejected: IO[E, A]): ZIO[Has[CircuitBreaker], E, A] =
      CircuitBreaker(_.attempt(zio)(rejected))

    sealed trait State extends Product with Serializable
    object State {
      final case object Open     extends State
      final case object HalfOpen extends State
      final case object Closed   extends State
    }
  }

  val successThreshold = 3
  val failureThreshold = 5
  val timeout          = Duration.fromMillis(100)
  lazy val testCircuitBreaker: ZLayer[Has[Clock], Nothing, Has[CircuitBreaker]] =
    (for {
      clock   <- ZIO.service[Clock]
      counter <- TRef.make[Int](0).commit
      state   <- TRef.make[CircuitBreaker.State](CircuitBreaker.State.Open).commit
      config = CircuitBreaker.Config(
        successThreshold,
        failureThreshold,
        timeout
      )
    } yield CircuitBreaker(config, clock, counter, state)).toLayer

  def spec =
    suite("CircuitBreaker") {
      sealed trait Error
      final case class Domain(msg: String) extends Error
      final case object Rejected           extends Error

      val succeed = CircuitBreaker.attempt(
        ZIO.succeed(())
      )(ZIO.fail(Rejected))

      val fail = CircuitBreaker
        .attempt[Error, Unit](
          ZIO.fail(Domain("Fail"))
        )(ZIO.fail(Rejected))
        .catchSome {
          case Domain("Fail") => ZIO.succeed()
        }

      val batchSuccess = ZIO.foreachPar(1 to 1000)(_ => succeed)

      val makeClosed = ZIO
        .foreachPar(1 to failureThreshold)(_ =>
          CircuitBreaker.attempt[Error, Unit](
            ZIO.fail(Domain("Make Closed"))
          )(ZIO.fail(Domain("Rejected on Make Closed")))
        )
        .catchSome {
          case Domain("Make Closed") => ZIO.succeed(())
        }

      val makeOpen = ZIO
        .foreachPar(1 to successThreshold)(_ => succeed)

      val waitHalfOpen = TestClock.adjust(timeout)

      val waitAndFail = waitHalfOpen *> fail

      test("bypass") {
        assertM(batchSuccess.exit)(succeeds(anything))
      } + test("reject") {
        val program = for {
          _ <- makeClosed
          _ <- succeed
        } yield ()

        assertM(program.exit)(fails(equalTo(Rejected)))
      } + test("half open") {
        val program = for {
          _    <- makeClosed
          rand <- Random.nextIntBetween(1, 100)
          _    <- waitAndFail.repeatN(rand - 1)
          _    <- waitHalfOpen
          _    <- batchSuccess
        } yield ()

        assertM(program.exit)(succeeds(anything))
      } + test("open") {
        val program = for {
          _    <- makeClosed
          rand <- Random.nextIntBetween(1, 100)
          _    <- waitAndFail.repeatN(rand - 1)
          _    <- waitHalfOpen
          _    <- makeOpen
          rand <- Random.nextIntBetween(0, failureThreshold - 1)
          _    <- fail.repeatN(rand - 1)
          _    <- batchSuccess
        } yield ()

        assertM(program.exit)(succeeds(anything))
      }
    }.provideCustomLayer(testCircuitBreaker)
}
