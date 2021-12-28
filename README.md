## OSW Examples
This repository holds examples of CSW and ESW code with the goal of providing more comprehensive examples 
and best practices. This repository will be developed more fully with time.

*This is a work in progress and is currently not uniformly ready for consumption.*

### Examples

* command-example

There are three supplemental versions of the tutorial Assembly and HCD called: basic, moderate, and full. The
basic version is similar to the tutorial example here with possible best practices. Two other versions are included that introduce
ways of programming components along with increasing functionality.

* lscs-example

This is an example of an end-to-end approach to the Assembly and HCD needed for M1CS.

Note: the [lscs-example/lscsDeploy](lscs-example/lscsDeploy) subproject supports creating
a single jar (using the [sbt-assembly](https://github.com/sbt/sbt-assembly) plugin) and
creating a native app, using [Graalvm Native Image](https://www.graalvm.org/). 
The [sbt-native-packager](https://github.com/sbt/sbt-native-packager) plugin can also be used
to create a local app with start script (using `sbt stage`).
See [lscs-example/lscsDeploy/README.md](lscs-example/lscsDeploy/README.md) for build details.

### Documentation

Documentation for osw-examples can be found [here](https://tmtsoftware.github.io/osw-examples).


