## Version 2.19.4 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 2.19.4

### Maintenance
* Bump bcpkix-jdk18on and commons-lang3 ([#877](https://github.com/opensearch-project/performance-analyzer/pull/877))
* Bump checkstyle and spotbug versions ([#861](https://github.com/opensearch-project/performance-analyzer/pull/861))
* Force io.netty:netty-codec-http2 version ([#879](https://github.com/opensearch-project/performance-analyzer/pull/879))
* Hardcode BWC to 2.19 to mitigate build failure ([#874](https://github.com/opensearch-project/performance-analyzer/pull/874))
* [AUTO] Increment version to 2.19.4-SNAPSHOT ([#842](https://github.com/opensearch-project/performance-analyzer/pull/842))
* Update the maven snapshot publish endpoint and credential ([#839](https://github.com/opensearch-project/performance-analyzer/pull/839))
* [2.19] run ./gradlew updateSHAs to fix dependency license check ([#859](https://github.com/opensearch-project/performance-analyzer/pull/859))

### Enhancements
* [Backport 2.19] Onboarding new maven snapshots publishing to s3 (PA) ([#854](https://github.com/opensearch-project/performance-analyzer/pull/854))