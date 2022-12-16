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

package org.gradle.internal.execution.schema;

import org.gradle.internal.fingerprint.FileNormalizer;

import java.util.function.Supplier;

public class AbstractFilePropertySchema extends AbstractWorkPropertySchema implements FilePropertySchema {
    private final FileNormalizer normalizer;

    public AbstractFilePropertySchema(String qualifiedName, boolean optional, FileNormalizer normalizer, Supplier<Object> valueResolver) {
        super(qualifiedName, optional, valueResolver);
        this.normalizer = normalizer;
    }

    @Override
    public FileNormalizer getNormalizer() {
        return normalizer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        AbstractFilePropertySchema that = (AbstractFilePropertySchema) o;

        return normalizer.equals(that.normalizer);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + normalizer.hashCode();
        return result;
    }
}