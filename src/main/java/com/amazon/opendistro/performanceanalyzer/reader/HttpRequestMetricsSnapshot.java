/*
 * Copyright <2019> Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.opendistro.performanceanalyzer.reader;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.SelectField;
import org.jooq.SelectHavingStep;
import org.jooq.impl.DSL;

import com.amazon.opendistro.performanceanalyzer.DBUtils;
import com.amazon.opendistro.performanceanalyzer.metrics.AllMetrics.CommonDimension;
import com.amazon.opendistro.performanceanalyzer.metrics.AllMetrics.HttpDimension;
import com.amazon.opendistro.performanceanalyzer.metrics.AllMetrics.HttpMetric;
import com.amazon.opendistro.performanceanalyzer.metricsdb.MetricsDB;

/**
 * Snapshot of start/end events generated by customer initiated http operations like bulk and search.
 */
@SuppressWarnings("serial")
public class HttpRequestMetricsSnapshot implements Removable {
    private static final Logger LOG = LogManager.getLogger(HttpRequestMetricsSnapshot.class);
    private static final Long EXPIRE_AFTER = 600000L;
    private final DSLContext create;
    private final Long windowStartTime;
    private final String tableName;
    private List<String> columns;

    public enum Fields {
        RID("rid"),
        OPERATION(CommonDimension.OPERATION.toString()),
        INDICES(HttpDimension.INDICES.toString()),
        HTTP_RESP_CODE(HttpDimension.HTTP_RESP_CODE.toString()),
        EXCEPTION(CommonDimension.EXCEPTION.toString()),
        HTTP_REQUEST_DOCS(HttpMetric.HTTP_REQUEST_DOCS.toString()),
        ST("st"),
        ET("et"),
        LAT("lat"),
        HTTP_TOTAL_REQUESTS(HttpMetric.HTTP_TOTAL_REQUESTS.toString());

        private final String fieldValue;

        Fields(String fieldValue) {
            this.fieldValue = fieldValue;
        }

        @Override
        public String toString() {
          return fieldValue;
        }
    }

    public HttpRequestMetricsSnapshot(Connection conn, Long windowStartTime) throws Exception {
        this.create = DSL.using(conn, SQLDialect.SQLITE);
        this.windowStartTime = windowStartTime;
        this.tableName = "http_rq_" + windowStartTime;

        this.columns = new ArrayList<String>() { {
            this.add(Fields.RID.toString());
            this.add(Fields.OPERATION.toString());
            this.add(Fields.INDICES.toString());
            this.add(Fields.HTTP_RESP_CODE.toString());
            this.add(Fields.EXCEPTION.toString());
            this.add(Fields.HTTP_REQUEST_DOCS.toString());
            this.add(Fields.ST.toString());
            this.add(Fields.ET.toString());
        } };

        List<Field<?>> fields = new ArrayList<Field<?>>() { {
            this.add(DSL.field(DSL.name(Fields.RID.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.OPERATION.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.INDICES.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.HTTP_RESP_CODE.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.EXCEPTION.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.HTTP_REQUEST_DOCS.toString()), Long.class));
            this.add(DSL.field(DSL.name(Fields.ST.toString()), Long.class));
            this.add(DSL.field(DSL.name(Fields.ET.toString()), Long.class));
        } };

        create.createTable(this.tableName)
            .columns(fields)
            .execute();
    }

    public void putStartMetric(Long startTime, Long itemCount, Map<String, String> dimensions) {
        Map<Field<?>, String> dimensionMap = new HashMap<>();
        for (Map.Entry<String, String> dimension: dimensions.entrySet()) {
                dimensionMap.put(DSL.field(DSL.name(dimension.getKey()), String.class),
                                    dimension.getValue());
        }
        create.insertInto(DSL.table(this.tableName))
            .set(DSL.field(DSL.name(Fields.ST.toString()), Long.class), startTime)
            .set(DSL.field(DSL.name(Fields.HTTP_REQUEST_DOCS.toString()), Long.class), itemCount)
            .set(dimensionMap)
            .execute();
    }

    public BatchBindStep startBatchPut() {
        List<Object> dummyValues = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            dummyValues.add(null);
        }
        return create.batch(create.insertInto(DSL.table(this.tableName)).values(dummyValues));
    }

    public void putEndMetric(Long endTime, Map<String, String> dimensions) {
        Map<Field<?>, String> dimensionMap = new HashMap<>();
        for (Map.Entry<String, String> dimension: dimensions.entrySet()) {
                dimensionMap.put(DSL.field(
                            DSL.name(dimension.getKey()), String.class),
                            dimension.getValue());
        }
        create.insertInto(DSL.table(this.tableName))
            .set(DSL.field(DSL.name(Fields.ET.toString()), Long.class), endTime)
            .set(dimensionMap)
            .execute();
    }

    public Result<Record> fetchAll() {
        return create.select().from(DSL.table(this.tableName)).fetch();
    }

    /**
     * This function returns a single row for each request.
     * We have a start and end event for each request and each event has different attributes.
     * This function aggregates all the data into a single row.
     *
     * Actual Table -
     *  |rid    |operation|indices |status|exception|itemCount|           st|           et|
     *  +-------+---------+--------+------+---------+---------+-------------+-------------+
     *  |1417935|search   |        |{null}|{null}   |        0|1535065254939|       {null}|
     *  |1418424|search   |{null}  |200   |         |   {null}|       {null}|1535065341025|
     *  |1418424|search   |sonested|{null}|{null}   |        0|1535065340730|       {null}|
     *  |1418435|search   |{null}  |200   |         |   {null}|       {null}|1535065343355|
     *
     * Returned Table
     *  |rid    |operation|indices |status|exception|itemCount|           st|           et|
     *  +-------+---------+--------+------+---------+---------+-------------+-------------+
     *  |1418424|search   |sonested|200   |         |        0|1535065340730|1535065341025|
     *  |1418435|search   |        |200   |         |        0|1535065254939|1535065343355|
     *
     *  @return a single row for each http request
     */
    public SelectHavingStep<Record> groupByRidSelect() {
        ArrayList<SelectField<?>> fields = new ArrayList<SelectField<?>>() { {
            this.add(DSL.field(DSL.name(Fields.RID.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.OPERATION.toString()), String.class));
        } };
        fields.add(DSL.max(DSL.field(Fields.ST.toString(), Long.class)).as(DSL.name(Fields.ST.toString())));
        fields.add(DSL.max(DSL.field(Fields.ET.toString(), Long.class)).as(DSL.name(Fields.ET.toString())));
        fields.add(DSL.max(DSL.field(Fields.INDICES.toString())).as(DSL.name(Fields.INDICES.toString())));
        fields.add(DSL.max(DSL.field(Fields.HTTP_RESP_CODE.toString())).as(DSL.name(Fields.HTTP_RESP_CODE.toString())));
        fields.add(DSL.max(DSL.field(Fields.EXCEPTION.toString())).as(DSL.name(Fields.EXCEPTION.toString())));
        fields.add(DSL.max(DSL.field(Fields.HTTP_REQUEST_DOCS.toString())).as(DSL.name(Fields.HTTP_REQUEST_DOCS.toString())));

        return create.select(fields).from(DSL.table(this.tableName))
            .groupBy(DSL.field(Fields.RID.toString()));
    }

    /**
     * This function returns row with latency for each request.
     * We have a start and end event for each request and each event has different attributes.
     * This function aggregates all the data into a single row.
     *
     * Actual Table -
     *  |rid    |operation|indices |status|exception|itemCount|           st|           et|
     *  +-------+---------+--------+------+---------+---------+-------------+-------------+
     *  |1417935|search   |        |{null}|{null}   |        0|1535065254939|       {null}|
     *  |1418424|search   |{null}  |200   |         |   {null}|       {null}|1535065341025|
     *  |1418424|search   |sonested|{null}|{null}   |        0|1535065340730|       {null}|
     *  |1418435|search   |{null}  |200   |         |   {null}|       {null}|1535065343355|
     *
     * Returned Table
     *  |rid    |operation|indices |status|exception|itemCount|           st|           et|  lat|
     *  +-------+---------+--------+------+---------+---------+-------------+-------------+-----+
     *  |1418424|search   |sonested|200   |         |        0|1535065340730|1535065341025|  295|
     *  |1418435|search   |        |200   |         |        0|1535065254939|1535065343355|88416|
     *
     *  @return rows with latency for each request
     */
    public SelectHavingStep<Record> fetchLatencyTable() {
        ArrayList<SelectField<?>> fields = new ArrayList<SelectField<?>>() { {
            this.add(DSL.field(DSL.name(Fields.RID.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.OPERATION.toString()), String.class));
            this.add(DSL.field(Fields.ST.toString(), Long.class));
            this.add(DSL.field(Fields.ET.toString(), Long.class));
            this.add(DSL.field(Fields.HTTP_RESP_CODE.toString()));
            this.add(DSL.field(Fields.INDICES.toString()));
            this.add(DSL.field(Fields.EXCEPTION.toString()));
            this.add(DSL.field(Fields.HTTP_REQUEST_DOCS.toString()));
        } };
        fields.add(DSL.field(Fields.ET.toString()).minus(DSL.field(Fields.ST.toString())).as(DSL.name(Fields.LAT.toString())));
        return create.select(fields).from(groupByRidSelect())
            .where(DSL.field(Fields.ET.toString()).isNotNull().and(
                        DSL.field(Fields.ST.toString()).isNotNull()));
    }

    /**
     * This function aggregates rows by operation.
     * This is a performance optimization to avoid writing one entry per request back into metricsDB.
     * This function returns one row per operation.
     *
     * Latency Table -
     *  |rid    |operation|indices |status|exception|itemCount|           st|           et|lat|
     *  +-------+---------+--------+------+---------+---------+-------------+-------------+-----+
     *  |1418424|search   |sonested|200   |         |        0|1535065340730|1535065341025|295|
     *  |1418435|search   |sonested|200   |         |        0|1535065254939|1535065343355|305|
     *
     *  Returned Table -
     *  |operation|indices |status|exception|sum_lat|avg_lat|min_lat|max_lat|
     *  +---------+--------+------+---------+---------+-------------+-------+
     *  |search   |sonested|200   |         |    600|    300|    295|    305|
     *
     *  @return latency rows by operation
     */
    public Result<Record> fetchLatencyByOp() {
        ArrayList<SelectField<?>> fields = new ArrayList<SelectField<?>>() { {
            this.add(DSL.field(DSL.name(Fields.OPERATION.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.HTTP_RESP_CODE.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.INDICES.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.EXCEPTION.toString()), String.class));
            this.add(DSL.sum(DSL.field(DSL.name(Fields.HTTP_REQUEST_DOCS.toString()), Long.class))
                    .as(DBUtils.getAggFieldName(Fields.HTTP_REQUEST_DOCS.toString(), MetricsDB.SUM)));
            this.add(DSL.avg(DSL.field(DSL.name(Fields.HTTP_REQUEST_DOCS.toString()), Long.class))
                    .as(DBUtils.getAggFieldName(Fields.HTTP_REQUEST_DOCS.toString(), MetricsDB.AVG)));
            this.add(DSL.min(DSL.field(DSL.name(Fields.HTTP_REQUEST_DOCS.toString()), Long.class))
                    .as(DBUtils.getAggFieldName(Fields.HTTP_REQUEST_DOCS.toString(), MetricsDB.MIN)));
            this.add(DSL.max(DSL.field(DSL.name(Fields.HTTP_REQUEST_DOCS.toString()), Long.class))
                    .as(DBUtils.getAggFieldName(Fields.HTTP_REQUEST_DOCS.toString(), MetricsDB.MAX)));
            this.add(DSL.sum(DSL.field(DSL.name(Fields.LAT.toString()), Double.class))
                    .as(DBUtils.getAggFieldName(Fields.LAT.toString(), MetricsDB.SUM)));
            this.add(DSL.avg(DSL.field(DSL.name(Fields.LAT.toString()), Double.class))
                    .as(DBUtils.getAggFieldName(Fields.LAT.toString(), MetricsDB.AVG)));
            this.add(DSL.min(DSL.field(DSL.name(Fields.LAT.toString()), Double.class))
                    .as(DBUtils.getAggFieldName(Fields.LAT.toString(), MetricsDB.MIN)));
            this.add(DSL.max(DSL.field(DSL.name(Fields.LAT.toString()), Double.class))
                    .as(DBUtils.getAggFieldName(Fields.LAT.toString(), MetricsDB.MAX)));
            this.add(DSL.count().as(Fields.HTTP_TOTAL_REQUESTS.toString()));
        } };
        ArrayList<Field<?>> groupByFields = new ArrayList<Field<?>>() { {
            this.add(DSL.field(DSL.name(Fields.OPERATION.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.HTTP_RESP_CODE.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.INDICES.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.EXCEPTION.toString()), String.class));
        } };

        return create.select(fields).from(fetchLatencyTable())
            .groupBy(groupByFields).fetch();
    }

    /**
     * This function returns requests with a missing end event.
     * A request maybe long running and the end event might not have occured in this snapshot.
     *
     * Actual Table -
     *  |rid    |operation|indices |status|exception|itemCount|           st|           et|
     *  +-------+---------+--------+------+---------+---------+-------------+-------------+
     *  |1417935|search   |        |{null}|{null}   |        0|1535065254939|       {null}|
     *  |1418424|search   |sonested|{null}|{null}   |        0|1535065340730|       {null}|
     *  |1418435|search   |{null}  |200   |         |   {null}|       {null}|1535065343355|
     *
     * Returned Table
     *  |rid    |operation|indices |status|exception|itemCount|           st|           et|
     *  +-------+---------+--------+------+---------+---------+-------------+-------------+
     *  |1418424|search   |sonested|200   |         |        0|1535065340730|             |
     *
     *  @return rows missing an end event
     */
    public SelectHavingStep<Record> fetchInflightRequests() {
        ArrayList<SelectField<?>> fields = new ArrayList<SelectField<?>>() { {
            this.add(DSL.field(DSL.name(Fields.RID.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.OPERATION.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.INDICES.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.HTTP_RESP_CODE.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.EXCEPTION.toString()), String.class));
            this.add(DSL.field(DSL.name(Fields.HTTP_REQUEST_DOCS.toString()), Long.class));
            this.add(DSL.field(Fields.ST.toString(), Long.class));
            this.add(DSL.field(Fields.ET.toString(), Long.class));
        } };

        return create.select(fields).from(groupByRidSelect())
            .where(DSL.field(Fields.ST.toString()).isNotNull()
                    .and(DSL.field(Fields.ET.toString()).isNull())
                    .and(DSL.field(Fields.ST.toString()).gt(this.windowStartTime - EXPIRE_AFTER)));
    }

    public String getTableName() {
        return this.tableName;
    }

    @Override
    public void remove() {
        LOG.info("Dropping table - {}", this.tableName);
        create.dropTable(DSL.table(this.tableName)).execute();
    }

    public void rolloverInflightRequests(HttpRequestMetricsSnapshot prevSnap) {
        //Fetch all entries that have not ended and write to current table.
        create.insertInto(DSL.table(this.tableName)).select(
                create.select().from(prevSnap.fetchInflightRequests())).execute();
    }
}

