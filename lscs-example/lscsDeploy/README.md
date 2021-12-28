# lscs-deploy

This subproject provides the `lscsDeploy` command that is used to run the assemblies and HCDs defined in ../lscsComps.

The build supports creating a single jar (using the [sbt-assembly](https://github.com/sbt/sbt-assembly) plugin) and
creating a native app, using [Graalvm Native Image](https://www.graalvm.org/).
The [sbt-native-packager](https://github.com/sbt/sbt-native-packager) plugin can also be used
to create a local app with start script (using `sbt stage`).

## Building and running the single jar version

To build the single jar, run:

    sbt lscsDeploy/assembly

Then you can run a container as follows:

    cd lscs-example/lscsDeploy
    java -jar target/scala-2.13/lscsDeploy-assembly-0.1.0-SNAPSHOT.jar --local src/main/resources/SegmentsContainer.conf

### Possible risks of using single jar

Note that in order to build the single jar file, conflicts need to be resolved for cases where
there are multiple versions of dependencies in use, or multiple resource files with the same path, etc.
The settings used here attempt to merge config files and otherwise just take the first version of a
dependency. This may not always be correct. See [sbt-assembly](https://github.com/sbt/sbt-assembly)
for more information.

## Building a native app

To build the native application (which will only run on the OS that it is built on), 
first install [GraalVM](https://www.graalvm.org/) and either make it the default java/javac version
(using `update-alternatives --config java` for example on Linux) 
or pass the `-java-home` option to the sbt commands (to override the default java version):

Run this to build the native app (This will take a while...):

    sbt lscsDeploy/graalvm-native-image:packageBin

or 

    sbt -java-home $GRAALHOME lscsDeploy/graalvm-native-image:packageBin

Then, to run the native application:

    cd lscs-example/lscsDeploy
    target/graalvm-native-image/lscsDeploy --local src/main/resources/SegmentsContainer.conf

Note that this version suffers the same possible issues as the single jar version.
Conflicts in resource files and classes have to be resolved at compile time and
there are limitations regarding reflection.

## Building an application directory with start script

You can also use the standard [sbt-native-packager](https://github.com/sbt/sbt-native-packager) targets
to build a release directory (or possibly an RPM, DEB or DMG file). For example:

    sbt stage

and then to run:

    cd lscs-example/lscsDeploy
    target/universal/stage/bin/lscsdeploy --local src/main/resources/SegmentsContainer.conf


## Notes on the build

For future reference, here are some notes about building a single jar or native app, based on Scala and Akka:

### Generating the files in the ./configs dir:

The files in the ./configs dir are referenced in build.sbt settings and generated using these commands:
```
sbt lscsDeploy/assembly
mkdir configs
$GRAALHOME/bin/java -agentlib:native-image-agent=config-output-dir=./configs \
    -jar lscsDeploy-assembly-0.1.0-SNAPSHOT.jar 
    --local src/main/resources/SegmentsContainer.conf
```

This assumes that the application executes all the branches of code needed to determine which classes are loaded 
using reflection, which resource files are accessed, etc. The last command generates the JSON
files under ./configs that are used to configure the native image build.

### Other Useful Information:

* Useful information and examples:
  See https://www.vandebron.tech/blog/building-native-images-and-compiling-with-graalvm-and-sbt

* More details
  See https://stackoverflow.com/questions/67987247/graalvm-native-image-reflection-doesnt-work

* Comparison:
  See https://medium.com/virtuslab/revisiting-scala-native-performance-67029089f241

* Issues:
  - Resources not loaded by default
  - Conflicts (already with sbt-assembly)
  - Reflection (Artery akka serialization)
  - slow builds
  - Various issues with akka:
    See https://github.com/vmencik/akka-graal-config
    and https://github.com/vmencik/akka-graal-native
  - akka actor issues: See https://github.com/vmencik/akka-graal-native/issues/8
    "the self/context fields in an actor are reflected on not just when watching, but also when the actor is restarted"
  - akka graalvm discussion: https://discuss.lightbend.com/t/akka-and-graal-s-native-image-tool/940
  - Issue with JDK Flight Recorder (JFR) with Native Image
    See https://www.graalvm.org/reference-manual/native-image/JFR/ for solution

