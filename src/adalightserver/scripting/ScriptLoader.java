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

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.syntax.Types;

import adalightserver.types.ColorHsv;
import adalightserver.types.ColorRgb;
import adalightserver.types.LedApi;


public class ScriptLoader {
    
    private ClassLoader parent = getClass().getClassLoader();
    private CompilerConfiguration config = new CompilerConfiguration();
    private GroovyClassLoader loader;
    
    public ScriptLoader() {
        config.setScriptBaseClass("adalightserver.types.LedScript");
        
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addStarImports("adalightserver.types");
        
        final SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setClosuresAllowed(true);
        secure.setMethodDefinitionAllowed(true);
        secure.setImportsWhitelist(Arrays.asList("java.lang.Math","java.lang.Object"));
        secure.setStarImportsWhitelist(Arrays.asList("adalightserver.types"));
        secure.setStaticImportsWhitelist(Arrays.asList("java.lang.Object.parameters","java.lang.Object.println"));
        secure.setStaticStarImportsWhitelist(Arrays.asList("adalightserver.types", "java.lang.Math","java.lang.Object","adalightserver.types.LedApi","adalightserver.types.ColorHsv", "adalightserver.types.ColorRgb")); // only java.lang.Math is allowed
        
        secure.setTokensWhitelist(Arrays.asList(
            Types.PLUS,
            Types.MINUS,
            Types.MULTIPLY,
            Types.DIVIDE,
            Types.MOD,
            Types.POWER,
            Types.PLUS_PLUS,
            Types.MINUS_MINUS,
            Types.COMPARE_EQUAL,
            Types.COMPARE_NOT_EQUAL,
            Types.COMPARE_LESS_THAN,
            Types.COMPARE_LESS_THAN_EQUAL,
            Types.COMPARE_GREATER_THAN,
            Types.COMPARE_GREATER_THAN_EQUAL,
            Types.ASSIGN));
        
        secure.setConstantTypesClassesWhiteList(Arrays.asList(
            Object.class,
            Integer.class,
            Float.class,
            Long.class,
            Double.class,
            BigDecimal.class,
            String.class,
            Integer.TYPE,
            Long.TYPE,
            Float.TYPE,
            Double.TYPE,
            LedApi.class));
        
        secure.setReceiversClassesWhiteList(Arrays.asList(
            Object.class,
            Math.class,
            Integer.class,
            Float.class,
            Double.class,
            Long.class,
            BigDecimal.class,
            String.class,
            ColorHsv.class,
            ColorRgb.class,
            LedApi.class));
        
        secure.setIndirectImportCheckEnabled(true);
        secure.setPackageAllowed(true);
    
        config.addCompilationCustomizers(importCustomizer);
        
        loader = new GroovyClassLoader(parent, config);
    }
    
    public Class<? extends LedScript> loadScript(File filename) {
        loader.clearCache();
        try {
            if (!filename.getName().endsWith(".groovy")) return null;
            
            Class<?> groovyClass = loader.parseClass(filename);
            
            if (LedScript.class.isAssignableFrom(groovyClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends LedScript> r = (Class<? extends LedScript>) groovyClass;
                return r;
            }
            else {
                return null;
            }
        } catch (Exception e) {
            System.out.println("Error loading " + filename.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    public void dispose() {
        try {
            loader.close();
        } catch (IOException e) {
           e.printStackTrace();
        }
    }
}
