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

package org.gradle.nativeplatform.internal.prebuilt;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Namer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.PrebuiltLibraries;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.Repositories;

public class DefaultRepositories extends DefaultPolymorphicDomainObjectContainer<ArtifactRepository> implements Repositories {
    public DefaultRepositories(Instantiator instantiator,
                               ObjectFactory objectFactory,
                               Action<PrebuiltLibrary> binaryFactory,
                               CollectionCallbackActionDecorator collectionCallbackActionDecorator,
                               DomainObjectCollectionFactory domainObjectCollectionFactory) {
        super(ArtifactRepository.class, instantiator, new ArtifactRepositoryNamer(), collectionCallbackActionDecorator);
        registerFactory(PrebuiltLibraries.class, new NamedDomainObjectFactory<PrebuiltLibraries>() {
            @Override
            public PrebuiltLibraries create(String name) {
                return domainObjectCollectionFactory.newContainer(DefaultPrebuiltLibraries.class, PrebuiltLibrary.class, name, instantiator, objectFactory, binaryFactory, domainObjectCollectionFactory);
            }
        });
    }

    private static class ArtifactRepositoryNamer implements Namer<ArtifactRepository> {
        @Override
        public String determineName(ArtifactRepository object) {
            return object.getName();
        }
    }
}
