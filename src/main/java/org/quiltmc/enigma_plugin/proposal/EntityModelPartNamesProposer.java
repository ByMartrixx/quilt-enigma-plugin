package org.quiltmc.enigma_plugin.proposal;

import java.util.Map;

import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.quiltmc.enigma_plugin.index.JarIndexer;
import org.quiltmc.enigma_plugin.index.entity_rendering.EntityModelPartNamesIndex;

public class EntityModelPartNamesProposer extends NameProposer {
	public static final String ID = "simple_type_field_names";
	private final EntityModelPartNamesIndex index;

	public EntityModelPartNamesProposer(JarIndexer index) {
		super(ID);
		this.index = index.getIndex(EntityModelPartNamesIndex.class);
	}

	@Override
	public void insertProposedNames(JarIndex index, Map<Entry<?>, EntryMapping> mappings) {
		for (FieldEntry field : this.index.getNames().keySet()) {
			String name = this.index.getNames().get(field);

			this.insertProposal(mappings, field, toFieldName(name));
		}
	}

	private static String toFieldName(String name) {
		return name.toUpperCase().replace("-", "_").replace(".", "_");
	}
}
