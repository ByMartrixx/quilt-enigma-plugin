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

package org.quiltmc.enigma_plugin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.enigma.api.Enigma;
import org.quiltmc.enigma.api.EnigmaProfile;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.class_provider.ClasspathClassProvider;
import org.quiltmc.enigma.api.source.TokenType;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.quiltmc.enigma.api.translation.representation.MethodDescriptor;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.quiltmc.enigma.api.translation.representation.entry.LocalVariableEntry;
import org.quiltmc.enigma.api.translation.representation.entry.MethodEntry;
import org.quiltmc.enigma.util.validation.ValidationContext;

import java.io.IOException;
import java.nio.file.Path;

public class NameProposalTest {
	private static final Path JAR = Path.of("build/obf/obf.jar");
	private static final Path PROFILE = Path.of("build/resources/testInputs/profile.json");
	private static EntryRemapper remapper;

	@BeforeAll
	public static void setupEnigma() throws IOException {
		var profile = EnigmaProfile.read(PROFILE);
		var enigma = Enigma.builder().setProfile(profile).build();

		var project = enigma.openJar(JAR, new ClasspathClassProvider(), ProgressListener.createEmpty());
		remapper = project.getRemapper();

		// Manually fire dynamic proposals
		remapper.insertDynamicallyProposedMappings(null, null, null);
	}

	public static void assertProposal(String name, Entry<?> entry) {
		var mapping = remapper.getMapping(entry);
		Assertions.assertNotNull(mapping);
		Assertions.assertEquals(name, mapping.targetName());
		Assertions.assertEquals(TokenType.JAR_PROPOSED, mapping.tokenType());
	}

	public static void assertDynamicProposal(String name, Entry<?> entry) {
		var mapping = remapper.getMapping(entry);
		Assertions.assertNotNull(mapping);
		Assertions.assertEquals(name, mapping.targetName());
		Assertions.assertEquals(TokenType.DYNAMIC_PROPOSED, mapping.tokenType());
	}

	public static void assertNotProposed(Entry<?> entry) {
		var mapping = remapper.getMapping(entry);
		Assertions.assertNotNull(mapping);
		Assertions.assertEquals(EntryMapping.OBFUSCATED, mapping);
	}

	public static void assertNotProposedBy(Entry<?> entry, String unexpectedSourceProposerId) {
		var mapping = remapper.getMapping(entry);
		Assertions.assertNotNull(mapping);
		if (mapping.sourcePluginId() != null) {
			Assertions.assertNotEquals(QuiltEnigmaPlugin.NAME_PROPOSAL_SERVICE_ID + "/" + unexpectedSourceProposerId, mapping.sourcePluginId());
		} else {
			Assertions.assertEquals(EntryMapping.OBFUSCATED, mapping);
		}
	}

	@Test
	public void testRecordNames() {
		var classEntry = new ClassEntry("com/a/d");

		assertProposal("value", field(classEntry, "b", "I"));
		assertProposal("value", method(classEntry, "a", "()I"));

		assertProposal("scale", field(classEntry, "c", "D"));
		assertProposal("scale", method(classEntry, "b", "()D"));

		assertProposal("s", field(classEntry, "d", "Ljava/util/Optional;"));
		assertProposal("s", method(classEntry, "c", "()Ljava/util/Optional;"));

		// Overridden component getters could return other components, there's no way to be sure which (overridden) getter corresponds to which component
		classEntry = new ClassEntry(classEntry, "a");

		assertNotProposedBy(method(classEntry, "a", "()D"), "records");
		assertNotProposed(method(classEntry, "b", "()D"));
		assertProposal("value", method(classEntry, "c", "()I"));

		var withCodecEntry = new ClassEntry("com/a/a$a");

		assertProposal("value", field(withCodecEntry, "b", "I"));
		assertProposal("value", method(withCodecEntry, "a", "()I"));
	}

	@Test
	public void testConstantFieldNames() {
		var classEntry = new ClassEntry("com/a/b/c");
		var desc = new TypeDescriptor("Lcom/a/b/f;");

		assertProposal("FOO", new FieldEntry(classEntry, "a", desc));
		assertProposal("BAR_FOO", new FieldEntry(classEntry, "b", desc));
		assertProposal("BAZ_FOO", new FieldEntry(classEntry, "c", desc));
		assertProposal("LOREM_IPSUM_BAZ", new FieldEntry(classEntry, "d", desc));
		assertProposal("AN_ID", new FieldEntry(classEntry, "e", desc));
		assertProposal("ANOTHER_ID_FOO", new FieldEntry(classEntry, "f", desc));
		assertProposal("ONE", new FieldEntry(classEntry, "g", desc));
		assertProposal("TWO", new FieldEntry(classEntry, "h", desc));
		assertProposal("THREE", new FieldEntry(classEntry, "i", desc));

		var class2Entry = new ClassEntry("com/a/b/a");

		assertProposal("FOO", new FieldEntry(class2Entry, "a", desc));
		assertProposal("BAR_FOO", new FieldEntry(class2Entry, "b", desc));
		assertProposal("AN_ID", new FieldEntry(class2Entry, "c", desc));
		assertProposal("NORTH", new FieldEntry(class2Entry, "d", desc));
		assertProposal("EAST", new FieldEntry(class2Entry, "e", desc));

		var class3Entry = new ClassEntry("com/a/b/b");

		assertProposal("FOO", new FieldEntry(class3Entry, "a", desc));
		assertProposal("BAR_FOO", new FieldEntry(class3Entry, "b", desc));

		var enumEntry = new ClassEntry("com/a/b/e");
		var enumDesc = new TypeDescriptor("Lcom/a/b/e;");

		assertProposal("NORTH", new FieldEntry(enumEntry, "a", enumDesc));
		assertProposal("EAST", new FieldEntry(enumEntry, "b", enumDesc));
		assertProposal("WEST", new FieldEntry(enumEntry, "c", enumDesc));
		assertProposal("SOUTH", new FieldEntry(enumEntry, "d", enumDesc));

		var enum2Entry = new ClassEntry("com/a/b/d");
		var enum2Desc = new TypeDescriptor("Lcom/a/b/d;");

		assertProposal("CONSTANT", new FieldEntry(enum2Entry, "a", enum2Desc));
		assertProposal("INT", new FieldEntry(enum2Entry, "b", enum2Desc));
		assertProposal("DOUBLE", new FieldEntry(enum2Entry, "c", enum2Desc));
		assertProposal("EXPONENTIAL", new FieldEntry(enum2Entry, "d", enum2Desc));
		assertProposal("GAUSSIAN", new FieldEntry(enum2Entry, "e", enum2Desc));
	}

	@Test
	public void testCodecNames() {
		var classEntry = new ClassEntry("com/a/a");

		assertProposal("value", field(classEntry, "c", "I"));
		assertProposal("getValue", method(classEntry, "a", "()I"));

		assertProposal("scale", field(classEntry, "d", "D"));
		assertProposal("getScale", method(classEntry, "b", "()D"));

		assertProposal("factor", field(classEntry, "e", "Ljava/util/Optional;"));
		assertProposal("getFactor", method(classEntry, "c", "()Ljava/util/Optional;"));

		assertProposal("seed", field(classEntry, "b", "J"));
	}

	@Test
	public void testConstructorParameterNames() {
		var classEntry = new ClassEntry("com/a/a");
		var constructor = method(classEntry, "<init>", "(IDLjava/util/Optional;J)V");

		assertDynamicProposal("value", localVar(constructor, 1));
		assertDynamicProposal("scale", localVar(constructor, 2));
		assertDynamicProposal("factor", localVar(constructor, 4));
		assertDynamicProposal("seed", localVar(constructor, 5));
	}

	@Test
	public void testGetterSetterNames() {
		var classEntry = new ClassEntry("com/a/c");

		var vc = new ValidationContext(null);
		remapper.putMapping(vc, field(classEntry, "a", "I"), new EntryMapping("silliness"));
		remapper.putMapping(vc, field(classEntry, "b", "Ljava/lang/String;"), new EntryMapping("name"));

		MethodEntry method;
		assertDynamicProposal("getSilliness", method(classEntry, "a", "()I"));
		assertDynamicProposal("setSilliness", (method = method(classEntry, "a", "(I)V")));
		assertDynamicProposal("silliness", localVar(method, 1));

		assertDynamicProposal("getName", method(classEntry, "b", "()Ljava/lang/String;"));
		assertDynamicProposal("setName", (method = method(classEntry, "b", "(Ljava/lang/String;)V")));
		assertDynamicProposal("name", localVar(method, 1));
	}


	@Test
	public void testSimpleTypeSingleNames() {
		var classEntry = new ClassEntry("com/a/e");
		var fieldsClassEntry = new ClassEntry(classEntry, "a");

		var owner = new ClassEntry(fieldsClassEntry, "a");
		assertNotProposed(field(owner, "a", "Lcom/a/c/g;"));
		assertNotProposed(field(owner, "b", "Lcom/a/c/g;"));

		owner = new ClassEntry(fieldsClassEntry, "b");
		assertProposal("POS", field(owner, "a", "Lcom/a/c/b;"));
		assertProposal("position", field(owner, "b", "Lcom/a/c/c;"));
		assertProposal("randomPosition", field(owner, "c", "Lcom/a/c/d;"));
		assertProposal("STATIC_STATE_A", field(owner, "d", "Lcom/a/c/e;"));
		assertProposal("STATIC_STATE_B", field(owner, "e", "Lcom/a/c/f;"));
		assertProposal("VALUE_A", field(owner, "f", "Lcom/a/c/g;"));
		assertProposal("VALUE_B", field(owner, "g", "Lcom/a/c/h;"));
		assertProposal("valueC", field(owner, "h", "Lcom/a/c/i;"));

		owner = new ClassEntry(fieldsClassEntry, "c");
		assertProposal("valueD", field(owner, "a", "Lcom/a/c/k;"));
		assertNotProposed(field(owner, "b", "Lcom/a/c/m;"));

		owner = new ClassEntry(fieldsClassEntry, "d");
		assertProposal("CONFIG", field(owner, "a", "Lcom/a/c/a;"));
		assertProposal("STATIC_STATE", field(owner, "b", "Lcom/a/c/e;"));
		assertProposal("value", field(owner, "c", "Lcom/a/c/i;"));

		owner = new ClassEntry(classEntry, "b");
		var parent = method(owner, "a", "(Lcom/a/c/a;)V");
		assertProposal("config", localVar(parent, 0));

		parent = method(owner, "a", "(Lcom/a/c/b;)V");
		assertProposal("pos", localVar(parent, 1));

		parent = method(owner, "a", "(Lcom/a/c/b;Lcom/a/c/c;)V");
		assertProposal("pos", localVar(parent, 1));
		assertProposal("position", localVar(parent, 2));

		parent = method(owner, "a", "(Lcom/a/c/b;Lcom/a/c/d;)V");
		assertProposal("pos", localVar(parent, 1));
		assertProposal("position", localVar(parent, 2));

		parent = method(owner, "a", "(Lcom/a/c/b;Lcom/a/c/c;Lcom/a/c/d;)V");
		assertProposal("pos", localVar(parent, 1));
		assertProposal("position", localVar(parent, 2));
		assertProposal("randomPosition", localVar(parent, 3));

		parent = method(owner, "a", "(Lcom/a/c/e;)V");
		assertProposal("state", localVar(parent, 1));

		parent = method(owner, "a", "(Lcom/a/c/e;Lcom/a/c/f;)V");
		assertProposal("stateA", localVar(parent, 0));
		assertProposal("stateB", localVar(parent, 1));

		parent = method(owner, "a", "(Lcom/a/c/g;Lcom/a/c/h;Lcom/a/c/i;)V");
		assertProposal("valueA", localVar(parent, 0));
		assertProposal("valueB", localVar(parent, 1));
		assertProposal("valueC", localVar(parent, 2));

		parent = method(owner, "a", "(Lcom/a/c/g;Lcom/a/c/g;)V");
		assertNotProposed(localVar(parent, 0));
		assertNotProposed(localVar(parent, 1));

		// Check that both the parent and child class have names
		parent = method(owner, "a", "(Lcom/a/c/j;)V");
		assertProposal("valueD", localVar(parent, 0));

		parent = method(owner, "a", "(Lcom/a/c/k;)V");
		assertProposal("valueD", localVar(parent, 0));

		// Check that just the parent class has a name
		parent = method(owner, "a", "(Lcom/a/c/l;)V");
		assertProposal("valueE", localVar(parent, 0));

		parent = method(owner, "a", "(Lcom/a/c/m;)V");
		assertNotProposed(localVar(parent, 0));

		// Check that indexing order doesn't affect inheritance tree
		owner = new ClassEntry("com/a/c/j");
		parent = method(owner, "a", "(Lcom/a/c/j;)V");
		assertProposal("valueD", localVar(parent, 0));

		parent = method(owner, "a", "(Lcom/a/c/k;)V");
		assertProposal("valueD", localVar(parent, 0));
	}

	@Test
	public void testSimpleTypeNameConflictFix() {
		// tests the conflict fixer via introducing a conflict manually

		var owner = new ClassEntry("com/a/c/a");
		var constructor = method(owner, "<init>", "(ILjava/lang/CharSequence;)V");

		// param 2 is initially 'id'
		assertProposal("id", localVar(constructor, 2));

		// fires dynamic proposal for the constructor parameter, creating a conflict
		// the conflict should then be automatically fixed by moving to the 'identifier' name
		// note we bypass putMapping so that we can create a conflict
		remapper.getMappings().insert(field(owner, "a", "I"), new EntryMapping("id"));
		remapper.insertDynamicallyProposedMappings(field(owner, "a", "I"), EntryMapping.OBFUSCATED, new EntryMapping("id"));

		assertDynamicProposal("id", localVar(constructor, 1));
		assertDynamicProposal("identifier", localVar(constructor, 2));
	}

	@Test
	public void testSimpleTypeNameConflictFixNoFallback() {
		var owner = new ClassEntry("com/a/c/a");
		var constructor = method(owner, "<init>", "(ILjava/lang/StringBuilder;)V");

		// param 2 is initially 'id'
		assertProposal("stringBuilder", localVar(constructor, 2));

		// fires dynamic proposal for the constructor parameter, creating a conflict
		// the conflict should then be automatically fixed via removing the name
		// note we bypass putMapping so that we can create a conflict
		FieldEntry backingField = field(owner, "a", "I");
		EntryMapping mapping = new EntryMapping("stringBuilder");
		remapper.getMappings().insert(backingField, mapping);
		remapper.insertDynamicallyProposedMappings(backingField, EntryMapping.OBFUSCATED, mapping);

		assertDynamicProposal("stringBuilder", localVar(constructor, 1));
		assertNotProposed(localVar(constructor, 2));
	}

	@Test
	public void testDelegateParameterNames() {
		var classEntry = new ClassEntry("com/a/b");

		assertDynamicProposal("seed", localVar(method(classEntry, "a", "(J)I"), 1));
		assertDynamicProposal("seed", localVar(method(classEntry, "a", "(I)V"), 1));
		assertDynamicProposal("seed", localVar(method(classEntry, "b", "(J)V"), 1));

		assertNotProposed(localVar(method(classEntry, "b", "(I)V"), 1));

		// Multiple parameters passed to the same method call within a single method def shouldn't be named
		assertNotProposed(localVar(method(classEntry, "a", "(II)I"), 0));
		assertNotProposed(localVar(method(classEntry, "a", "(II)I"), 1));

		assertNotProposed(localVar(method(classEntry, "a", "(IIII)I"), 0));
		assertNotProposed(localVar(method(classEntry, "a", "(IIII)I"), 1));
		assertNotProposed(localVar(method(classEntry, "a", "(IIII)I"), 2));
		assertNotProposed(localVar(method(classEntry, "a", "(IIII)I"), 3));

		// If multiple parameters would have the same name, don't name any of them
		assertNotProposed(localVar(method(classEntry, "b", "(II)I"), 0));
		assertNotProposed(localVar(method(classEntry, "b", "(II)I"), 1));

		classEntry = new ClassEntry(classEntry, "a");

		assertDynamicProposal("val", localVar(method(classEntry, "<init>", "(I)V"), 1));
		assertDynamicProposal("val", localVar(method(classEntry, "<init>", "(IJ)V"), 1));
		assertDynamicProposal("val", localVar(method(classEntry, "<init>", "(IJI)V"), 1));
		assertDynamicProposal("val", localVar(method(classEntry, "a", "(I)Lcom/a/b$a;"), 0));
		assertDynamicProposal("val", localVar(method(classEntry, "a", "(IJ)Lcom/a/b$a;"), 0));

		assertDynamicProposal("j", localVar(method(classEntry, "<init>", "(IJ)V"), 2));
		assertDynamicProposal("j", localVar(method(classEntry, "<init>", "(IJI)V"), 2));
		assertDynamicProposal("j", localVar(method(classEntry, "a", "(IJ)Lcom/a/b$a;"), 1));

		assertDynamicProposal("index", localVar(method(classEntry, "<init>", "(IJI)V"), 4));

		// Test dynamic renames
		remapper.putMapping(new ValidationContext(null), localVar(method(classEntry, "<init>", "(IJI)V"), 2), new EntryMapping("silly"));
		assertDynamicProposal("silly", localVar(method(classEntry, "<init>", "(IJ)V"), 2));
		assertDynamicProposal("silly", localVar(method(classEntry, "a", "(IJ)Lcom/a/b$a;"), 1));

		remapper.putMapping(new ValidationContext(null), localVar(method(classEntry, "<init>", "(IJI)V"), 2), EntryMapping.OBFUSCATED);
		assertDynamicProposal("j", localVar(method(classEntry, "<init>", "(IJ)V"), 2));
		assertDynamicProposal("j", localVar(method(classEntry, "<init>", "(IJI)V"), 2));
		assertDynamicProposal("j", localVar(method(classEntry, "a", "(IJ)Lcom/a/b$a;"), 1));

		// Ensure names are also loaded from external classes
		classEntry = new ClassEntry("com/a/c");

		assertDynamicProposal("bound", localVar(method(classEntry, "b", "(I)V"), 1));

		// Names shouldn't be proposed for synthetic parameters (like Enum constructor name and ordinal)
		classEntry = new ClassEntry("com/a/b/e");
		var method = method(classEntry, "<init>", "(Ljava/lang/String;IZ)V");

		assertNotProposed(localVar(method, 1));
		assertNotProposed(localVar(method, 2));
	}

	@Test
	public void testEntityRendering() {
		var classEntry = new ClassEntry("com/a/a/a");

		assertProposal("HEAD", field(classEntry, "a", "Ljava/lang/String;"));
		assertProposal("EARS", field(classEntry, "b", "Ljava/lang/String;"));
		assertNotProposed(field(classEntry, "c", "Ljava/lang/String;"));
		assertNotProposed(field(classEntry, "d", "Ljava/lang/String;"));
	}

	private static FieldEntry field(ClassEntry parent, String name, String desc) {
		return new FieldEntry(parent, name, new TypeDescriptor(desc));
	}

	private static MethodEntry method(ClassEntry parent, String name, String desc) {
		return new MethodEntry(parent, name, new MethodDescriptor(desc));
	}

	private static LocalVariableEntry localVar(MethodEntry parent, int index) {
		return new LocalVariableEntry(parent, index);
	}
}
