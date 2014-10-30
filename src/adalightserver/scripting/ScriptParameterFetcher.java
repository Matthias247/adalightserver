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

import rx.Scheduler.Worker;
import adalightserver.types.LedApi;

public class ScriptParameterFetcher {

    private static class ParameterEvalContext implements LedScriptContext {
    
        @Override
        public LedApi getLedApi() {
            throw new RuntimeException();
        }
    
        @Override
        public Worker getScheduler() {
            throw new RuntimeException();
        }
        
        Map<String, ScriptParameter> scriptParameters = new HashMap<String, ScriptParameter>();
        
        public Map<String, ScriptParameter> getScriptParameters() {
            return scriptParameters;
        }
    
        @Override
        public void setupBindingFromParameters(Map<String, Map<String, Object>> paramMap) {
            scriptParameters.clear();
            try {
                paramMap.forEach((paramName,  v) -> {
                    if (!(paramName instanceof String) || !(v instanceof Map<?, ?>) || (paramName.isEmpty())) return;
                     
                    Object typeObject = v.get("type");
                    if (!(typeObject instanceof Class<?>)) return;
                    Class<?> type = (Class<?>)typeObject;
                    Object defaultObject = v.get("default");
                    if (defaultObject == null) return;
                    
                    ScriptParameter param = new ScriptParameter(paramName, type, defaultObject);
                    scriptParameters.put(paramName, param);
                });
            }
            catch (Exception e) {}
            throw new RuntimeException();
        }

        @Override
        public void stop() {
            throw new RuntimeException();
        }
    }
    
    public static Map<String, ScriptParameter> getParametersForScript(Class<? extends LedScript> scriptClass) {
        if (scriptClass == null)
            throw new NullPointerException();
        
        try {
            LedScript script = scriptClass.newInstance();
            ParameterEvalContext context = new ParameterEvalContext();
            LedScript.Initializer.init(script, context);
            try {
                script.run();
            }
            catch (Exception e) {}
            Map<String, ScriptParameter> paramMap = context.getScriptParameters();
            return paramMap;
        } catch (Exception x) {
            x.printStackTrace();
            return null;
        }
    }
}
