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

package org.quiltmc.enigma_plugin.proposal;

import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.service.EnigmaServiceContext;
import org.quiltmc.enigma.api.service.NameProposalService;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma_plugin.Arguments;
import org.quiltmc.enigma_plugin.QuiltEnigmaPlugin;
import org.quiltmc.enigma_plugin.index.JarIndexer;
import org.quiltmc.enigma_plugin.index.entity_rendering.EntityModelPartNamesIndex;
import org.quiltmc.enigma_plugin.index.simple_type_single.SimpleTypeSingleIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class NameProposerService implements NameProposalService {
	private final List<NameProposer> nameProposers = new ArrayList<>();

	public NameProposerService(JarIndexer indexer, EnigmaServiceContext<NameProposalService> context) {
		this.addIfEnabled(context, indexer, Arguments.DISABLE_RECORDS, RecordComponentNameProposer::new);
		this.addIfEnabled(context, indexer, Arguments.DISABLE_CONSTANT_FIELDS, ConstantFieldNameProposer::new);
		this.addIfEnabled(context, Arguments.DISABLE_EQUALS, EqualsNameProposer::new);
		this.addIfEnabled(context, indexer, Arguments.DISABLE_LOGGER, LoggerNameProposer::new);
		this.addIfEnabled(context, indexer, Arguments.DISABLE_CODECS, CodecNameProposer::new);
		this.addIfNotDisabled(context, Arguments.DISABLE_MAP_NON_HASHED, MojangNameProposer::new);

		if (indexer.getIndex(SimpleTypeSingleIndex.class).isEnabled()) {
			this.nameProposers.add(new SimpleTypeFieldNameProposer(indexer));
		}

		if (indexer.getIndex(EntityModelPartNamesIndex.class).isEnabled()) {
			this.nameProposers.add(new EntityModelPartNamesProposer(indexer));
		}

		this.addIfEnabled(context, indexer, Arguments.DISABLE_CONSTRUCTOR_PARAMS, ConstructorParamsNameProposer::new);
		this.addIfEnabled(context, indexer, Arguments.DISABLE_GETTER_SETTER, GetterSetterNameProposer::new);
		this.addIfEnabled(context, indexer, Arguments.DISABLE_DELEGATE_PARAMS, DelegateParametersNameProposer::new);

		// conflict fixer must be last in order to get context from other dynamic proposers
		this.addIfEnabled(context, indexer, Arguments.DISABLE_CONFLICT_FIXER, ConflictFixProposer::new);
	}

	private void addIfEnabled(EnigmaServiceContext<NameProposalService> context, String name, Supplier<NameProposer> factory) {
		this.addIfEnabled(context, null, name, indexer -> factory.get());
	}

	private void addIfEnabled(EnigmaServiceContext<NameProposalService> context, JarIndexer indexer, String name, Function<JarIndexer, NameProposer> factory) {
		if (!Arguments.getBoolean(context, name)) {
			this.nameProposers.add(factory.apply(indexer));
		}
	}

	private void addIfNotDisabled(EnigmaServiceContext<NameProposalService> context, String name, Supplier<NameProposer> factory) {
		this.addIfNotDisabled(context, null, name, indexer -> factory.get());
	}

	private void addIfNotDisabled(EnigmaServiceContext<NameProposalService> context, JarIndexer indexer, String name, Function<JarIndexer, NameProposer> factory) {
		if (!Arguments.getBoolean(context, name, true)) {
			this.nameProposers.add(factory.apply(indexer));
		}
	}

	@Override
	public Map<Entry<?>, EntryMapping> getProposedNames(JarIndex index) {
		HashMap<Entry<?>, EntryMapping> proposedNames = new HashMap<>();

		for (NameProposer proposer : this.nameProposers) {
			proposer.insertProposedNames(index, proposedNames);
		}

		return proposedNames;
	}

	@Override
	public Map<Entry<?>, EntryMapping> getDynamicProposedNames(EntryRemapper remapper, Entry<?> obfEntry, EntryMapping oldMapping, EntryMapping newMapping) {
		HashMap<Entry<?>, EntryMapping> proposedNames = new HashMap<>();

		for (NameProposer proposer : this.nameProposers) {
			proposer.proposeDynamicNames(remapper, obfEntry, oldMapping, newMapping, proposedNames);
		}

		return proposedNames;
	}

	@Override
	public String getId() {
		return QuiltEnigmaPlugin.NAME_PROPOSAL_SERVICE_ID;
	}
}
