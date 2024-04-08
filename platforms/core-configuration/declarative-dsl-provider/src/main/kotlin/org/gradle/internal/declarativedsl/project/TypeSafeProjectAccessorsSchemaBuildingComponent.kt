/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.project

import org.gradle.api.Project
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.internal.declarativedsl.analysis.DataTypeRef
import org.gradle.internal.declarativedsl.analysis.FqNameImpl
import org.gradle.internal.declarativedsl.analysis.DataProperty.PropertyMode
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver
import org.gradle.internal.declarativedsl.schemaBuilder.CollectedPropertyInformation
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultPropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf


/**
 * Brings the typesafe project accessors into the schema if the feature is enabled by doing the following:
 * * in the [ProjectTopLevelReceiver] type, introduces a `projects` property of type `RootProjectAccessor`;
 * * brings the `RootProjectAccessor` type into type discovery, and
 * * ensures type discovery going recursively over the generated typesafe accessor containers (see [TypesafeProjectAccessorTypeDiscovery]);
 * * for each typesafe accessor container type, extracts its properties (see [TypesafeProjectPropertyProducer]);
 * * at runtime, resolves property access to the `projects` property on [Project] instances.
 */
internal
class TypesafeProjectAccessorsComponent(targetScope: ClassLoaderScope) : EvaluationSchemaComponent {
    private
    val projectAccessorsClass: KClass<*>? = try {
        targetScope.localClassLoader.loadClass("org.gradle.accessors.dm.RootProjectAccessor").kotlin
    } catch (e: ClassNotFoundException) {
        null
    }

    private
    val projectAccessorsExtension: CollectedPropertyInformation? = projectAccessorsClass?.let {
        CollectedPropertyInformation(
            "projects",
            projectAccessorsClass.createType(),
            returnType = DataTypeRef.Name(FqNameImpl.parse(projectAccessorsClass.qualifiedName!!)),
            propertyMode = PropertyMode.READ_ONLY,
            hasDefaultValue = true,
            isHiddenInDeclarativeDsl = false,
            isDirectAccessOnly = false,
            claimedFunctions = emptyList()
        )
    }

    override fun propertyExtractors(): List<PropertyExtractor> = when (projectAccessorsExtension) {
        null -> emptyList()
        else -> listOf(
            TypesafeProjectPropertyProducer(),
            ExtensionProperties(mapOf(ProjectTopLevelReceiver::class to listOf(projectAccessorsExtension)))
        )
    }

    override fun typeDiscovery(): List<TypeDiscovery> = when (projectAccessorsClass) {
        null -> emptyList()
        else -> listOf(
            FixedTypeDiscovery(ProjectTopLevelReceiver::class, listOf(projectAccessorsClass)),
            TypesafeProjectAccessorTypeDiscovery()
        )
    }

    override fun runtimePropertyResolvers(): List<RuntimePropertyResolver> = listOf(ProjectPropertyAccessorRuntimeResolver())
}


private
class ProjectPropertyAccessorRuntimeResolver : RuntimePropertyResolver {
    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.ReadResolution =
        if (receiverClass.isSubclassOf(Project::class) && name == "projects") {
            RuntimePropertyResolver.ReadResolution.ResolvedRead { receiver -> (receiver as Project).extensions.getByName("projects") }
        } else RuntimePropertyResolver.ReadResolution.UnresolvedRead

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String) = RuntimePropertyResolver.WriteResolution.UnresolvedWrite
}


private
class TypesafeProjectAccessorTypeDiscovery : TypeDiscovery {
    override fun getClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> {
        return if (kClass.isGeneratedAccessors()) {
            allClassesReachableFromGetters(kClass).flatMapTo(mutableSetOf(), ::allSupertypes)
        } else {
            emptyList()
        }
    }

    private
    fun allClassesReachableFromGetters(kClass: KClass<*>) = buildSet {
        fun visit(kClass: KClass<*>) {
            if (add(kClass)) {
                val properties = propertyFromTypesafeProjectGetters.extractProperties(kClass)
                val typesFromGetters = properties.mapNotNull { it.originalReturnType.classifier as? KClass<*> }
                typesFromGetters.forEach(::visit)
            }
        }
        visit(kClass)
    }

    private
    fun allSupertypes(kClass: KClass<*>) = buildSet<KClass<*>> {
        fun visit(type: KType) {
            val classifier = type.classifier
            if (classifier is KClass<*> && add(classifier)) {
                (classifier.supertypes - Any::class.createType()).forEach(::visit) // No need to visit Any, it only clutters the extracted functions
            }
        }
        add(kClass)
        kClass.supertypes.forEach(::visit)
    }
}


private
class TypesafeProjectPropertyProducer : PropertyExtractor {
    override fun extractProperties(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> =
        if (kClass.isGeneratedAccessors()) {
            propertyFromTypesafeProjectGetters.extractProperties(kClass, propertyNamePredicate)
        } else emptyList()
}


private
val propertyFromTypesafeProjectGetters = DefaultPropertyExtractor { property ->
    (property.returnType.classifier as? KClass<*>)?.isGeneratedAccessors() == true
}


private
fun KClass<*>.isGeneratedAccessors() =
    // TODO: find a better way to filter the accessor types
    qualifiedName.orEmpty().startsWith("org.gradle.accessors.dm.")
