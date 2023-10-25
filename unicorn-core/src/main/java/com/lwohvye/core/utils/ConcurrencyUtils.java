/*
 *    Copyright (c) 2022-2023.  lWoHvYe(Hongyan Wang)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.lwohvye.core.utils;

import com.lwohvye.core.exception.UtilsException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@UtilityClass
public class ConcurrencyUtils extends UnicornAbstractThreadUtils {


    /**
     * Basic flow : execute tasks, the result as the input of composeResult, the previous res as the input of eventual
     *
     * @param composeResult consume the task res
     * @param eventual      finally execute, consume the res of  composeResult
     * @param tasks         tasks wtd
     * @date 2022/9/22 8:26 PM
     */
    public static void structuredExecute(Function<List<?>, ?> composeResult, Consumer<Object> eventual, Callable<?>... tasks) {
        log.warn("In Java 17 Source");
        List<? extends Future<?>> subtasks = null;
        if (Objects.nonNull(tasks))
            subtasks = Arrays.stream(tasks).map(TASK_EXECUTOR::submit).toList();

        Object results = null;
        if (Objects.nonNull(composeResult))
            results = composeResult.apply(Objects.nonNull(subtasks) ?
                    subtasks.stream().map(future -> {
                        try {
                            return future.get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            throw new UtilsException(e.getMessage());
                        }
                        return null;
                    }).toList() : Collections.emptyList());
        if (Objects.nonNull(eventual))
            eventual.accept(results);

    }

    // Returns a Callable object that, when called, runs the given task and returns null.
    // var callable = Executors.callable(runnable) // Runnable 2 Callable
    // A FutureTask can be used to wrap a Callable or Runnable object. Because FutureTask implements Runnable, a FutureTask can be submitted to an Executor for execution.
    // var futureTask = new FutureTask<T>(Callable/Runnable) // Callable/Runnable 2 Runnable/Future
    public static void structuredExecute(Runnable eventual, Runnable... tasks) {
        log.warn("In Java 17 Source");
        if (Objects.nonNull(tasks))
            Arrays.stream(tasks).forEach(TASK_EXECUTOR::execute);
        // Here, both forks have succeeded, so compose their results
        if (Objects.nonNull(eventual))
            log.warn("Unsupported Java below 21 Skip EventualTask");
    }

    public static Runnable withMdc(Runnable runnable) {
        var mdc = MDC.getCopyOfContextMap();
        return () -> {
            MDC.setContextMap(mdc);
            runnable.run();
        };
    }

    public static <U> Supplier<U> withMdc(Supplier<U> supplier) {
        var mdc = MDC.getCopyOfContextMap();
        return () -> {
            MDC.setContextMap(mdc);
            return supplier.get();
        };
    }
}
