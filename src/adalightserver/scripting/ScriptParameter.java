/*
 * Copyright 2014 Matthias Einwag
 *
 * The author licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *//*
 * Copyright 2014 Matthias Einwag
 *
 * The author licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package adalightserver.scripting;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import adalightserver.types.ColorRgb;

public class ScriptParameter {
    
    public final String name;
    public final Class<?> type;
    public final Object defaultValue;
    public Object currentValue;
    
    public static final Set<Class<?>> SupportedParameters
        = new HashSet<>();
    static {
        SupportedParameters.addAll(Arrays.asList(
            int.class, double.class, ColorRgb.class
        ));
    }
    
    public ScriptParameter(String name, Class<?> type, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }
    
    public ScriptParameter(ScriptParameter rhs) {
        this.name = rhs.name;
        this.type = rhs.type;
        this.defaultValue = rhs.defaultValue;
        this.currentValue = rhs.currentValue;
    }
    
    public boolean isSupported() {
        if (type != null && SupportedParameters.contains(type)) return true;
        return false;
    }
    
    public String toJson() {
        if (!SupportedParameters.contains(type)) return null;
        StringBuilder s = new StringBuilder();
        s.append("{")
         .append("\"name\": \"")
         .append(name)
         .append("\", \"type\": \"");
        
        if (type.equals(int.class)) {
            s.append("int\"");
            if (defaultValue instanceof Integer) {
                s.append(", \"default\": ")
                 .append((int)defaultValue);
            }
            if (currentValue instanceof Integer) {
                s.append(", \"current\": ")
                 .append((int)currentValue);
            }
        }
        else if (type.equals(double.class)) {
            s.append("double\"");
            if (defaultValue instanceof Double) {
                s.append(", \"default\": ")
                 .append((double)defaultValue);
            }
            else if (defaultValue instanceof BigDecimal) {
                s.append(", \"default\": ")
                 .append((BigDecimal)defaultValue);
            }
            else if (defaultValue instanceof Integer) {
                s.append(", \"default\": ")
                 .append((double)(int)defaultValue);
            }
            
            if (currentValue instanceof Double) {
                s.append(", \"current\": ")
                 .append((double)currentValue);
            }
            else if (currentValue instanceof BigDecimal) {
                s.append(", \"current\": ")
                 .append((BigDecimal)currentValue);
            }
            else if (currentValue instanceof Integer) {
                s.append(", \"current\": ")
                 .append((double)(int)currentValue);
            }
        }
        else if (type.equals(ColorRgb.class)) {
            s.append("ColorRgb\"");
            if (defaultValue instanceof ColorRgb) {
                s.append(", \"default\": \"")
                 .append(((ColorRgb)defaultValue).toHexString())
                 .append("\"");
            }
            if (currentValue instanceof ColorRgb) {
                s.append(", \"current\": \"")
                 .append(((ColorRgb)currentValue).toHexString())
                 .append("\"");
            }
        }
        
        s.append("}");
        return s.toString();
    }
    
    public void parseParameter(String valueString) {
        if (!SupportedParameters.contains(type) || valueString == null) {
            currentValue = defaultValue;
            return;
        }
        
        if (type == int.class) {
            try {
                currentValue = Integer.parseInt(valueString);
                return;
            } catch (Exception e) {}
        }
        else if (type == double.class) {
            try {
                currentValue = Double.parseDouble(valueString);
                return;
            } catch (Exception e) {}
        }
        else if (type == ColorRgb.class) {
            try {
                currentValue = ColorRgb.parseColor(valueString);
                return;
            } catch (Exception e) {}
        }
        else if (type == String.class) {
            currentValue = valueString;
            return;
        }
        
        currentValue = defaultValue;
    }
}
