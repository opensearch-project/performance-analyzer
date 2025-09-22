/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import java.lang.reflect.Array;

public class TestUtils {
    public static Object createDummyValue(Class<?> type) {
        if (type.equals(String.class)) {
            return "dummyString";
        } else if (type.equals(int.class) || type.equals(Integer.class)) {
            return 0;
        } else if (type.equals(long.class) || type.equals(Long.class)) {
            return 0L;
        } else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            return false;
        } else if (type.equals(double.class) || type.equals(Double.class)) {
            return 0.0;
        } else if (type.equals(float.class) || type.equals(Float.class)) {
            return 0.0f;
        } else if (type.equals(byte.class) || type.equals(Byte.class)) {
            return (byte) 0;
        } else if (type.equals(char.class) || type.equals(Character.class)) {
            return 'a';
        } else if (type.equals(short.class) || type.equals(Short.class)) {
            return (short) 0;
        } else if (type.isArray()) {
            return Array.newInstance(type.getComponentType(), 0);
        } else {
            // For complex objects, you might need to add more specific handling
            return null;
        }
    }
}
