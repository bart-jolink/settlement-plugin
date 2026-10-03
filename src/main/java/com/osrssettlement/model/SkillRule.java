package com.osrssettlement.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.Skill;

/**
 * How XP in one skill feeds the settlement. Gathering skills produce their outputs directly;
 * processing skills convert their inputs (in priority order) into a single output.
 */
@Getter
public final class SkillRule
{
	private final Skill skill;
	private final SkillKind kind;
	private final Building building;
	private final Map<Resource, Double> outputs;
	private final List<Resource> inputs;

	private SkillRule(Skill skill, SkillKind kind, Building building, Map<Resource, Double> outputs, List<Resource> inputs)
	{
		this.skill = skill;
		this.kind = kind;
		this.building = building;
		this.outputs = Collections.unmodifiableMap(new EnumMap<>(outputs));
		this.inputs = List.copyOf(inputs);
	}

	static SkillRule produce(Skill skill, SkillKind kind, Building building, Map<Resource, Double> outputs)
	{
		return new SkillRule(skill, kind, building, outputs, List.of());
	}

	static SkillRule process(Skill skill, Building building, Resource output, Resource... inputs)
	{
		return new SkillRule(skill, SkillKind.PROCESSING, building, Map.of(output, 1.0), List.of(inputs));
	}

	/**
	 * The single output of a processing skill.
	 */
	public Resource getOutput()
	{
		return outputs.keySet().iterator().next();
	}

	public boolean isProcessing()
	{
		return kind == SkillKind.PROCESSING;
	}
}
