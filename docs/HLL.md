In `src/main/java/org/opensearch/performanceanalyzer/`:

- `PerformanceAnalyzerPlugin`'s constructor does the following:
    1. Creates a `scheduledMetricCollectorsExecutor`.
    2. `scheduledMetricCollectorsExecutor.addScheduledMetricCollector(new XYZMetricsCollector())` is called for all collectors.
    3. `scheduledMetricCollectorsExecutor.start()` is called and then `EventLogQueueProcessor.scheduleExecutor()`.

- Methods in `PerformanceAnalyzerPlugin` interface with the OpenSearch plugin architecture 
    - `onIndexModule`, `onDiscovery`, etc. are all called by OpenSearch when their corresponding events occur and the plugin can act on them.
    - For example:
        - `getActionFilters` provides OpenSearch with a list of classes that implement `ActionFilter`.
        - `action/PerformanceAnalyzerActionFilter` is the only class currently returned to OpenSearch as an `ActionFilter`.
        - when a BulkRequest or SearchRequest is recieved by OpenSearch, `action/PerformanceAnalyzerActionFilter` logs a start event and creates a listener (`action/PerformanceAnalyzerActionListener`) which waits to record the corresponding end event.
    - `PerformanceAnalyzerPlugin.getRestHandlers` returns all the classes that can handle REST requests to OpenSearch.
-  The classes in `http_action/config` define all the public API routes for Performance Analyzer.
    - `http_action/config/RestConfig` defines `PA_BASE_URI = "/_plugins/_performanceanalyzer"`
    - `PerformanceAnalyzerResourceProvider` defines the `metrics`, `rca`, `batch` and `actions` routes.
    -  `PerformanceAnalyzerResourceProvider.SUPPORTED_REDIRECTIONS = ("rca", "metrics", "batch", "actions")`
- `listener/PerformanceAnalyzerSearchListener` hooks into OpenSearch core to emit search operation related metrics.
        
    
- `writer/EventLogQueueProcessor`:
    -  contains `purgeQueueAndPersist` which drains `PerformanceAnalyzerMetrics.metricQueue` into a file that contains all events for a certain time bucket. It also removes old events. Uses `event_process.EventLogFileHandler` in Performance Analyzer Commons for file writing logic.
    - `scheduleExecutor` periodically runs `purgeQueueAndPersist`.

- Classes in `collectors` extend `PerformanceAnalyzerMetricsCollector` and implement `MetricsProcessor`.
	- `PerformanceAnalyzerMetricsCollector` implements `Runnable` and contains common variables like `value` where a collector stores serialized metrics.
	- A collector is triggered through  `PerformanceAnalyzerMetricsCollector.collectMetrics`.
		1. The collector will store serialized data in the `value` variable and then call `MetricsProcessor.saveMetricValues`.
		2. `saveMetricValues` calls `PerformanceAnalyzerMetrics.emitMetric` that creates an `Event` from the serialized data and adds it to a static queue (`PerformanceAnalyzerMetrics.metricQueue`) shared across all collectors. 