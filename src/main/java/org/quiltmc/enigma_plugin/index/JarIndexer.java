/*
 * Copyright 2022 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.enigma_plugin.index;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.class_provider.ProjectClassProvider;
import org.quiltmc.enigma.api.service.EnigmaServiceContext;
import org.quiltmc.enigma.api.service.JarIndexerService;
import org.quiltmc.enigma_plugin.QuiltEnigmaPlugin;
import org.quiltmc.enigma_plugin.index.constant_fields.ConstantFieldIndex;
import org.quiltmc.enigma_plugin.index.entity_rendering.EntityModelPartNamesIndex;
import org.quiltmc.enigma_plugin.index.simple_type_single.SimpleTypeSingleIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class JarIndexer implements JarIndexerService, Opcodes {
	private final HashMap<Class<? extends Index>, Index> indexes = new LinkedHashMap<>();

	public JarIndexer() {
		this.addIndex(new RecordIndex());
		this.addIndex(new ConstantFieldIndex());
		this.addIndex(new CodecIndex());
		this.addIndex(new ConstructorParametersIndex());
		this.addIndex(new GetterSetterIndex());
		this.addIndex(new SimpleTypeSingleIndex());
		this.addIndex(new DelegateParametersIndex());
		this.addIndex(new LoggerIndex());
		this.addIndex(new EntityModelPartNamesIndex());
	}

	private <T extends Index> void addIndex(T index) {
		this.indexes.put(index.getClass(), index);
	}

	@SuppressWarnings("unchecked")
	public <T extends Index> T getIndex(Class<T> indexClass) {
		return (T) this.indexes.get(indexClass);
	}

	public JarIndexer withContext(EnigmaServiceContext<JarIndexerService> context) {
		for (var index : this.indexes.values()) {
			index.withContext(context);
		}

		return this;
	}

	@Override
	public void acceptJar(Set<String> scope, ProjectClassProvider classProvider, JarIndex jarIndex) {
		List<Index> enabledIndexes = new ArrayList<>(this.indexes.size());

		for (var index : this.indexes.values()) {
			index.reset();

			if (index.isEnabled()) {
				enabledIndexes.add(index);
				index.setIndexingContext(scope, jarIndex);
			}
		}

		for (String className : scope) {
			ClassNode node = classProvider.get(className);
			if (node != null) {
				for (var index : enabledIndexes) {
					index.visitClassNode(classProvider, node);
				}
			}
		}

		for (var index : enabledIndexes) {
			index.onIndexingEnded();
		}
	}

	@Override
	public String getId() {
		return QuiltEnigmaPlugin.INDEX_SERVICE_ID;
	}
}
