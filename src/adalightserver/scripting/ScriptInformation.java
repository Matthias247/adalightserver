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
 */

package adalightserver.scripting;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ScriptInformation {
    
    public final String name;
    public final Class<? extends LedScript> script;
    public final Map<String, ScriptParameter> parameters;
    
    public ScriptInformation(String name, Class<? extends LedScript> script,
            Map<String, ScriptParameter> parameters) {
        this.name = name;
        this.script = script;
        this.parameters = parameters;
    }
    
    public ScriptInformation(ScriptInformation rhs) {
        this.name = rhs.name;
        this.script = rhs.script;
        this.parameters = new HashMap<>();
        rhs.parameters.forEach((pname, param) -> {
            parameters.put(pname, new ScriptParameter(param));
        });
    }
    
    public String toJson() {
        StringBuilder s = new StringBuilder();
        
        s.append("{\"name\": \"");
        s.append(name);
        s.append("\", \"parameters\": [");
        if (parameters != null) {
            s.append(parameters.values().stream()
                    .filter(param -> param.isSupported())
                    .map(ScriptParameter::toJson)
                    .collect(Collectors.joining(", ")));
        }
        s.append("]}");
        
        return s.toString();
    }

}
