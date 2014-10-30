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

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public class ScriptManager {
    
    Path watchPath;
    WatchService watchService;
    WatchThread watchThread;
    final CountDownLatch latch = new CountDownLatch(1);
    ScriptLoader scriptLoader = new ScriptLoader();
    
    // The scheduler on which we will load and update scripts
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Scheduler scheduler = Schedulers.from(executor);
    
    Map<String, ScriptInformation> scripts = new HashMap<>();
    
    BehaviorSubject<Map<String, ScriptInformation>> availableScriptsSubject = 
        BehaviorSubject.create(new HashMap<>());
    
    public ScriptManager(Path watchPath) {
        this.watchPath = watchPath;
    }
    
    public Observable<Map<String, ScriptInformation>> availableScriptsChanged() {
        return availableScriptsSubject;
    }
    
    public void startWatch() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchPath.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        
            watchThread = new WatchThread();
            watchThread.thread = new Thread(watchThread);
            // Listen for file updates in our directory
            watchThread.fileChanges.observeOn(scheduler).subscribe((ev) -> {
                handleFileChange(ev.path.toFile(), ev.fileExists);
            }, (e) -> {}, () -> {
                latch.countDown();
            });
            
            watchThread.thread.start();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void handleFileChange(File file, Boolean fileExists) {
        String filename = file.getName();
        if (!filename.endsWith(".groovy")) return;
        String scriptName = filename.substring(0, filename.length() - ".groovy".length());
        
        if (fileExists) {
            Class<? extends LedScript> newClass = scriptLoader.loadScript(file);
            if (newClass != null) {
                System.out.println("Loaded script " + scriptName);
                // Load the parameters for the new script
                Map<String, ScriptParameter> parameters = ScriptParameterFetcher.getParametersForScript(newClass);
                if (parameters != null) {
                    ScriptInformation newScript = new ScriptInformation(scriptName, newClass, parameters);
                    scripts.put(scriptName, newScript);
                }
            }
            else {
                scripts.remove(scriptName);
            }
        }
        else {
            scripts.remove(scriptName);
        }
        
        availableScriptsSubject.onNext(new HashMap<String, ScriptInformation>(scripts));
    }
    
    public void stopWatch() {
        try {
            watchService.close();
            watchThread.thread.join();
            latch.await();
            // Shutdown executor to stop script list updates
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            // Clear the available scripts
            scripts.clear();
            scriptLoader.dispose();
            availableScriptsSubject.onCompleted();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public CompletableFuture<ScriptInformation> getScriptByName(String scriptName) {
        CompletableFuture<ScriptInformation> f = new CompletableFuture<>();
        scheduler.createWorker().schedule(() -> {
            f.complete(scripts.get(scriptName));
        });
        return f;
    }
    
    public CompletableFuture<Map<String, ScriptInformation>> getAvailableScripts() {
        CompletableFuture<Map<String, ScriptInformation>> f = new CompletableFuture<>();
        scheduler.createWorker().schedule(() -> {
            f.complete(new HashMap<String, ScriptInformation>(scripts));
        });
        return f;
    }
    
    private class FileChangeEvent {
        public final Boolean fileExists;
        public final Path path;
        
        public FileChangeEvent(Boolean fileExists, Path path) {
            this.fileExists = fileExists;
            this.path = path;
        }
    }
    
    private class WatchThread implements Runnable {
        
        PublishSubject<FileChangeEvent> fileChanges = PublishSubject.create();
        Thread thread;
        
        @Override
        public void run() {
            
            // Initial filling
            File pathfile = watchPath.toFile();
            for (File file : pathfile.listFiles()) {
                FileChangeEvent ev = new FileChangeEvent(true, file.toPath());
                fileChanges.onNext(ev);
            }
            
            while (true) {
                final WatchKey key;
                try {
                    key = watchService.poll();
                }
                catch (ClosedWatchServiceException e) {
                    break;
                }
                if (key == null) continue;
                
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    @SuppressWarnings("unchecked")
                    final WatchEvent<Path> wePath = ( WatchEvent<Path>) watchEvent;
                    final Kind<Path> kind = wePath.kind();
                    final Path path = wePath.context();
                    final Path combinedPath = watchPath.resolve(path);
                    
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        FileChangeEvent ev = new FileChangeEvent(true, combinedPath);
                        fileChanges.onNext(ev);
                    }
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        FileChangeEvent ev = new FileChangeEvent(false, combinedPath);
                        fileChanges.onNext(ev);
                    }
                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        FileChangeEvent ev = new FileChangeEvent(true, combinedPath);
                        fileChanges.onNext(ev);
                    }
                }
                
                if (!key.reset()) { 
                    break;
                }
            }
            fileChanges.onCompleted();
        }
    }

}
