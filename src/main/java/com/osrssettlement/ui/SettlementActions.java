package com.osrssettlement.ui;

import com.osrssettlement.model.Building;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.Resource;

/**
 * Player actions from the sidebar. Implementations must hop to the client thread themselves.
 */
public interface SettlementActions
{
	void upgrade(Building building);

	void claimBounty(int bountyId);

	void claimExpedition(ExpeditionId expeditionId);

	void tradeKeldagrimConsortium(Resource input, Resource output);

	void setLabourPaused(boolean paused);

	void reset();

	default Runnable prepareReset()
	{
		return this::reset;
	}

	Runnable prepareRestore();

	void retry();

	/**
	 * Requests a fresh snapshot, e.g. when the panel is opened.
	 */
	void refresh();
}
