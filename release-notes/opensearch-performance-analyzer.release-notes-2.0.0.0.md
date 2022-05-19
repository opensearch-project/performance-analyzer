## Version 2.0.0.0 Release Notes

Compatible with OpenSearch 2.0.0

### Features

* Adds setting to enable/disable Thread Contention Monitoring ([#171](https://github.com/opensearch-project/performance-analyzer/pull/171))
* Fixes calculation of average thread blocked time and average thread waited time ([#118](https://github.com/opensearch-project/performance-analyzer-rca/pull/118))

### Bug fixes

* Fix EventLogFileHandlerTests flaky test ([#178](https://github.com/opensearch-project/performance-analyzer/pull/178))
* Add retry for tests ([#180](https://github.com/opensearch-project/performance-analyzer/pull/180))

### Maintenance

* Gradle 7, JDK related changes and OS 2.0 ([#179](https://github.com/opensearch-project/performance-analyzer/pull/179))
* Add additional logs for Integration Tests ([#182](https://github.com/opensearch-project/performance-analyzer/pull/182))
* Enable dependency license check and removing unused license ([#183](https://github.com/opensearch-project/performance-analyzer/pull/183))
* Moving build script file here from opensearch build package ([#184](https://github.com/opensearch-project/performance-analyzer/pull/184))
* Update directory names and remove jar for integTest ([#187](https://github.com/opensearch-project/performance-analyzer/pull/187))
* Update PA directories from plugins to root ([#189](https://github.com/opensearch-project/performance-analyzer/pull/189))
* Changes to add jdk17, remove jdk 8,14, OS 2.0 and upgrade to gradle 7 ([#156](https://github.com/opensearch-project/performance-analyzer-rca/pull/156))
* Update directory names ([#166](https://github.com/opensearch-project/performance-analyzer-rca/pull/166))
* Update PA directories from plugins to root ([#168](https://github.com/opensearch-project/performance-analyzer-rca/pull/168))

### Documentation
* Updated issue templates from .github. ([#177](https://github.com/opensearch-project/performance-analyzer/pull/177))
* Removing metrics which are not required now as were removed in OS 2.0 ([#159](https://github.com/opensearch-project/performance-analyzer-rca/pull/159))
