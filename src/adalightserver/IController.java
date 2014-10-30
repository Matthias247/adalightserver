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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import rx.Observable;

public interface IController {

    CompletableFuture<Void> setScript(String scriptName, Map<String,String> params);
    CompletableFuture<String> getCurrentScript();
    CompletableFuture<List<String>> getAvailableScripts();
    
    CompletableFuture<String> getStateAsJson();
    
    Observable<String> stateChanged();
    
    CompletableFuture<Void> stop();

}