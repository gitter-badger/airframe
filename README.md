# Airframe  [![Gitter Chat][gitter-badge]][gitter-link] [![CircleCI][circleci-badge]][circleci-link] [![Coverage Status][coverall-badge]][coverall-link]

[circleci-badge]: https://circleci.com/gh/wvlet/airframe.svg?style=svg
[circleci-link]: https://circleci.com/gh/wvlet/airframe
[gitter-badge]: https://badges.gitter.im/Join%20Chat.svg
[gitter-link]: https://gitter.im/wvlet/wvlet?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge
[coverall-badge]: https://coveralls.io/repos/github/wvlet/airframe/badge.svg?branch=master
[coverall-link]: https://coveralls.io/github/wvlet/airframe?branch=master

Airframe is a dependency injection (DI) library tailored to Scala. While Google's [Guice](https://github.com/google/guice) is designed for injecting Java objects (e.g., using constructors or providers), Airframe redesigned it for Scala traits so that we can mix-in traits that have several object dependencies.

Airframe can be used in three steps:
- ***Bind***: Inject necessary classes with `bind[X]`: 
```scala
import wvlet.airframe._

trait App {
  val x = bind[X]
  val y = bind[Y]
  val z = bind[Z]
  // Do something with X, Y, and Z
}
```
- ***Design***: Describe how to provide object instances:
```scala
val design : Design = 
   newDesign
     .bind[X].toInstance(new X)  // Bind type X to a concrete instance
     .bind[Y].toSingleton        // Bind type Y to a singleton object
     .bind[Z].to[ZImpl]          // Bind type Z to an instance of ZImpl
```
Note that *Design* is *immutable*, so you can safely reuse and extend it.

- ***Build***: Create a concrete instance:
```scala
val session = design.newSession
val app : App = session.build[App]
```

Airframe builds an instance of `App` based on the binding rules specified in the *Design* object.
`Session` manages the lifecycle of objects generated by Airframe. For example, singleton objects that are instantiated within a Session will be discarded when `Session.shutdown` is called.

The major advantages of Airframe include:
- Simple usage. Just `import wvlet.airframe._` and do the above three steps to enjoy DI in Scala! 
- *Design* remembers how to build complex objects on your behalf.
  - For example, you can avoid code duplications in your test and production codes. Compare writing `new App(new X, new Y(...), new Z(...), ...)` every time and just calling `session.build[App]`.
  - When writing application codes, you only need to care about how to ***use*** objects, rather than how to ***provide*** them. *Design* already knows how to provide objects to your class.
- You can enjoy the flexibility of Scala traits and dependency injection (DI) at the same time.
  - Mixing traits is far easier than calling object constructors. This is because traits can be combined in an arbitrary order. So you no longer need to remember the order of the constructor arguments.
- Scala macro based binding generation. 
- Scala 2.11, 2.12 support.

# Usage

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.wvlet/airframe_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.wvlet/airframe_2.11)
- [Release Notes](https://github.com/wvlet/airframe/blob/master/RELEASE_NOTES.md) 

**build.sbt** 
```
libraryDependencies += "org.wvlet" %% "airframe" % "(version)"
```

## Binding Examples

This example shows all binding types available in Airframe:

```scala
import wvlet.airframe._
import BindingExample._

trait BindingExample {
  val a = bind[A]  // Inject A
  val b = bind[B]  // Inject B

  val s = bindSingleton[S] // Inject S as a singleton

  val p0: P = bind { P() } // Inject P using the provider function (closure)
  val p1: P = bind { d1:D1 => P(d1) } // Inject D1 to create P
  val p2: P = bind { (d1:D1, d2:D2) => P(d1, d2) } // Inject D1 and D2 to create P
  val p3: P = bind { (d1:D1, d2:D2, d3:D3) => P(d1, d2, d3) } // Inject D1, D2 and D3

  val pd: P = bind { provider _ } // Inject D1, D2 and D3 to call a provider function
  val ps: P = bindSingleton { provider _ } // Create a singleton using a provider 
}

object BindingExample {
  case class P(d1:D1 = D1(), d2:D2 = D2(), d3:D3 = D3())
  def provider(d1:D1, d2:D2, d3:D3) : P = P(d1, d2, d3)
}
```

## Design Examples

To configure actual bindings, define object bindings using **design**:

```scala
import wvlet.airframe._
// If you define multiple bindings to the same type, the last one will be used.
val design : Design =
  newDesign                      // Create an empty design
  .bind[A].to[AImpl]             // Bind a concrete class AImpl to A
  .bind[B].toInstance(new B(1))  // Bind a concrete instance to B (This instance will be a singleton)
  .bind[S].toSingleton           // S will be a singleton within the session
  .bind[ES].toEagerSingleton     // ES will be initialized as a singleton at session start time
  .bind[D1].toInstance(D1(1))    // Bind D1 to a concrete instance D1(1)
  .bind[D2].toInstance(D2(2))    // Bind D2 to a concrete instance D2(2)
  .bind[D3].toInstance(D3(3))    // Bind D3 to a cocreete instance D3(3)
  .bind[P].toProvider{ d1:D1 => P(d1) } // Create P by resolveing D1 from the design to create P
  .bind[P].toProvider{ (d1:D1, d2:D2) => P(d1, d2) } // Resolve D1 and D2
  .bind[P].toProvider{ provider _ }  // Use a function as a provider. D1, D2 and D3 will be resolved from the design
  .bind[P].toSingletonProvider{ d1:D1 => P(d1) } // Create a singleton using the provider function
  .bind[P].toEagerSingletonProvider{ d1:D1 => P(d1) } // Create an eager singleton using the provider function

// Start a session
val session = desing.newSession
try {
  session.start
  val p = session.build[P]
}
finally {
   session.shutdown
}
```

## Life Cycle Management

Server-side applications often require object initializtaion, server start, and shutdown hooks. 
Airframe has a built-in object life cycle manager to implement these hooks:

```scala
// Your server application
trait Server {
  def init() {}
  def start() {}
  def stop() {}
}

// When binding an object, you can define life cycle hooks to the injected object:
trait MyServerService {
  val service = bind[Server].withLifeCycle(
    init = { _.init },    // Called when the object is injected
    start = { _.start },  // Called when sesion.start is called
    shutdown = { _.stop } // Called when session.shutdown is called
  )
}
```

## Debugging Airframe Binding and Injection

To check the runtime behaviour of Airframe object injection, set the log level of `wvlet.airframe`
to `debug` or `trace`:

**src/main/resources/log.properties**
```
wvlet.airframe=debug
```

While debugging the code in your test cases, you can also use `log-test.properties` file:
**src/test/resources/log-test.properties**
```
wvlet.airframe=debug
```
See [wvlet-log configuration](https://github.com/wvlet/log#configuring-log-levels) for the details of log level configurations.


Then you will see the log messages that show the object bindings and injection activities:
```
2016-12-29 22:23:17-0800 debug [Design] Add binding: ProviderBinding(DependencyFactory(Wing@@Left,List(),wvlet.airframe.LazyF0@e9c510cf),true,true)  - (Design.scala:43)
2016-12-29 22:23:17-0800 debug [Design] Add binding: ProviderBinding(DependencyFactory(Wing@@Right,List(),wvlet.airframe.LazyF0@4678272f),true,true)  - (Design.scala:43)
2016-12-29 22:23:17-0800 debug [Design] Add binding: ProviderBinding(DependencyFactory(PlaneType,List(),wvlet.airframe.LazyF0@442b0f),true,true)  - (Design.scala:43)
2016-12-29 22:23:17-0800 debug [Design] Add binding: ProviderBinding(DependencyFactory(Metric,List(),wvlet.airframe.LazyF0@1595a8db),true,true)  - (Design.scala:43)
2016-12-29 22:23:17-0800 debug [Design] Add binding: ClassBinding(Engine,GasolineEngine)  - (Design.scala:43)
2016-12-29 22:23:17-0800 debug [Design] Add binding: ProviderBinding(DependencyFactory(PlaneType,List(),wvlet.airframe.LazyF0@b24c12d8),true,true)  - (Design.scala:43)
2016-12-29 22:23:17-0800 debug [Design] Add binding: ClassBinding(Engine,SolarHybridEngine)  - (Design.scala:43)
2016-12-29 22:23:17-0800 debug [SessionBuilder] Creating a new session: session:7bf38868  - (SessionBuilder.scala:48)
2016-12-29 22:23:17-0800 debug [SessionImpl] [session:7bf38868] Initializing  - (SessionImpl.scala:48)
2016-12-29 22:23:17-0800 debug [SessionImpl] [session:7bf38868] Completed the initialization  - (SessionImpl.scala:55)
2016-12-29 22:23:17-0800 debug [SessionImpl] Get or update dependency [AirPlane]  - (SessionImpl.scala:80)
2016-12-29 22:23:17-0800 debug [SessionImpl] Get dependency [wvlet.obj.tag.@@[example.Example.Wing,example.Example.Left]]  - (SessionImpl.scala:60)
2016-12-29 22:23:17-0800 debug [SessionImpl] Get dependency [wvlet.obj.tag.@@[example.Example.Wing,example.Example.Right]]  - (SessionImpl.scala:60)
2016-12-29 22:23:17-0800 debug [SessionImpl] Get dependency [example.Example.Engine]  - (SessionImpl.scala:60)
2016-12-29 22:23:17-0800 debug [SessionImpl] Get or update dependency [Fuel]  - (SessionImpl.scala:80)
2016-12-29 22:23:17-0800 debug [SessionImpl] Get dependency [example.Example.PlaneType]  - (SessionImpl.scala:60)
2016-12-29 22:23:17-0800 debug [SessionImpl] Get dependency [example.Example.Metric]  - (SessionImpl.scala:60)
```

# More Illustrative Examples

Here is a more illustrative usage example of Airframe.
You can find the whole code used here from [AirframeTest](https://github.com/wvlet/airframe/blob/master/airframe/src/test/scala/wvlet/airframe/AirframeTest.scala).

In this example, we will create a service that prints a greeting at random:

```scala
import wvlet.airframe._ 
import wvlet.log.LogSupport

trait Printer {
  def print(s: String): Unit
}

// Concrete classes which will be bound to Printer
class ConsolePrinter(config: ConsoleConfig) extends Printer { 
  def print(s: String) { println(s) }
}
class LogPrinter extends Printer with LogSupport { 
  def print(s: String) { info(s) }
}

class Fortune { 
  def generate: String = { /** generate random fortune message **/ }
}
```

## Local variable binding

Using local variables is the simplest way to binding objects:

```scala
trait FortunePrinterEmbedded {
  val printer = bind[Printer]
  val fortune = bind[Fortune]

  printer.print(fortune.generate)
}
```

## Reuse bindings with mixin

To reuse bindings, we can create XXXService traits and mix-in them to build a complex object. 

```scala
import wvlet.airframe._

trait PrinterService {
  val printer = bind[Printer] // It can bind any Printer types
}

trait FortuneService {
  val fortune = bind[Fortune]
}

trait FortunePrinterMixin extends PrinterService with FortuneService {
  printer.print(fortune.generate)
}
```

It is also possible to manually inject an instance implementation. This is useful for changing the behavior of objects for testing: 
```scala
trait CustomPrinterMixin extends FortunePrinterMixin {
  override val printer = new Printer { def print(s:String) = { Console.err.println(s) } } // Manually inject an instance
}
```

## Tagged binding

Airframe can provide separate implementations to the same type object by using object tagging (@@):
```scala
import wvlet.obj.tag.@@
case class Fruit(name: String)

trait Apple
trait Banana

trait TaggedBinding {
  val apple  = bind[Fruit @@ Apple]
  val banana = bind[Fruit @@ Banana]
}
 ```

Tagged binding is also useful to inject primitive type values:
```scala
trait Env

trait MyService {
  // Coditional binding
  lazy val threadManager = bind[String @@ Env] match {
     case "test" => bind[TestingThreadManager] // prepare a testing thread manager
     case "production" => bind[ThreadManager] // prepare a thread manager for production
  }
}

val coreDesign = newDesign
val testingDesign = coreDesign.bind[String @@ Env].toInstance("test")
val productionDesign = coreDesign.bind[String @@ Env].toInstance("production")
```

## Object Injection

Before binding objects, you need to define a `Design` of dependent components. It is similar to `modules` in Guice.

```scala
val design = newDesign
  .bind[Printer].to[ConsolePrinter]  // Airframe will generate an instance of ConsolePrinter by resolving its dependencies
  .bind[ConsoleConfig].toInstance(ConsoleConfig(System.err)) // Binding an actual instance
```

You can also define bindings to the tagged objects:

```scala
val design = newDesign
  .bind[Fruit @@ Apple].toInstance(Fruit("apple"))
  .bind[Fruit @@ Banana].toInstance(Fruit("banana"))
  .bind[Fruit @@ Lemon].toInstance(Fruit("lemon"))
````

To bind a class to a singleton, use `toSingleton`:

```scala
class HeavyObject extends LogSupport { /** */ }

val design = newDesign
  .bind[HeavyOBject].toSingleton
````

We can create an object from a design by using `newSession.build[X]`:

```
design.newSession.build[FortunePrinterMixin]
```

See more detail in [AirframeTest](https://github.com/wvlet/airframe/blob/master/airframe/src/test/scala/wvlet/airframe/AirframeTest.scala).

# LICENSE

[Apache v2](https://github.com/wvlet/airframe/blob/master/LICENSE)
