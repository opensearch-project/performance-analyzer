## Version 1.3.0.0 Release Notes

Compatible with OpenSearch 1.3.0

### Features

* Add .whitesource configuration file ([#119](https://github.com/opensearch-project/performance-analyzer/pull/119))
* Add support for OPENSEARCH_JAVA_HOME ([#133](https://github.com/opensearch-project/performance-analyzer/pull/133))
* Adding auto backport ([#146](https://github.com/opensearch-project/performance-analyzer/pull/146))

### Bug fixes

* Fix and lock link checker at lycheeverse/lychee-action@v1.2.0. ([#113](https://github.com/opensearch-project/performance-analyzer/pull/113))
* Upgrade plugin to 1.3.0 and log4j to 2.17.1 ([#118](https://github.com/opensearch-project/performance-analyzer/pull/118))
* Don't run opensearch-cli in a child process ([#126](https://github.com/opensearch-project/performance-analyzer/pull/126))
* Modify grpc-netty-shaded to grpc-netty ([#129](https://github.com/opensearch-project/performance-analyzer-rca/pull/129))
* Fixes grpc channel leak issue and vertex buffer issue on non active master ([#130](https://github.com/opensearch-project/performance-analyzer-rca/pull/130))
* Fixes RCA crash on active master ([#132](https://github.com/opensearch-project/performance-analyzer-rca/pull/132))

### Maintenance

* Upgrade docker to 1.3 ([#114](https://github.com/opensearch-project/performance-analyzer-rca/pull/114))
* Upgrade plugin to 1.3.0 and log4j to 2.17.1 ([#118](https://github.com/opensearch-project/performance-analyzer/pull/118))
* Removing deprecated InitialBootClassLoaderMetaspaceSize JVM command line flag ([#124](https://github.com/opensearch-project/performance-analyzer/pull/124))
* Upgrade guava, protobuf version ([#127](https://github.com/opensearch-project/performance-analyzer/pull/127))
* Update jacksonVersion to 2.12.6 ([#129](https://github.com/opensearch-project/performance-analyzer/pull/129))
* Upgrade netty and bouncycastle versions ([#130](https://github.com/opensearch-project/performance-analyzer/pull/130))
* Remove jcenter ([#136](https://github.com/opensearch-project/performance-analyzer/pull/136))
* Update grpc licenses ([#139](https://github.com/opensearch-project/performance-analyzer/pull/139))

### Documentation

* Modify license headers ([#153](https://github.com/opensearch-project/performance-analyzer-rca/pull/153))