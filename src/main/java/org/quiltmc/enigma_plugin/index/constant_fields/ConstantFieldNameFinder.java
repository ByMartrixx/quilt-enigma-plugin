/*
 * Copyright 2016, 2017, 2018, 2019 FabricMC
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

package org.quiltmc.enigma_plugin.index.constant_fields;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.tinylog.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConstantFieldNameFinder implements Opcodes {
	private final HashMap<String, Set<String>> usedNamesByClass = new HashMap<>();
	private final HashMap<String, Set<String>> duplicatedNamesByClass = new HashMap<>();
	private final HashMap<FieldEntry, FieldEntry> linkedFields = new HashMap<>();

	private static boolean isClassPutStatic(String owner, AbstractInsnNode insn) {
		return insn.getOpcode() == PUTSTATIC && ((FieldInsnNode) insn).owner.equals(owner);
	}

	private static boolean isInit(AbstractInsnNode insn) {
		return insn.getOpcode() == INVOKESPECIAL && ((MethodInsnNode) insn).name.equals("<init>");
	}

	private static boolean isCharacterUsable(char c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
	}

	private static String stringToUpperSnakeCase(String s) {
		StringBuilder usableName = new StringBuilder();
		boolean hasAlphabetic = false;

		for (int j = 0; j < s.length(); j++) {
			char c = s.charAt(j);

			if (isCharacterUsable(c)) {
				if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
					hasAlphabetic = true;
				}

				if (j > 0 && Character.isUpperCase(c) && j < s.length() - 1 && Character.isLowerCase(s.charAt(j + 1))) {
					usableName.append('_');
				}

				usableName.append(c);
			} else {
				usableName.append('_');
			}
		}

		if (!hasAlphabetic) {
			return null;
		}

		return usableName.toString().toUpperCase();
	}

	private FieldEntry followFieldLink(FieldEntry field, Map<FieldEntry, String> names) {
		if (names.containsKey(field)) {
			return field;
		} else if (this.linkedFields.containsKey(field)) {
			return followFieldLink(this.linkedFields.get(field), names);
		}

		return null;
	}

	private void clear() {
		this.usedNamesByClass.clear();
		this.duplicatedNamesByClass.clear();
		this.linkedFields.clear();
	}

	public Map<FieldEntry, String> findNames(ConstantFieldIndex fieldIndex) throws Exception {
		this.clear();

		Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
		Map<FieldEntry, String> fieldNames = new HashMap<>();

		for (String clazz : fieldIndex.getStaticInitializers().keySet()) {
			var initializers = fieldIndex.getStaticInitializers().get(clazz);

			findNamesInInitializers(clazz, initializers, analyzer, fieldNames);
		}

		// Insert linked names
		for (FieldEntry linked : this.linkedFields.keySet()) {
			FieldEntry target = followFieldLink(linked, fieldNames);
			String name = fieldNames.get(target);

			String clazz = linked.getParent().getFullName();
			Set<String> usedNames = this.usedNamesByClass.computeIfAbsent(clazz, s -> new HashSet<>());
			Set<String> duplicatedNames = this.duplicatedNamesByClass.computeIfAbsent(clazz, s -> new HashSet<>());
			if (!duplicatedNames.contains(name) && usedNames.add(name)) {
				fieldNames.put(linked, name);
			} else {
				duplicatedNames.add(name);
				Logger.warn("Duplicate name \"{}\" for field {}", name, linked);
			}
		}

		return fieldNames;
	}

	private void findNamesInInitializers(String clazz, List<MethodNode> initializers, Analyzer<SourceValue> analyzer, Map<FieldEntry, String> fieldNames) throws AnalyzerException {
		var usedNames = this.usedNamesByClass.computeIfAbsent(clazz, s -> new HashSet<>());
		var duplicatedNames = this.duplicatedNamesByClass.computeIfAbsent(clazz, s -> new HashSet<>());

		for (var initializer : initializers) {
			var frames = analyzer.analyze(clazz, initializer);
			var instructions = initializer.instructions;

			for (int i = 1; i < instructions.size(); i++) {
				var insn = instructions.get(i);
				var prevInsn = insn.getPrevious();

				if (!isClassPutStatic(clazz, insn)) {
					continue;
				}

				/*
				 * We want instructions in the form of
				 * prevInsn: INVOKESTATIC ${clazz}.* (*)L*; || INVOKESPECIAL ${clazz}.<init> (*)V
				 * insn:     PUTSTATIC ${clazz}.* : L*;
				 */
				var putStatic = (FieldInsnNode) insn;
				if (!(prevInsn instanceof MethodInsnNode invokeInsn) || !invokeInsn.owner.equals(clazz)) {
					continue; // Ensure the previous instruction was an invocation of one of this class' methods
				}

				if (invokeInsn.getOpcode() != INVOKESTATIC && !isInit(invokeInsn)) {
					continue; // Ensure the invocation is either an INVOKESTATIC or a constructor invocation
				}

				// Search for a name within the frame for the invocation instruction
				var frame = frames[i - 1];
				String name = null;
				for (int j = 0; j < frame.getStackSize(); j++) {
					var value = frame.getStack(j);
					for (var stackInsn : value.insns) {
						if (stackInsn instanceof LdcInsnNode ldc && ldc.cst instanceof String constant && !constant.isBlank()) {
							name = constant;
							break;
						}
					}
				}

				if (name == null) {
					// If we couldn't find a name, try to link this field to one from another class instead
					FieldInsnNode otherFieldInsn = null;
					for (int j = 0; j < frame.getStackSize(); j++) {
						var value = frame.getStack(j);
						AbstractInsnNode lastStackInsn = null;
						for (var stackInsn : value.insns) {
							lastStackInsn = stackInsn;
							if (stackInsn instanceof FieldInsnNode fieldInsn && fieldInsn.getOpcode() == GETSTATIC && !fieldInsn.owner.equals(clazz)) {
								otherFieldInsn = fieldInsn;
								break;
							}
						}

						/* Search between the last stack instruction and the invocation instruction, useful for parameters passed to a constructor
						 * lastStackInsn: NEW * // Last instruction in the stack
						 *                DUP
						 *                GETSTATIC !${clazz}.* : * // Instruction we are looking for
						 *                INVOKESPECIAL *.<init> (*)V
						 * invokeInsn:    INVOKESPECIAL ${clazz}.<init> (L*;*)V
						 */
						if (otherFieldInsn == null && lastStackInsn != null) {
							var stackInsn = lastStackInsn;
							while ((stackInsn = stackInsn.getNext()) != null && stackInsn != invokeInsn) {
								if (stackInsn instanceof FieldInsnNode fieldInsn && fieldInsn.getOpcode() == GETSTATIC && !fieldInsn.owner.equals(clazz)) {
									otherFieldInsn = fieldInsn;
									break;
								}
							}
						}
					}

					if (otherFieldInsn != null) {
						this.linkedFields.put(fieldFromInsn(putStatic), fieldFromInsn(otherFieldInsn));
					}

					continue; // Done with the current putStatic
				}

				// Remove identifier namespace
				if (name.contains(":")) {
					name = name.substring(name.lastIndexOf(":") + 1);
				}

				// Process a path
				if (name.contains("/")) {
					int separator = name.indexOf("/");
					String first = name.substring(0, separator);
					String last;

					if (name.contains(".") && name.indexOf(".") > separator) {
						last = name.substring(separator + 1, name.indexOf("."));
					} else {
						last = name.substring(separator + 1);
					}

					if (first.endsWith("s")) {
						first = first.substring(0, first.length() - 1);
					}

					name = last + "_" + first;
				}

				var fieldName = stringToUpperSnakeCase(name);

				if (fieldName == null || fieldName.isEmpty()) {
					continue;
				}

				if (!duplicatedNames.contains(fieldName)) {
					if (!usedNames.add(fieldName)) {
						Logger.warn("Duplicate key: " + fieldName + " (" + name + ") in " + clazz);
						duplicatedNames.add(fieldName);
						usedNames.remove(fieldName);
					}
				}

				if (usedNames.contains(fieldName)) {
					fieldNames.put(fieldFromInsn(putStatic), fieldName);
				}
			}
		}
	}

	private static FieldEntry fieldFromInsn(FieldInsnNode insn) {
		return new FieldEntry(new ClassEntry(insn.owner), insn.name, new TypeDescriptor(insn.desc));
	}
}
