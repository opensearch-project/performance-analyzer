## Version 2.9.0 Release Notes

Compatible with OpenSearch 2.9.0

### Enhancements
* Remove heap allocation rate as the input metric to HotShardClusterRca [#411](https://github.com/opensearch-project/performance-analyzer-rca/pull/411)
* Set ThreadMetricsRca evaluation period from 12 seconds to 5 seconds [#410](https://github.com/opensearch-project/performance-analyzer-rca/pull/410)
* Add unit tests for the REST layer in RCA Agent [#436](https://github.com/opensearch-project/performance-analyzer-rca/pull/436)

### Bug fixes
* Fix NPE issue in ShardStateCollector, which was impacted by changes from upstream core [#489](https://github.com/opensearch-project/performance-analyzer/pull/489)
* Fix Mockito initialization issue [#443](https://github.com/opensearch-project/performance-analyzer-rca/pull/443)


### Infrastructure
* Update the BWC version to 2.8.0 [#446](https://github.com/opensearch-project/performance-analyzer/pull/446)
* Upgrade bcprov to bcprov-jdk15to18 in performance-analyzer [#493](https://github.com/opensearch-project/performance-analyzer/pull/493)
* Upgrade bcprov to bcprov-jdk15to18 in performance-analyzer-rca [439](https://github.com/opensearch-project/performance-analyzer-rca/pull/439)
* Upgrade bcpkix to bcpkix-jdk15to18 in performance-analyzer-rca [446](https://github.com/opensearch-project/performance-analyzer-rca/pull/446)
* Upgrade checkstyle version from 9.3 to 10.3.3 [#495](https://github.com/opensearch-project/performance-analyzer/pull/495)


### Maintenance
* Update build.gradle and github workflow to support 2.9 version [#499](https://github.com/opensearch-project/performance-analyzer/pull/499)
* Update licenses files for 2.9 [#501](https://github.com/opensearch-project/performance-analyzer/pull/501)
* Swap jboss annotation dependency for jakarta annotations [#407](https://github.com/opensearch-project/performance-analyzer-rca/pull/407)
* Ensures compatibility check readiness [#438](https://github.com/opensearch-project/performance-analyzer-rca/pull/438)
