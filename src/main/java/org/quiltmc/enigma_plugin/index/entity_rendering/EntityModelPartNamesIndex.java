/*
 * Copyright 2024 QuiltMC
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

package org.quiltmc.enigma_plugin.index.entity_rendering;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.quiltmc.enigma.api.service.EnigmaServiceContext;
import org.quiltmc.enigma.api.service.JarIndexerService;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.quiltmc.enigma_plugin.Arguments;
import org.quiltmc.enigma_plugin.index.Index;
import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.JsonToken;
import org.tinylog.Logger;

public class EntityModelPartNamesIndex extends Index {
	private final Map<FieldEntry, String> fieldToValues = new HashMap<>();

	private String className;

	public EntityModelPartNamesIndex() {
		super(null);
	}

	@Override
	public void withContext(EnigmaServiceContext<JarIndexerService> context) {
		super.withContext(context);

		this.className = read(context.getSingleArgument(Arguments.ENTITY_RENDERING).map(context::getPath).orElseThrow());
	}

	private static String read(Path path) {
		try (var reader = JsonReader.json5(path)) {
			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				throw new IllegalArgumentException("entity rendering file must be a JSON object!");
			}

			reader.beginObject();

			while (reader.hasNext()) {
				String field = reader.nextName();

				if (field.equals("part_names")) {
					if (Objects.requireNonNull(reader.peek()) == JsonToken.STRING) {
						return reader.nextString();
					}

					throw new IllegalArgumentException("part_names must be a string to a class! Found " + reader.peek());
				}

				reader.skipValue();
			}

			reader.endObject();
		} catch (IOException e) {
			Logger.error(e, "Failed to read entity rendering file!");
		}

		throw new IllegalArgumentException("Did not find part_names field!");
	}

	@Override
	public void visitClassNode(ClassNode node) {
		if (node.name.equals(this.className)) {
			var parentEntry = new ClassEntry(node.name);
			for (FieldNode field: node.fields) {
				if ((field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) != 0) {
					if (field.desc.equals("Ljava/lang/String;")) {
						this.fieldToValues
							.put(
								new FieldEntry(parentEntry, field.name, new TypeDescriptor(field.desc)),
								((String) field.value)
							);
					}
				}
			}
		}
	}

	@Override
	public void onIndexingEnded() {
		// Removes multiple instances of the same part name
		this.fieldToValues.entrySet().stream()
			.collect(Collectors.<Map.Entry<FieldEntry, String>, String, Map.Entry<List<FieldEntry>, Integer>>toMap(
				Map.Entry::getValue,
				e -> Map.entry(List.of(e.getKey()), 1),
				(e1, e2) -> {
					List<FieldEntry> fields = new ArrayList<>(e1.getKey());
					fields.addAll(e2.getKey());
					return Map.entry(fields, e1.getValue() + e2.getValue());
				}
			))
			.values()
			.stream()
			.filter(entry -> entry.getValue() > 1)
			.forEach(entry -> entry.getKey().forEach(this.fieldToValues::remove));
	}

	@Override
	public void reset() {
		this.fieldToValues.clear();
	}

	public Map<FieldEntry, String> getNames() {
		return this.fieldToValues;
	}
}
