## Version 2.7.0 Release Notes

Compatible with OpenSearch 2.7.0

## Enhancements
* Adding CIRCUIT_BREAKER_COLLECTOR_EXECUTION_TIME, CIRCUIT_BREAKER_COLLECTOR_ERROR, CLUSTER_MANAGER_METRICS_ERROR in StatExceptionCode ([420](https://github.com/opensearch-project/performance-analyzer/pull/420/))
* Adding Shard HotSpot feature in RCA ([295](https://github.com/opensearch-project/performance-analyzer-rca/pull/295))

### Bug fixes
* Fix AdmissionControl class loading issue in Netty/PA communication ([#414](https://github.com/opensearch-project/performance-analyzer/pull/414))
* Fix GC metric not collected in RCA ([287](https://github.com/opensearch-project/performance-analyzer-rca/pull/287))
* Fix ShardEvents and ShardBulkDocs null metrics in RCA ([283](https://github.com/opensearch-project/performance-analyzer-rca/pull/283))

### Infrastructure
* Getting Jackson,JUnit, Log4j dependency version from core ([#417](https://github.com/opensearch-project/performance-analyzer/pull/417))
* Upgrade checkstyle to 9.3 ([#395](https://github.com/opensearch-project/performance-analyzer/pull/395))
* Publish snapshots to maven via GHA ([#385](https://github.com/opensearch-project/performance-analyzer/issues/385))

### Maintenance
* Modify namespace from xcontent common to core ([#410](https://github.com/opensearch-project/performance-analyzer/pull/410))
