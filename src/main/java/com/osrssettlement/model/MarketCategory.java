package com.osrssettlement.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketCategory
{
	RAW("Raw materials", ResourceCategory.RAW),
	PROCESSED("Processed", ResourceCategory.REFINED),
	SPECIAL("Special goods", ResourceCategory.SPECIAL);

	private final String displayName;
	private final ResourceCategory resourceCategory;

	public List<Resource> getResources()
	{
		List<Resource> resources = new ArrayList<>();
		for (Resource resource : Resource.values())
		{
			if (resource.getCategory() == resourceCategory)
			{
				resources.add(resource);
			}
		}
		return Collections.unmodifiableList(resources);
	}

	/**
	 * @return the category trading this resource, or null if the Keldagrim Consortium does not trade it
	 */
	public static MarketCategory of(Resource resource)
	{
		for (MarketCategory category : values())
		{
			if (category.resourceCategory == resource.getCategory())
			{
				return category;
			}
		}
		return null;
	}
}