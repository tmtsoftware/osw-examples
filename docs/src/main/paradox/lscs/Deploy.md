#Segments Assembly and HCD Deployment

This is a development project and is not set up to deploy final products; therefore, it is necessary to check out the
repository and build the Assembly/HCD, etc. following typical sbt build, test, publish, etc.  See the CSW docs at at
[CSW GitHub](https://tmtsoftware.github.io/csw/index.html).

## Starting CSW Services

The test code starts any needed services, such as Location Service, during the tests, but if you want to test the
Assembly or HCD outside of the testing environment, you must start CSW Services. This is described 
[here](https://tmtsoftware.github.io/csw/apps/cswservices.html). This demonstration does not use many services, so
starting with -e is adequate.

```scala
csw-services start -e
```
@@@ warning
You can not run the tests *and* have csw-services also running. You will get errors.
@@@

## Launching With sbt

For demonstration or testing it is easiest to start the Assembly, HCD or both from the sbt build tool as shown
here. This can be done from the shell or within sbt.

There is a directory in the distribution called `lscs-deploy`. Within the directory is a resources file that contains 
three ComponentConfigurationFiles as described here:

| File | Description |
|------|-------------|
| SegmentsAssemblyStandalone.conf | Starts the Assembly only standalone |
| SegmentsHcdStandalone.conf | Starts the HCD only standalone |
| SegmentsContainer.conf | Starts both the Assembly and HCD in a container |

The third option that starts a container with Assembly and HCD is the most useful when testing externally. 

To launch the container from the command line, type:

```scala
sbt "lscs-deploy/runMain m1cs.segments.deploy.SegmentsContainerCmdApp --local src/main/resources/SegmentsContainer.conf"
```
From within sbt use the runMain option:
```scala
lscs-deploy/runMain m1cs.segments.deploy.SegmentsContainerCmdApp --local src/main/resources/SegmentsContainer.conf
```
In this case, both the Assembly and HCD are started within the same process in a CSW container, which does little other
than manage the creation and shutdown of the components.  There is no performance penalty for using a container.

To launch a standalone conf you must add the -standalone option, but everything else is the same:

```scala
sbt "lscs-deploy/runMain m1cs.segments.deploy.SegmentsContainerCmdApp --standalone --local src/main/resources/SegmentsContainer.conf"
```

