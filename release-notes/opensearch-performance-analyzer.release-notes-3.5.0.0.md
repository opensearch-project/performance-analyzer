## Version 3.5.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.5.0

### Maintenance
* Consuming performance-analyzer-commons 2.1.1 on JDK21 with all versions bumped for OpenSearch 3.5 release. Takes in the following changes for 3.5 release.
    - https://github.com/opensearch-project/performance-analyzer-commons/pull/116 
    - https://github.com/opensearch-project/performance-analyzer-commons/pull/117

* Jackson core and annotations have different minor versions in OpenSearch-3.5.0 snapshot. Since we're using the same variable for both, build fails with invalid version. Using the version as per 3.5 snapshot.