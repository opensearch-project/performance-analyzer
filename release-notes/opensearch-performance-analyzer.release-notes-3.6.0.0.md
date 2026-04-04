## Version 3.6.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.6.0

### Features

* Add shard operations collector and optimized node stats collector ([#824](https://github.com/opensearch-project/performance-analyzer/pull/824))

### Bug Fixes

* Fix CVE-2025-68161 by force resolving log4j dependencies ([#932](https://github.com/opensearch-project/performance-analyzer/pull/932))

### Infrastructure

* Disable dependencyLicenses check to align with other plugin repos ([#926](https://github.com/opensearch-project/performance-analyzer/pull/926))
* Make performance-analyzer plugin aware of FIPS build parameter for proper BouncyCastle dependency handling ([#915](https://github.com/opensearch-project/performance-analyzer/pull/915))
