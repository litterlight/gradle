/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.upgrade.report.groovy;

import org.codehaus.groovy.runtime.callsite.CallSiteArray;

import java.util.concurrent.atomic.AtomicReference;

public class CallsiteApiUpgradeDecoratorRegistry {
    private static final AtomicReference<CallsiteApiUpgradeDecorator> REGISTRY = new AtomicReference<>(new NoopCallsiteApiUpgradeDecorator());

    public static boolean shouldDecorateCallsiteArray() {
        return REGISTRY.get().shouldDecorateCallsiteArray();
    }

    /**
     * Used from instrumented bytecode
     */
    @SuppressWarnings("unused")
    public static void decorateCallSiteArray(CallSiteArray callSites) {
        REGISTRY.get().decorateCallSiteArray(callSites);
    }

    public static void setCallsiteApiUpgradeDecorator(CallsiteApiUpgradeDecorator decorator) {
        REGISTRY.set(decorator);
    }
}
