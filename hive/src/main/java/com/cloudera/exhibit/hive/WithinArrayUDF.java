/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.exhibit.hive;

import com.cloudera.exhibit.core.Calculator;
import com.cloudera.exhibit.core.Exhibit;
import com.cloudera.exhibit.core.Frame;
import com.cloudera.exhibit.core.Obs;
import com.cloudera.exhibit.core.ObsDescriptor;
import com.cloudera.exhibit.core.simple.SimpleExhibit;
import com.cloudera.exhibit.core.vector.Vector;
import com.cloudera.exhibit.sql.SQLCalculator;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentLengthException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;

import java.util.List;
import java.util.Map;

public class WithinArrayUDF extends GenericUDF {
  private Calculator calculator;
  private transient Exhibit exhibit;

  public WithinArrayUDF() {
  }

  @Override
  public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
    if (args.length <= 1) {
      throw new UDFArgumentLengthException("The 'within_array' function takes at least two arguments");
    }

    ObjectInspector first = args[0];
    String[] queries = HiveUtils.getQueries(first);

    Map<String, Frame> frames = Maps.newHashMap();
    for (int i = 1; i < args.length; i++) {
      HiveFrame frame = HiveUtils.getHiveFrame(args[i]);
      frames.put("T" + i, frame);
    }
    Map<String, Vector> vectors = Maps.newHashMap(); // TODO: implement
    this.exhibit = new SimpleExhibit(Obs.EMPTY, frames, vectors);
    this.calculator = new SQLCalculator(queries);
    ObsDescriptor od = calculator.initialize(exhibit.descriptor());
    return ObjectInspectorFactory.getStandardListObjectInspector(HiveUtils.fromDescriptor(od, false));
  }

  @Override
  public Object evaluate(GenericUDF.DeferredObject[] args) throws HiveException {
    for (int i = 1; i < args.length; i++) {
      ((HiveFrame) exhibit.frames().get("T" + i)).updateValues(args[i].get());
    }
    List<Object> results = Lists.newArrayList();
    for (Obs obs : calculator.apply(exhibit)) {
      results.add(getResult(obs));
    }
    return results;
  }

  private Object getResult(Obs obs) {
    if (obs.descriptor().size() == 1) {
      return HiveUtils.asHiveType(obs.get(0));
    } else {
      List<Object> values = Lists.newArrayListWithExpectedSize(obs.descriptor().size());
      for (int i = 0; i < obs.descriptor().size(); i++) {
        values.add(HiveUtils.asHiveType(obs.get(i)));
      }
      return values;
    }
  }

  @Override
  public String getDisplayString(String[] args) {
    assert (args.length > 1);
    return "within_array(" + Joiner.on(',').join(args) + ")";
  }
}
