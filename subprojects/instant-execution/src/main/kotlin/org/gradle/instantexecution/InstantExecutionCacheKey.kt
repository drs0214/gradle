/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.instantexecution.extensions.unsafeLazy
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.internal.hash.HashValue
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import java.io.File


class InstantExecutionCacheKey(
    private val startParameter: InstantExecutionStartParameter
) {

    val string: String by unsafeLazy {
        HashValue(
            Hashing.md5().newHasher().apply {
                putCacheKeyComponents()
            }.hash().toByteArray()
        ).asCompactString()
    }

    override fun toString() = string

    override fun hashCode(): Int = string.hashCode()

    private
    fun Hasher.putCacheKeyComponents() {
        putString(GradleVersion.current().version)

        val requestedTaskNames = startParameter.requestedTaskNames
        putAll(requestedTaskNames)

        val excludedTaskNames = startParameter.excludedTaskNames
        putAll(excludedTaskNames)

        val taskNames = requestedTaskNames.asSequence() + excludedTaskNames.asSequence()
        val hasRelativeTaskName = taskNames.any { !it.startsWith(':') }
        if (hasRelativeTaskName) {
            // Because unqualified task names are resolved relative to the enclosing
            // sub-project according to `invocationDirectory`,
            // the relative invocation directory information must be part of the key.
            relativeChildPathOrNull(
                startParameter.currentDirectory,
                startParameter.rootDirectory
            )?.let { relativeSubDir ->
                putString(relativeSubDir)
            }
        }
    }

    private
    fun Hasher.putAll(list: Collection<String>) {
        putInt(list.size)
        list.forEach(::putString)
    }

    /**
     * Returns the path of [target] relative to [base] if
     * [target] is a child of [base] or `null` otherwise.
     */
    private
    fun relativeChildPathOrNull(target: File, base: File): String? =
        GFileUtils.relativePathOf(target, base)
            .takeIf { !it.startsWith('.') }
}
