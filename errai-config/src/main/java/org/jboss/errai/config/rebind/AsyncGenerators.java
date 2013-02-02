/*
 * Copyright 2012 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.config.rebind;

import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.typeinfo.TypeOracleException;
import org.jboss.errai.common.metadata.ScannerSingleton;
import org.jboss.errai.common.rebind.CacheUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * @author Mike Brock
 */
public final class AsyncGenerators {
  private AsyncGenerators() {
  }

  private static final Object lock = new Object();
  private static volatile boolean started = false;
  private static volatile GeneratorContext currentContext;

  private static final Map<Class, AsyncCodeGenerator> codeGenerators = new ConcurrentHashMap<Class, AsyncCodeGenerator>();
  private static final Map<Class, Future<String>> activeFutures = new ConcurrentHashMap<Class, Future<String>>();

  static Future<String> getFutureFor(final AsyncGenerationJob job) {
    synchronized (lock) {
      startAll(job);

      if (!codeGenerators.containsKey(job.getInterfaceType())) {
        throw new RuntimeException("no generator found for interface: " + job.getInterfaceType().getName());
      }

      if (activeFutures.containsKey(job.getInterfaceType())) {
        return activeFutures.get(job.getInterfaceType());
      }
      else {
        throw new RuntimeException("could not find future for interface: " + job.getInterfaceType().getName());
      }
    }
  }

  private static void startAll(final AsyncGenerationJob job) {
    if (started && job.getGeneratorContext() != currentContext) {
      started = false;
    }

    if (!started) {
      CacheUtil.clearAll();

      started = true;
      currentContext = job.getGeneratorContext();

      job.notifyStarting();

      for (final Class<?> cls : ScannerSingleton.getOrCreateInstance().getTypesAnnotatedWith(GenerateAsync.class)) {
        try {
          final AsyncCodeGenerator asyncCodeGenerator
              = cls.asSubclass(AsyncCodeGenerator.class).newInstance();

          final GenerateAsync generateAsync = cls.getAnnotation(GenerateAsync.class);

          try {
            job.getGeneratorContext().getTypeOracle().getType(generateAsync.value().getName());
            codeGenerators.put(generateAsync.value(), asyncCodeGenerator);
            System.out.println(" ****** REGISTERED ASYNC CODE GENERATOR: " + generateAsync.value());
          }
          catch (TypeOracleException e) {
            e.printStackTrace();
            // ignore because not inherited in an active module.
          }
        }
        catch (Throwable e) {
        }
      }

      job.notifyStarted();

      for (final Map.Entry<Class, AsyncCodeGenerator> entry : codeGenerators.entrySet()) {
        activeFutures.put(entry.getKey(),
            entry.getValue().generateAsync(job.getTreeLogger(), job.getGeneratorContext()));
        System.out.println("  ASYNC GENERATION BEGAN >> " + entry.getKey().getName());
      }
    }
    else {
      job.notifyStarted();
    }
  }
}