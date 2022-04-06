/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.integ_test.json;


import com.google.gson.annotations.SerializedName;

/** "fields": [ { "name": "CPU_Utilization", "type": "DOUBLE" } ] */
public class JsonResponseField {
    private static final String NAME = "name";
    private static final String TYPE = "type";

    @SerializedName(NAME)
    private String name;

    @SerializedName(TYPE)
    private String type;

    public JsonResponseField(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    @Override
    public String toString() {
        return "JsonResponseField{" + "name='" + name + '\'' + ", type='" + type + '\'' + '}';
    }

    // SQLite data type
    public enum Type {
        VARCHAR(Constants.VARCHAR),
        DOUBLE(Constants.DOUBLE);

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static class Constants {
            public static final String VARCHAR = "VARCHAR";
            public static final String DOUBLE = "DOUBLE";
        }
    }
}
