## Version 2.8.0 Release Notes

Compatible with OpenSearch 2.8.0

## Enhancements
* Add Latency and Error service metrics in PA plugin [#442](https://github.com/opensearch-project/performance-analyzer/pull/442)
* Add Latency Metrics for respective Emitters in Reader Event Processing [#364](https://github.com/opensearch-project/performance-analyzer-rca/pull/364)
* Adding Collector Metrics and refactoring the Error metrics [#365](https://github.com/opensearch-project/performance-analyzer-rca/pull/365)

### Bug fixes
* Fix ShardStateCollector which was impacted by [core refactoring](https://github.com/opensearch-project/OpenSearch/pull/7301) [445](https://github.com/opensearch-project/performance-analyzer/pull/445)


### Infrastructure
* Upgrade gradle to 7.6.1, upgrade gradle test-retry plugin to 1.5.2. ([#438](https://github.com/opensearch-project/performance-analyzer/pull/438))
* Introduce protobuf and guava dependency from core versions file [#437](https://github.com/opensearch-project/performance-analyzer/pull/437)
* Update dependency org.xerial:sqlite-jdbc to v3.41.2.2 [#375](https://github.com/opensearch-project/performance-analyzer-rca/pull/375)


### Maintenance
* Update RestController constructor for tests [#440](https://github.com/opensearch-project/performance-analyzer/pull/440)
* Dependencies change in favor of Commons repo [#448](https://github.com/opensearch-project/performance-analyzer/pull/448)
* WriterMetrics and config files dependency redirection [#450](https://github.com/opensearch-project/performance-analyzer/pull/450)
* Refactor code related to Commons change, fixing unit tests [#451](https://github.com/opensearch-project/performance-analyzer/pull/451)
* Remove remaining dependencies from PA-RCA due to commons repo [#453](https://github.com/opensearch-project/performance-analyzer/pull/453)
* Fix BWC Integration tests [#413](https://github.com/opensearch-project/performance-analyzer/pull/413)
* Fix SHA update for PA-Commons repo in build.gradle  [#454](https://github.com/opensearch-project/performance-analyzer/pull/454)
* Refactor Service/Stat Metrics [#376](https://github.com/opensearch-project/performance-analyzer-rca/pull/376)