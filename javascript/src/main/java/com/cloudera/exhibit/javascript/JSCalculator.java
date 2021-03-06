/*
 * Copyright (c) 2015, Cloudera, Inc. All Rights Reserved.
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
package com.cloudera.exhibit.javascript;

import com.cloudera.exhibit.core.Calculator;
import com.cloudera.exhibit.core.Exhibit;
import com.cloudera.exhibit.core.ExhibitDescriptor;
import com.cloudera.exhibit.core.Exhibits;
import com.cloudera.exhibit.core.FieldType;
import com.cloudera.exhibit.core.Frame;
import com.cloudera.exhibit.core.Obs;
import com.cloudera.exhibit.core.ObsDescriptor;
import com.cloudera.exhibit.core.Vec;
import com.cloudera.exhibit.core.simple.SimpleFrame;
import com.cloudera.exhibit.core.simple.SimpleObs;
import com.cloudera.exhibit.core.simple.SimpleObsDescriptor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class JSCalculator implements Serializable, Calculator {

  private String src;
  private boolean hasReturn;

  private transient ObsDescriptor descriptor;
  private transient Context ctx = null;
  private transient Scriptable scope;
  private transient Script script;
  private transient Function func;

  public JSCalculator(String src) {
    this(null, src);
  }

  public JSCalculator(ObsDescriptor descriptor, String src) {
    this.src = src;
    this.hasReturn = src.contains("return");
    this.descriptor = descriptor;
  }

  @Override
  public ObsDescriptor initialize(ExhibitDescriptor ed) {
    if( !ContextFactory.hasExplicitGlobal() ){
      ContextFactory.initGlobal(new ExhibitContextFactory());
    }
    if (ctx == null) {
      ctx = Context.enter();
      this.scope = ctx.initStandardObjects(null, true);
      if (hasReturn) {
        this.func = ctx.compileFunction(scope, "function() {" + src + "}", "<cmd>", 1, null);
      } else {
        this.script = ctx.compileString(src, "<cmd>", 1, null);
      }
    }

    if (this.descriptor == null) {
      this.descriptor = toObsDescriptor(eval(Exhibits.defaultValues(ed), true));
    }
    return descriptor;
  }

  @Override
  public Iterable<Obs> apply(Exhibit exhibit) {
    Object res = eval(exhibit, false);
    List<Obs> ret = Lists.newArrayList();
    if (res instanceof ScriptableFrame) {
      return ((ScriptableFrame) res).frame();
    } else if (res instanceof List) {
      for (Object obj : (List) res) {
        ret.add(toObs(obj, exhibit));
      }
    } else {
      ret.add(toObs(res, exhibit));
    }
    return new SimpleFrame(descriptor, ret);
  }

  Obs toObs(Object obj, Exhibit exhibit) {
    if (obj instanceof ScriptableObs) {
      return ((ScriptableObs) obj).obs();
    }
    List<Object> values = Lists.newArrayListWithExpectedSize(descriptor.size());
    if (obj instanceof Map) {
      Map mres = (Map) obj;
      for (ObsDescriptor.Field f : descriptor) {
        Object v = mres.get(f.name);
        values.add(v == null ? null : f.type.cast(v));
      }
    } else if (descriptor.size() == 1) {
      if (obj == null) {
        values.add(null);
      } else {
        values.add(descriptor.get(0).type.cast(obj));
      }
    } else {
      throw new IllegalStateException("Invalid javascript result: " + obj + " for exhibit: " + exhibit);
    }
    return new SimpleObs(descriptor, values);
  }

  Object eval(Exhibit exhibit, boolean init) {
    Scriptable exhibitScope = ctx.newObject(scope);
    exhibitScope.setPrototype(scope);
    exhibitScope.setParentScope(null);
    exhibitScope.put("INIT", exhibitScope, init);

    Obs attr = exhibit.attributes();
    for (int i = 0; i < attr.descriptor().size(); i++) {
      exhibitScope.put(attr.descriptor().get(i).name, exhibitScope, attr.get(i));
    }
    for (Map.Entry<String, Frame> e : exhibit.frames().entrySet()) {
      exhibitScope.put(e.getKey(), exhibitScope, new ScriptableFrame(e.getValue()));
    }
    for (Map.Entry<String, Vec> e : exhibit.vectors().entrySet()) {
      exhibitScope.put(e.getKey(), exhibitScope, new ScriptableVec(e.getValue()));
    }

    if (hasReturn) {
      return func.call(ctx, exhibitScope, null, new Object[0]);
    } else {
      return script.exec(ctx, exhibitScope);
    }
  }

  ObsDescriptor toObsDescriptor(Object res) {
    if (res == null) {
      throw new IllegalStateException("Null return values are not permitted");
    } else if (res instanceof ScriptableObs) {
      return ((ScriptableObs) res).obs().descriptor();
    } else if (res instanceof ScriptableFrame) {
      return ((ScriptableFrame) res).frame().descriptor();
    } else if (res instanceof ScriptableVec) {
      ObsDescriptor.Field f = new ObsDescriptor.Field("c1", ((ScriptableVec) res).vec().getType());
      return new SimpleObsDescriptor(ImmutableList.of(f));
    } else if (res instanceof List) {
      return toObsDescriptor(((List) res).get(0));
    } else if (res instanceof Map) {
      Map<String, Object> mres = (Map<String, Object>) res;
      List<ObsDescriptor.Field> fields = Lists.newArrayList();
      for (String key : Sets.newTreeSet(mres.keySet())) {
        Object val = mres.get(key);
        FieldType ft = null;
        if (val == null) {
          throw new IllegalStateException("Null value for key: " + key);
        } else if(val instanceof Integer) {
          ft = FieldType.INTEGER;
        } else if(val instanceof Long) {
          ft = FieldType.LONG;
        } else if(val instanceof Float) {
          ft = FieldType.FLOAT;
        } else if(val instanceof BigDecimal) {
          ft = FieldType.DECIMAL;
        } else if (val instanceof Number) {
          ft = FieldType.DOUBLE;
        } else if (val instanceof String) {
          ft = FieldType.STRING;
        } else if (val instanceof Boolean) {
          ft = FieldType.BOOLEAN;
        }
        fields.add(new ObsDescriptor.Field(key, ft));
      }
      return new SimpleObsDescriptor(fields);
    } else if (res instanceof Integer) {
      return SimpleObsDescriptor.of("res", FieldType.INTEGER);
    } else if (res instanceof Long) {
      return SimpleObsDescriptor.of("res", FieldType.LONG);
    } else if (res instanceof Float) {
      return SimpleObsDescriptor.of("res", FieldType.FLOAT);
    } else if (res instanceof BigDecimal) {
      return SimpleObsDescriptor.of("res", FieldType.DECIMAL);
    } else if (res instanceof Number) {
      return SimpleObsDescriptor.of("res", FieldType.DOUBLE);
    } else if (res instanceof String) {
      return SimpleObsDescriptor.of("res", FieldType.STRING);
    } else if (res instanceof Boolean) {
      return SimpleObsDescriptor.of("res", FieldType.BOOLEAN);
    } else {
      throw new IllegalStateException("Unsupported result type: " + res);
    }
  }

  @Override
  public void cleanup() {
    if(ctx != null) {
      Context.exit();
      ctx = null;
    }
  }
}
