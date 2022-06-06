- [Developer Guide](#developer-guide)
  - [Forking and Cloning](#forking-and-cloning)
  - [Install Prerequisites](#install-prerequisites)
    - [JDK 14](#jdk-14)
  - [Building](#building)
  - [Using IntelliJ IDEA](#using-intellij-idea)
  - [Submitting Changes](#submitting-changes)

## Developer Guide

So you want to contribute code to this project? Excellent! We're glad you're here. Here's what you need to do.

### Forking and Cloning

Fork this repository on GitHub, and clone locally with `git clone`.

### Install Prerequisites

#### JDK 14

OpenSearch components build using Java 14 at a minimum. This means you must have a JDK 14 installed with the environment variable `JAVA_HOME` referencing the path to Java home for your JDK 14 installation, e.g. `JAVA_HOME=/usr/lib/jvm/jdk-14`.

### Building

To build from the command line, use `./gradlew`.

```
./gradlew clean
./gradlew build
./gradlew publishToMavenLocal
```    

`./gradlew spotlessApply` formats code.
`./gradlew paBwcCluster#mixedClusterTask -Dtests.security.manager=false` launches a cluster with three nodes of bwc version of OpenSearch with performance-analyzer and tests backwards compatibility by upgrading one of the nodes with the current version of OpenSearch with performance-analyzer creating a mixed cluster.
`./gradlew paBwcCluster#rollingUpgradeClusterTask -Dtests.security.manager=false` launches a cluster with three nodes of bwc version of OpenSearch with performance-analyzer and tests backwards compatibility by performing rolling upgrade of each node with the current version of OpenSearch with performance-analyzer.
`./gradlew paBwcCluster#fullRestartClusterTask -Dtests.security.manager=false` launches a cluster with three nodes of bwc version of OpenSearch with performance-analyzer and tests backwards compatibility by performing a full restart on the cluster upgrading all the nodes with the current version of OpenSearch with performance-analyzer.
`./gradlew bwcTestSuite -Dtests.security.manager=false` runs all the above bwc tests combined.

### Using IntelliJ IDEA

Launch Intellij IDEA, choose **Import Project**, and select the `build.gradle` file in the root of this package. 

### Submitting Changes

See [CONTRIBUTING](CONTRIBUTING.md).

### Backports

The Github workflow in [`backport.yml`](.github/workflows/backport.yml) creates backport PRs automatically when the
original PR with an appropriate label `backport <backport-branch-name>` is merged to main with the backport workflow
run successfully on the PR. For example, if a PR on main needs to be backported to `1.x` branch, add a label
`backport 1.x` to the PR and make sure the backport workflow runs on the PR along with other checks. Once this PR is
merged to main, the workflow will create a backport PR to the `1.x` branch.