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

package adalightserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import adalightserver.scripting.ScriptContext;
import adalightserver.scripting.ScriptInformation;
import adalightserver.scripting.ScriptManager;
import adalightserver.types.ColorRgb;
import adalightserver.types.LedApi;

public class Controller implements IController {
    
    Scheduler scriptSchedulerFactory = Schedulers.newThread();
    Scheduler scheduler = new SingleThreadedComputationScheduler();
    ScriptContext activeScript;
    ScriptManager scriptManager;
    LedApi api;
    
    Map<String, ScriptInformation> availableScripts = new HashMap<>();
    Subscription scriptSub;
    
    private enum Mode {
        None,
        Script
    }
    
    private Mode mode = Mode.None;
    
    public Controller(LedApi api, ScriptManager scriptManager) {
        this.api = api;
        this.scriptManager = scriptManager;
        
        scriptSub = 
        scriptManager.availableScriptsChanged()
                     .observeOn(scheduler)
                     .subscribe(scriptMap -> {
                         availableScripts = scriptMap;
                         stateSubject.onNext(createStateJson());
                     });
    }

    @Override
    public CompletableFuture<Void> stop() {
        final CompletableFuture<Void> f = new CompletableFuture<>();
        scheduler.createWorker().schedule(() -> {
            stopActiveScript();
            f.complete(null);
        });
        return f;
    }    
    
    public CompletableFuture<Void> setScript(String scriptName, Map<String,String> params) {
        final CompletableFuture<Void> f = new CompletableFuture<>();
        scheduler.createWorker().schedule(() -> {
            stopActiveScript();
            System.out.println("Setting to script " + scriptName + " with params " + params);
            
            ScriptInformation scriptInfo;
            try {
                scriptInfo = scriptManager.getScriptByName(scriptName).get();
            }
            catch (Exception e1) {
                f.completeExceptionally(e1);
                return;
            }
            if (scriptInfo == null) {
                f.completeExceptionally(new RuntimeException("Invalid script name"));
                return;
            }
            
            try {
                activeScript = new ScriptContext(scriptSchedulerFactory, api, scriptInfo, params);
                activeScript.run();
                mode = Mode.Script;
            } catch (Exception e) {
                e.printStackTrace();
                f.completeExceptionally(new RuntimeException("Error starting script " + scriptName));
                return;
            }
            
            stateSubject.onNext(createStateJson());
            f.complete(null);
        });
        return f;
    }
    
    @Override
    public CompletableFuture<String> getCurrentScript() {
        final CompletableFuture<String> f = new CompletableFuture<>();
        scheduler.createWorker().schedule(() -> {
            if (mode != Mode.Script) f.complete("");
            else f.complete(activeScript.getScriptName());
        });
        return f;
    }
    
    @Override
    public CompletableFuture<List<String>> getAvailableScripts() {
        CompletableFuture<List<String>> c = new CompletableFuture<List<String>>();
        c.complete(new ArrayList<String>(availableScripts.keySet()));
        return c;
    }
    
    private void stopActiveScript() {
        if (mode != Mode.Script) return;
        
        activeScript.getScheduler().schedule(() -> { activeScript.stop(); });
        // Wait till the script completes
        try {
            activeScript.getCompletionFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        activeScript = null;
        
        try {
            api.setAllLedsToColor(new ColorRgb(0,0,0));
            api.flush();
        } catch (Exception e) {}
        
        mode = Mode.None;
        
        stateSubject.onNext(createStateJson());
    }
    
    @Override
    public CompletableFuture<String> getStateAsJson() {
        final CompletableFuture<String> f = new CompletableFuture<>();
        scheduler.createWorker().schedule(() -> {
            f.complete(createStateJson());
        });
        return f;
    }
    
    private String createStateJson() {
        StringBuilder s = new StringBuilder();
        s.append("{");
        s.append("\"mode\": \"");
        if (mode == Mode.Script) s.append("script");
        else s.append("none");
        s.append("\", \"active_script\": ");
        if (mode == Mode.Script) {
            s.append(activeScript.getScriptInformation().toJson());
        }
        else s.append("{}");
        s.append(", \"available_scripts\": [");
        s.append(availableScripts.values().stream()
                .map(ScriptInformation::toJson)
                .collect(Collectors.joining(", ")));
        s.append("]}");
        return s.toString();
    }
    
    BehaviorSubject<String> stateSubject = BehaviorSubject.create("{}");
    
    public Observable<String> stateChanged() {
        return stateSubject;
    }
}
