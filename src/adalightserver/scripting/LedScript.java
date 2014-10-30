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

import java.security.InvalidParameterException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import rx.Subscription;
import rx.functions.Action0;
import adalightserver.types.LedApi;

public abstract class LedScript extends groovy.lang.Script {
    private LedScriptContext _context;
    
    public LedApi getLedApi() {
        return _context.getLedApi();
    }
    
    public void stop() {
        _context.stop();
    }
    
    public Object setTimeout(int milliseconds, Action0 action) {
        if (milliseconds < 0)
            throw new InvalidParameterException("milliseconds must be positive");
        
        Action0 wrappedAction = () -> {
            try {
                action.call();
            } catch(Exception e) {
                System.out.println("Error executing " + LedScript.this.getClass().getName() + ":");
                System.out.println(e);
                stop();
            }
        };
        
        Subscription s = _context.getScheduler().schedule(wrappedAction, milliseconds, TimeUnit.MILLISECONDS);
        return s;
    }
    
    public Object repeat(int milliseconds, Action0 action) {
        if (milliseconds < 0)
            throw new InvalidParameterException("milliseconds must be positive");
        
        Action0 wrappedAction = () -> {
            try {
                action.call();
            } catch(Exception e) {
                System.out.println("Error executing " + LedScript.this.getClass().getName() + ":");
                System.out.println(e);
                stop();
            }
        };
        
        Subscription s = _context.getScheduler().schedulePeriodically(wrappedAction, milliseconds, milliseconds, TimeUnit.MILLISECONDS);
        return s;
    }
    
    public void clearTimeout(Object timerId) {
        if (timerId instanceof Subscription) {
            ((Subscription) timerId).unsubscribe();
        }
    }
    
    public void parameters(Map<String, Map<String,Object>> paramMap) {
        _context.setupBindingFromParameters(paramMap);
    }
    
    public static class Initializer
    {
        public static void init(LedScript script, LedScriptContext context) {
            script._context = context;
        }
    }
}