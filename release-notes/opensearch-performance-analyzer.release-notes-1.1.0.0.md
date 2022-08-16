## Version 1.1.0.0 Release Notes

Compatible with OpenSearch 1.1.0

### Features

* AdmissionControl RequestSize AutoTuning ([#44](https://github.com/opensearch-project/performance-analyzer-rca/pull/44))

### Enhancements

* Use Collector override disable list for ShardIndexingPressureMetricCollector ([#28](https://github.com/opensearch-project/performance-analyzer/pull/28))
* Adding metric emission + UT for RCA_FRAMEWORK_CRASH ([#36](https://github.com/opensearch-project/performance-analyzer-rca/pull/36))
* Replace String split with Guava Splitter ([#42](https://github.com/opensearch-project/performance-analyzer-rca/pull/42))
* Add cluster_manager not up writer metric ([#51](https://github.com/opensearch-project/performance-analyzer-rca/pull/51))

### Bug fixes

* Handling empty flow unit during Cache/Queue RCA execution ([#34](https://github.com/opensearch-project/performance-analyzer-rca/pull/34))
* Fix for OOM error ([#35](https://github.com/opensearch-project/performance-analyzer-rca/pull/35))
* Fix thread name categorizations for Operation dimension in metrics API ([#44](https://github.com/opensearch-project/performance-analyzer-rca/pull/44)) 
* Add privileges for removing files ([#45](https://github.com/opensearch-project/performance-analyzer-rca/pull/45))
* Fix spotbugs failure by removing unused variable ([#47](https://github.com/opensearch-project/performance-analyzer-rca/pull/47))
* Change log level and remove retry ([#50](https://github.com/opensearch-project/performance-analyzer-rca/pull/50))
* Add retries for flaky tests and fix failing tests ([#52](https://github.com/opensearch-project/performance-analyzer-rca/pull/52))
* Fix deleting files within 60sec interval ([#62](https://github.com/opensearch-project/performance-analyzer/pull/62))

### Maintenance

* Fix snapshot build, upgrade to OpenSearch 1.1 ([#55](https://github.com/opensearch-project/performance-analyzer-rca/pull/55))
* Add workflow for gauntlet tests and fix spotbug errors ([#63](https://github.com/opensearch-project/performance-analyzer-rca/pull/63))
* Update version and add release notes for 1.1.0.0 release ([#68](https://github.com/opensearch-project/performance-analyzer/pull/68))

### Documentation

* Add themed logo to README ([#40](https://github.com/opensearch-project/performance-analyzer/pull/40))
* Fixes typo in APIs to enable PA batch metrics API in readme ([#42](https://github.com/opensearch-project/performance-analyzer/pull/42))

### Refactoring

* Addressing changes for StatsCollector ([#37](https://github.com/opensearch-project/performance-analyzer-rca/pull/37))
* Refactor stats collector ([#46](https://github.com/opensearch-project/performance-analyzer/pull/46))
