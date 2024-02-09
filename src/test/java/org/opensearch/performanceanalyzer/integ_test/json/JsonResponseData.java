/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.integ_test.json;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;

/**
 * spotless:off
 *
 * "data": {
 *   "fields": [
 *      {
 *        "name": "CPU_Utilization",
 *        "type": "DOUBLE"
 *      }
 *   ],
 *   "records": [
 *       [
 *         0.005275218803760752
 *       ]
 *    ]
 *  }
 *
 *  spotless:on
 */
public class JsonResponseData {
    private static final String FIELDS = "fields";
    private static final String RECORDS = "records";

    @SerializedName(FIELDS)
    private JsonResponseField[] fields;

    @SerializedName(RECORDS)
    private String[][] records;

    public JsonResponseData(JsonResponseField[] fields, String[][] records) {
        this.fields = fields;
        this.records = records;
    }

    public int getFieldDimensionSize() {
        return fields.length;
    }

    public int getRecordSize() {
        return records.length;
    }

    public JsonResponseField getField(int index) throws IndexOutOfBoundsException {
        return fields[index];
    }

    public String getRecord(int index, String fieldName) throws Exception {
        for (int i = 0; i < getFieldDimensionSize(); i++) {
            if (fieldName.equals(fields[i].getName())) {
                return records[index][i];
            }
        }
        throw new IllegalArgumentException();
    }

    public Double getRecordAsDouble(int index, String fieldName) throws Exception {
        String recordStr = getRecord(index, fieldName);
        JsonResponseField field = getField(index);
        if (!field.getType().equals(JsonResponseField.Type.Constants.DOUBLE)) {
            throw new IllegalArgumentException();
        }
        return Double.parseDouble(recordStr);
    }

    @Override
    public String toString() {
        return "data:{"
                + "fields="
                + Arrays.toString(fields)
                + ", records="
                + Arrays.toString(records)
                + '}';
    }
}
