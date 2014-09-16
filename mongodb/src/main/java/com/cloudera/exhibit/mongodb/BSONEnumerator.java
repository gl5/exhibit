/*
 * Copyright (c) 2014, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.exhibit.mongodb;

import net.hydromatic.linq4j.Enumerator;
import org.bson.BSONObject;

import java.util.List;

class BSONEnumerator implements Enumerator<Object> {

  private List<? extends BSONObject> records;
  private List<String> columns;
  private List<Object> defaultValues;
  private Object[] current;
  private int currentIndex = -1;

  public BSONEnumerator(List<? extends BSONObject> records, List<String> columns, List<Object> defaultValues) {
    this.records = records;
    this.columns = columns;
    this.defaultValues = defaultValues;
    this.current = new Object[columns.size()];
  }

  @Override
  public Object current() {
    return current;
  }

  @Override
  public boolean moveNext() {
    currentIndex++;
    boolean hasNext = currentIndex < records.size();
    if (hasNext) {
      updateValues();
    }
    return hasNext;
  }

  @Override
  public void reset() {
    currentIndex = -1;
  }

  @Override
  public void close() {
  }

  private void updateValues() {
    BSONObject bson = records.get(currentIndex);
    for (int i = 0; i < columns.size(); i++) {
      current[i] = bson.get(columns.get(i));
      if (current[i] == null) {
        Object defaultValue = defaultValues.get(i);
        if (!(defaultValue instanceof Class)) {
          current[i] = defaultValue;
        }
      }
    }
  }
}