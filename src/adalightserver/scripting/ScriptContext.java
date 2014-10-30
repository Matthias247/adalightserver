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

import java.util.Map;
import java.util.concurrent.Future;

import adalightserver.types.LedApi;
import rx.Scheduler;
import rx.subjects.AsyncSubject;

public class ScriptContext implements LedScriptContext {
    private Scheduler.Worker scheduler;
    private LedApi ledApi;
    private AsyncSubject<Boolean> finished = AsyncSubject.create();
    private Future<Boolean> completionFuture = finished.toBlocking().toFuture();
    private Boolean stopped = false;
    private ScriptInformation scriptInfo;
    private groovy.lang.Script groovyScript;
    
    public ScriptContext(Scheduler schedulerFactory, LedApi api, ScriptInformation scriptInfo, Map<String,String> params) {
        this.scheduler = schedulerFactory.createWorker();
        this.ledApi = api;
        // Clone the info to be able to add custom info
        this.scriptInfo = new ScriptInformation(scriptInfo);
        
        LedScript script = null;
        try {
            script = scriptInfo.script.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        LedScript.Initializer.init(script, this);
        
        this.groovyScript = script;
        setupParameters(params);
    }

    public void run() {
        scheduler.schedule(() -> {
            try {
                groovyScript.run();
            } catch(Exception e) {
                System.out.println("Error executing " + groovyScript.getClass().getName() + ":");
                System.out.println(e);
                stop();
            }
        });
    }
    
    public LedApi getLedApi() {
        return ledApi;
    }

    public Scheduler.Worker getScheduler() {
        return scheduler;
    }
    
    public ScriptInformation getScriptInformation() {
        return scriptInfo;
    }
    
    public final Future<Boolean> getCompletionFuture() {
        return completionFuture;
    }
    
    public final void stop() {
        if (!stopped) {
            stopped = true;
            scheduler.unsubscribe();
            finished.onNext(true);
            finished.onCompleted();
        }
    }
    
    private void setupParameters(Map<String,String> params) {
        scriptInfo.parameters.forEach((pname, param) -> {
            String paramValue = params.get(pname);
            param.parseParameter(paramValue);
            groovyScript.getBinding().setVariable(pname, param.currentValue);
        });
    }
    
    public void setupBindingFromParameters(Map<String, Map<String,Object>> paramMap) {
    }
    
    public String getScriptName() {
        return scriptInfo.name;
    }
}