/**
 * TESTING
 *
 * To facilitate robust testing of ZIO applications, ZIO includes a testkit
 * called _ZIO Test_. ZIO Test is a lightweight but rich testing environment
 * that seamlessly integrates with ZIO, providing access to all of ZIO's
 * power from within unit tests and full testability for all ZIO services.
 *
 * In this module, you will explore how you can use _ZIO Test_ to write
 * powerful unit, integration, and system tests that ensure your ZIO
 * applications perform correctly in production.
 */
package advancedzio.testing

import zio._
import zio.test._
import zio.test.TestAspect._
import zio.test.environment._
import zio.test.Assertion._

/**
 * SPECS
 *
 * ZIO Test specs are fully-functional, executable programs. They can be run
 * without an IDE, because they all have a main function. If you are using the
 * SBT test runner or the IntelliJ IDEA ZIO plug-in, you can also run your tests
 * directly from SBT or your IDE.
 */
object SimplestSpec extends DefaultRunnableSpec {

  /**
   * EXERCISE
   *
   * Using sbt or your IDE, run `SpecBasics` by using its `main` function (not the test runner).
   */
  def spec = suite("SimplestSpec")()
}

/**
 * ASSERTIONS
 *
 * ZIO Test operates using assertions, which use macros to provide very
 * powerful error messages and reporting. Assertions produce values,
 * which compose using a variety of operators.
 */
object BasicAssertions extends DefaultRunnableSpec {
  def spec = suite("BasicAssertions") {
    trait Building {
      def contents: String
    }
    object House extends Building {
      def contents = "bed, coffee pot, kitchen"
    }
    object Barn extends Building {
      def contents = "hay, goats, feed"
    }
    object Shed extends Building {
      def contents = "needle, broom"
    }

    val buildings = List(House, Barn, Shed)

    test("2 + 2 == 4") {

      /**
       * EXERCISE
       *
       * Using `assertTrue`, assert that 2 + 2 == 4.
       */
      assertTrue(2 + 2 == 4)
    } +
      test("sherlock misspelling") {

        /**
         * EXERCISE
         *
         * Examine the output of this failed test. Then fix the test so that it passes.
         */
        assertTrue("sherlock".contains("lock"))
      } +
      test("multiple assertions") {
        val string = "cannac"

        /**
         * EXERCISE
         *
         * Using the `&&` operator of `Assert`, verify the following properties
         * about `string`:
         *
         *  - The string is 6 letters in length
         *  - the string starts with "can"
         *  - the reverse of the string is equal to itself
         */
        assertTrue(string.length() == 6) &&
        assertTrue(string.startsWith("can")) &&
        assertTrue(string.reverse == string)
      } +
      /**
       * EXERCISE
       *
       * Using `+`, add another test to the suite, which you can create with
       * `test`, as above. This test should verify that the contents of one
       * of the buildings in `buildings` contains a `needle`.
       */
      test("exists needle") {
        assertTrue(buildings.exists(_.contents.contains("needle")))
      }
  }
}

/**
 * ZIO ASSERTIONS
 *
 * Most assertions in ZIO Test will be effectful, rather than pure. Using the
 * same syntax, ZIO lets you write effectful tests.
 */
object BasicAssertionsZIO extends DefaultRunnableSpec {
  def spec = suite("BasicAssertionsZIO") {
    test("incrementing a ref") {

      /**
       * EXERCISE
       *
       * Using `assertTrue`, assert that incrementing a zero-valued ref by one
       * results in 1.
       */
      for {
        ref <- Ref.make(0)
        v   <- ref.updateAndGet(_ + 1)
      } yield assertTrue(v == 1)
    } +
      test("multiple assertions") {

        /**
         * EXERCISE
         *
         * Using the `&&` operator of `Assert`, verify the following properties
         * about `v`:
         *
         *  - It is an even number
         *  - It is greater than 0
         */
        for {
          ref  <- Ref.make(0)
          rand <- Random.nextIntBetween(1, 4)
          v    <- ref.updateAndGet(_ + 1).repeatN(rand * 2)
          // it's actually odd
        } yield assertTrue(v % 2 == 1) && assertTrue(v > 0)
      }
  }
}

/**
 * TEST ASPECTS
 *
 * ZIO Test offers _test aspects_, which are values that allow modifying specs,
 * whether suites or individual tests. Test aspects are kind of like annotations,
 * except they are type-safe, non-magical, and first class values that can be
 * trasnformed and composed with other test aspects.
 *
 * Test aspects can add features like retrying tests, ignoring tests, running
 * tests only on a certain platform, and so forth.
 */
object BasicTestAspects extends DefaultRunnableSpec {
  import zio.test.TestAspect._

  def spec = suite("BasicTestAspects") {
    test("ignore") {

      /**
       * EXERCISE
       *
       * Using `TestAspect.ignore`, add the `ignore` aspect to this test so that
       * the failure is ignored.
       */
      assertTrue(false)
    } @@ ignore +
      test("flaky") {

        /**
         * EXERCISE
         *
         * Using `TestAspect.flaky`, mark this test as flaky so that it will pass so
         * long as it sometimes succeeds.
         */
        for {
          number <- Random.nextInt
        } yield assertTrue(number % 2 == 0)
      } @@ flaky +
      test("nonFlaky") {

        /**
         * EXERCISE
         *
         * Using `TestAspect.nonFlaky`, mark this test as non-flaky so that ZERO
         * failures are permitted.
         */
        for {
          number <- Random.nextIntBetween(0, 100)
        } yield assertTrue(number * 2 % 2 == 0)
      } @@ nonFlaky +
      /**
       * EXERCISE
       *
       * Add the `sequential` aspect to this suite and observe the change in
       * output to the console.
       */
      suite("sequential") {
        test("Test 1") {
          for {
            _ <- Live.live(ZIO.sleep(10.millis))
            _ <- Console.printLine("Test 1")
          } yield assertTrue(true)
        } +
          test("Test 2") {
            for {
              _ <- Console.printLine("Test 2")
            } yield assertTrue(true)
          }
      } @@ sequential
  }
}

/**
 * TEST FIXTURES
 *
 * ZIO can execute arbitrary logic before, after, or before and after
 * tests individually, or all tests in a suite. This ability is sometimes
 * used for "test fixtures", which allow developers to perform custom
 * setup / tear down operations required for running tests.
 */
object TestFixtures extends DefaultRunnableSpec {
  val beforeRef = new java.util.concurrent.atomic.AtomicInteger(0)
  val aroundRef = new java.util.concurrent.atomic.AtomicInteger(0)

  val incBeforeRef: UIO[Any] = UIO(beforeRef.incrementAndGet())

  def spec = suite("TestFixtures") {

    /**
     * EXERCISE
     *
     * Using `TestAspect.before`, ensure the `incBeforeRef` effect is executed
     * prior to the start of the test.
     */
    test("before") {
      for {
        value <- UIO(beforeRef.get)
      } yield assertTrue(value > 0)
    } @@ before(incBeforeRef) +
      /**
       * EXERCISE
       *
       * Using `TestAspect.after`, ensure the message `done with after` is printed
       * to the console using `ZIO.debug`.
       */
      test("after") {
        for {
          _ <- Console.printLine("after")
        } yield assertTrue(true)
      } @@ after(ZIO.debug("done with after")) +
      /**
       * EXERCISE
       *
       * Using `TestAspect.around`, ensure the `aroundRef` is incremented before and
       * decremented after the test.
       */
      test("around") {
        for {
          value <- UIO(aroundRef.get)
        } yield assertTrue(value == 1)
      } @@ around(
        UIO(aroundRef.incrementAndGet()),
        UIO(aroundRef.decrementAndGet())
      )
  }
}

/**
 * TEST SERVICES
 *
 * By default, ZIO tests use test versions of all the standard services
 * baked into ZIO, including Random, Clock, System, and Console.
 * These allow you to programmatically control the services, such as
 * adjusting time, setting up fake environment variables, or inspecting
 * console output or providing console input.
 */
object TestServices extends DefaultRunnableSpec {
  def spec =
    suite("TestServices") {

      /**
       * EXERCISE
       *
       * Using `TestClock.adjust`, ensure this test passes without timing out.
       */
      test("TestClock") {
        for {
          fiber <- Clock.sleep(1.second).as(42).fork
          _     <- TestClock.adjust(1.second)
          value <- fiber.join
        } yield assertTrue(value == 42)
      } +
        /**
         * EXERCISE
         *
         * Using `TestSystem.setEnv`, set an environment variable to make the
         * test pass.
         */
        test("TestSystem") {
          for {
            _    <- TestSystem.putEnv("name", "Sherlock Holmes")
            name <- System.env("name").some
          } yield assertTrue(name == "Sherlock Holmes")
        } +
        /**
         * EXERCISE
         *
         * Using `TestConsole.feedLines`, feed a name into the console such that
         * the following test passes.
         */
        test("TestConsole") {
          for {
            _    <- TestConsole.feedLines("Sherlock Holmes")
            _    <- Console.printLine("What is your name?")
            name <- Console.readLine
          } yield assertTrue(name == "Sherlock Holmes")
        } +
        /**
         * EXERCISE
         *
         * Using `TestRandom.feedInts`, feed the integer 5 into the Random
         * generator so the test will pass.
         */
        test("TestRandom") {
          for {
            _      <- TestRandom.feedInts(5)
            _      <- TestConsole.feedLines("5")
            number <- Random.nextInt
            _      <- Console.printLine("Guess a random number between 0 - 10: ")
            guess  <- Console.readLine
            result <- if (guess == number.toString) Console.printLine("Good job!").as(true)
                     else Console.printLine("Try again!").as(false)
          } yield assertTrue(result)
        } +
        /**
         * EXERCISE
         *
         * Some times it is necessary to run code against a live standard
         * service, rather than one of the test services baked into ZIO Test.
         * A useful function for doing this is `Live.live`, which will ensure
         * the provided effect runs using the live services.
         */
        test("Live") {
          for {
            now <- Live.live(Clock.instant.map(_.getEpochSecond()))
          } yield assertTrue(now > 0)
        }
    }
}

/**
 * INTEGRATION/SYSTEM ASPECTS
 *
 * Some ZIO Test aspects are designed for more advanced integration and system
 * tests.
 */
object IntegrationSystem extends DefaultRunnableSpec {

  /**
   * EXERCISE
   *
   * Explore jvmOnly, windows, linux, ifEnv, and other test aspects that
   * are useful for running platform-specific or integration / system tests.
   */
  import zio.test.Assertion._

  def spec = suite("IntegrationSystem") {
    test("ifEnv") {
      for {
        name <- System.env("name").some
      } yield assertTrue(name == "Sherlock Holmes")
    } @@ ifEnv("name", containsString("Sherlock Holmes"))
  }
}

/**
 * CUSTOM LAYERS
 *
 * The code you are testing may use its own layers, to provide access to
 * other services required by your application. This is especially true
 * for business logic, which may be assembled from high-level layers
 * that allow expressing business logic in a direct style.
 *
 * ZIO Test allows you to provide custom layers in a variety of ways
 * to your tests.
 */
object CustomLayers extends DefaultRunnableSpec {
  final case class User(id: String, name: String, age: Int)

  trait UserRepo {
    def getUserById(id: String): Task[Option[User]]
    def updateUser(user: User): Task[Unit]
  }
  object UserRepo extends Accessible[UserRepo] {
    def getUserById(id: String): RIO[Has[UserRepo], Option[User]] =
      UserRepo(_.getUserById(id))

    def updateUser(user: User): RIO[Has[UserRepo], Unit] =
      UserRepo(_.updateUser(user))
  }

  final case class TestUserRepo(ref: Ref[Map[String, User]]) extends UserRepo {

    /**
     * EXERCISE
     *
     * Implement the following method of the user repo to operate on the
     * in-memory test data stored in the Ref.
     */
    def getUserById(id: String): Task[Option[User]] =
      ref.get.map(_.get(id))

    /**
     * EXERCISE
     *
     * Implement the following method of the user repo to operate on the
     * in-memory test data stored in the Ref.
     */
    def updateUser(user: User): Task[Unit] =
      ref.update(_.updated(user.id, user))
  }

  /**
   * EXERCISE
   *
   * Create a test user repo layer and populate it with some test data.
   */
  lazy val testUserRepo: ULayer[Has[UserRepo]] =
    (for {
      ref <- Ref.make(
              Map(
                "x1" -> User("x1", "John", 42),
                "y2" -> User("y2", "Diana", 21)
              )
            )
    } yield TestUserRepo(ref)).toLayer

  def spec =
    suite("CustomLayers") {
      test("provideCustomLayer") {

        /**
         * EXERCISE
         *
         * In order to complete this exercise, you will have to make several
         * changes. First, use `UserRepo.getUserById` to retrieve the user
         * associated with the id. Then check the age is 42. To make the
         * test compile, you will have to `provideCustomLayer` on the test.
         * Finally, to make the test pass, you will have to create test
         * data matches your test expectations.
         */
        for {
          user <- UserRepo.getUserById("x1").some
        } yield assertTrue(user.age == 42)
      }.provideCustomLayer(testUserRepo) +
        /**
         * EXERCISE
         *
         * Layers can be shared across all the tests in a suite.
         *
         * Use `provideCustomLayerShared` to provide a layer that is shared
         * across both of the following (sequentially executed) tests. Then
         * add a user in the first test that is then retrieved in the second.
         */
        suite("shared layer") {
          val user = User("z3", "Maria", 32)
          test("adding a user") {
            for {
              _ <- UserRepo.updateUser(user)
            } yield assertTrue(true)
          } + test("getting a user") {
            for {
              repo_user <- UserRepo.getUserById(user.id)
            } yield assertTrue(repo_user.contains(user))
          }
        }.provideCustomLayerShared(testUserRepo) @@ sequential
    }
}

/**
 * GRADUATION PROJECT
 *
 * To graduate from this section, you will choose and complete one of the
 * following projects under the assistance of the instructor:
 *
 * 1. Implement a custom `TestAspect` that provides features or functionality
 *    you might like to use in your own unit tests.
 *
 * 2. Design an `EmailService` for sending emails. Then create a test
 *    implementation that allows simulating failures and successes, and
 *    which captures sent emails for purposes of testing. Finally,
 *    create a layer for the test email service and use it in a test.
 *
 */
object Graduation extends DefaultRunnableSpec {
  final case class Email(from: String, to: String, text: String)

  trait EmailService {
    def send(email: Email): Task[Unit]
  }

  object EmailService extends Accessible[EmailService] {
    def send(email: Email): RIO[Has[EmailService], Unit] =
      EmailService(_.send(email))
  }

  final case class TestEmailService(
    emails: Ref[List[(Boolean, Email)]],
    commands: Ref[List[(Boolean, Int)]]
  ) extends EmailService {

    override def send(email: Email): Task[Unit] =
      for {
        success <- commands.modify {
                    case (success, n) :: tail =>
                      (
                        success,
                        if (n == 1) tail
                        else (success, n - 1) :: tail
                      )
                    case Nil => (true, Nil)
                  }
        _ <- emails.update(
              (success, email) :: _
            )
        _ <- if (success) ZIO.succeed()
            else ZIO.fail(new RuntimeException())
      } yield ()

    def nextResults(success: Boolean, times: Int): Task[Unit] =
      for {
        _ <- commands.update(
              _ :+ (success, times)
            )
      } yield ()

    def getResults: Task[List[(Boolean, Email)]] =
      emails.get
  }

  object TestEmailService extends Accessible[TestEmailService] {
    def nextResults(success: Boolean, times: Int): RIO[Has[TestEmailService], Unit] =
      TestEmailService(_.nextResults(success, times))

    def getResults: RIO[Has[TestEmailService], List[(Boolean, Email)]] =
      TestEmailService(_.getResults)
  }

  lazy val testEmailService: ULayer[Has[TestEmailService] with Has[EmailService]] =
    (
      for {
        emails   <- Ref.make[List[(Boolean, Email)]](List.empty)
        commands <- Ref.make[List[(Boolean, Int)]](List.empty)
        service  = TestEmailService(emails, commands)
      } yield Has.allOf[EmailService, TestEmailService](service, service)
    ).toLayerMany

  def spec = suite("Graduation") {
    test("aspect") {
      val email: Email = Email("John", "Maria", "Hello")

      val program: RIO[Has[EmailService], Unit] =
        for {
          _ <- EmailService.send(email).retryN(5)
        } yield ()

      val expectedResults = List.fill(4)((false, email)) :+ (true, email)

      for {
        _       <- TestEmailService.nextResults(false, 4)
        _       <- program
        results <- TestEmailService.getResults
      } yield assert(results)(hasSameElements(expectedResults))
    }.provideCustomLayer(testEmailService)
  }
}
