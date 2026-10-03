package com.osrssettlement.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.osrssettlement.engine.SettlementEngine;
import com.osrssettlement.model.Boost;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Test;

public class SettlementStoreTest
{
	private final Gson gson = new Gson();
	private final MemoryConfiguration configuration = new MemoryConfiguration();
	private final SettlementStore store = new SettlementStore(configuration, gson);

	private static final class MemoryConfiguration implements SettlementStore.Configuration
	{
		private final Map<String, String> values = new HashMap<>();
		private int writes;
		private String failKey;
		private boolean failAfterWrite;
		private boolean mismatch;
		private boolean readFailure;
		private boolean failReadback;
		private Set<Integer> failWrites = Set.of();

		@Override
		public String get(String profile, String key)
		{
			if (readFailure)
			{
				throw new IllegalStateException("read failed");
			}
			return values.get(profile + "/" + key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			writes++;
			if (failWrites.contains(writes))
			{
				throw new IllegalStateException("write failed");
			}
			if (key.equals(failKey) && !failAfterWrite)
			{
				throw new IllegalStateException("write failed");
			}
			values.put(profile + "/" + key, mismatch ? "unexpected" : value);
			if (failReadback)
			{
				readFailure = true;
			}
			if (key.equals(failKey))
			{
				throw new IllegalStateException("write applied before failure");
			}
		}
	}

	private SettlementState healthyState()
	{
		return new SettlementEngine(SettlementEngine.newSettlement(), new Random(5)).getState();
	}

	private SettlementStore.Session existing(SettlementState state)
	{
		configuration.values.put("account/state", store.snapshot(state));
		return store.load("account");
	}

	private static void rejected(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected a protected operation to fail");
		}
		catch (RuntimeException expected)
		{
			assertTrue(expected.getMessage() != null);
		}
	}

	@Test
	public void roundTripKeepsProgressWithoutLoadWrites()
	{
		SettlementState state = healthyState();
		SettlementEngine engine = new SettlementEngine(state, new Random(5));
		engine.onXp(Skill.WOODCUTTING, 500);
		engine.onXp(Skill.SMITHING, 500);
		state.addStock(Resource.RARE_MONSTER_PARTS, 7);
		state.addStock(Resource.EPIC_MONSTER_PARTS, 3);
		state.addStock(Resource.ARTIFACT, 11);
		state.getBlueprints().add(Building.KELDAGRIM_CONSORTIUM);
		state.setLevel(Building.TOWN_HALL, 2);
		state.setLevel(Building.KELDAGRIM_CONSORTIUM, 2);
		state.setLabourPaused(true);
		state.getBoosts().add(new Boost(Resource.FISH, 2, 300));
		state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives().put("miningXp", 321.5);

		SettlementStore.Session loaded = existing(state);
		assertEquals(SettlementStore.Status.READY, loaded.getStatus());
		assertEquals(state, loaded.getState());
		assertEquals(0, configuration.writes);
	}

	@Test
	public void rejectedLoadsAndSavesLeavePrimaryAndBackupsUnchanged()
	{
		String[] records = {"", " ", "null", "[]", "broken", "{}", "{\"schemaVersion\":null}",
			"{\"schemaVersion\":6.5}", "{\"schemaVersion\":\"6\"}", "{\"schemaVersion\":2}",
			"{\"schemaVersion\":99,\"futureProgress\":123}",
			"{\"schemaVersion\":6,\"stock\":{\"UNOBTAINIUM\":3}}",
			"{\"schemaVersion\":6,\"stock\":{\"LOGS\":-1}}",
			"{\"schemaVersion\":6,\"stock\":{\"LOGS\":1e400}}",
			"{\"schemaVersion\":6,\"labour\":{\"SMITHING\":-1}}",
			"{\"schemaVersion\":6,\"levels\":{\"TOWN_HALL\":11}}",
			"{\"schemaVersion\":6,\"levels\":{\"TOWN_HALL\":1.5}}",
			"{\"schemaVersion\":6,\"blueprints\":[\"MOON_BASE\"]}",
			"{\"schemaVersion\":6,\"bounties\":[null]}",
			"{\"schemaVersion\":6,\"events\":[{\"event\":\"ALIEN_INVASION\"}]}",
			"{\"schemaVersion\":6,\"stock\":null}",
			"{\"schemaVersion\":6,\"extraProgress\":3}",
			"{\"schemaVersion\":6,\"schemaVersion\":6}",
			"{\"schemaVersion\":6,\"stock\":{\"LOGS\":5,\"LOGS\":10}}",
			"{\"schemaVersion\":6} trailing data"};
		for (String record : records)
		{
			configuration.values.put("account/state", record);
			configuration.values.put("account/state-backup", "protected original");
			configuration.values.put("account/state-before-reset", "protected reset");
			SettlementStore.Session session = store.load("account");
			assertFalse("Writable record: " + record, session.isWritable());
			rejected(() -> store.save(session, store.snapshot(healthyState())));
			assertEquals(record, configuration.get("account", "state"));
			assertEquals("protected original", configuration.get("account", "state-backup"));
			assertEquals("protected reset", configuration.get("account", "state-before-reset"));
		}
		assertEquals(0, configuration.writes);
	}

	@Test
	public void newProfileRequiresNoPrimaryOrRecoveryRecord()
	{
		assertEquals(SettlementStore.Status.NEW, store.load("account").getStatus());
		configuration.values.put("account/state-backup", "recovery");
		assertEquals(SettlementStore.Status.NEW, store.load("account").getStatus());
		configuration.values.put("account/state-before-reset", "recovery");
		assertEquals(SettlementStore.Status.INVALID_DATA, store.load("account").getStatus());
		assertFalse(store.load(null).isWritable());
	}

	@Test
	public void newSaveRechecksRecoveryData()
	{
		SettlementStore.Session session = store.load("account");
		configuration.values.put("account/state-before-reset", "appeared later");
		rejected(() -> store.save(session, store.snapshot(healthyState())));
		assertEquals(0, configuration.writes);
	}

	@Test
	public void supportedSchemaCanDefaultMissingOptionalCollections()
	{
		configuration.values.put("account/state", "{\"schemaVersion\":6,\"levels\":{\"TOWN_HALL\":1}}");
		assertEquals(SettlementStore.Status.READY, store.load("account").getStatus());
		assertEquals(0, configuration.writes);
	}

	@Test
	public void unsupportedSchemaHasDistinctStatus()
	{
		configuration.values.put("account/state", "{\"schemaVersion\":99}");
		assertEquals(SettlementStore.Status.UNSUPPORTED_VERSION, store.load("account").getStatus());
	}

	@Test
	public void readFailureDoesNotCreateNewProfile()
	{
		configuration.readFailure = true;
		assertEquals(SettlementStore.Status.STORAGE_ERROR, store.load("account").getStatus());
		assertEquals(0, configuration.writes);
	}

	@Test
	public void externalChangeBlocksSaveAndReset()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		configuration.values.put("account/state", "externally changed");
		rejected(() -> store.save(session, store.snapshot(state)));
		rejected(() -> store.reset(session, store.snapshot(state), store.snapshot(healthyState())));
		assertEquals("externally changed", configuration.get("account", "state"));
		assertEquals(0, configuration.writes);
	}

	@Test
	public void failedSaveCanRetryWithoutLosingOriginal()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		String original = session.getExpectedJson();
		state.addStock(Resource.LOGS, 20);
		String updated = store.snapshot(state);
		configuration.failKey = "state";
		rejected(() -> store.save(session, updated));
		assertEquals(original, configuration.get("account", "state"));
		configuration.failKey = null;
		store.save(session, updated);
		assertEquals(updated, configuration.get("account", "state"));
	}

	@Test
	public void failureAfterWriteReconcilesExpectedValueForRetry()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		state.addStock(Resource.LOGS, 20);
		String updated = store.snapshot(state);
		configuration.failKey = "state";
		configuration.failAfterWrite = true;
		rejected(() -> store.save(session, updated));
		assertEquals(updated, session.getExpectedJson());
		configuration.failKey = null;
		store.save(session, updated);
	}

	@Test
	public void appliedWriteWithFailedReadbackCanBeReconciled()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		state.addStock(Resource.LOGS, 20);
		String updated = store.snapshot(state);
		configuration.failReadback = true;
		rejected(() -> store.save(session, updated));
		configuration.failReadback = false;
		configuration.readFailure = false;
		store.save(session, updated);
		assertEquals(updated, session.getExpectedJson());
	}

	@Test
	public void snapshotSerializationFailureDoesNotWrite()
	{
		SettlementState state = healthyState();
		existing(state);
		String original = configuration.get("account", "state");
		state.getStock().put(Resource.LOGS, Double.NaN);
		rejected(() -> store.snapshot(state));
		assertEquals(original, configuration.get("account", "state"));
		assertEquals(0, configuration.writes);
	}

	@Test
	public void largeStockAndWonderLevelsArePreserved()
	{
		SettlementState state = healthyState();
		state.getStock().put(Resource.LOGS, 1e50);
		state.setLevel(Building.WONDER, 100);
		SettlementStore.Session session = existing(state);
		assertEquals(SettlementStore.Status.READY, session.getStatus());
		assertEquals(state, session.getState());
		assertEquals(0, configuration.writes);
	}

	@Test
	public void unexpectedReadbackBlocksFurtherWrites()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		configuration.mismatch = true;
		rejected(() -> store.save(session, store.snapshot(state)));
		assertFalse(session.isWritable());
	}

	@Test
	public void explicitProfileKeysKeepAccountsSeparate()
	{
		SettlementStore.Session first = store.load("first");
		SettlementStore.Session second = store.load("second");
		String firstJson = store.snapshot(healthyState());
		SettlementState other = healthyState();
		other.addStock(Resource.LOGS, 500);
		String secondJson = store.snapshot(other);
		store.save(first, firstJson);
		store.save(second, secondJson);
		assertEquals(firstJson, configuration.get("first", "state"));
		assertEquals(secondJson, configuration.get("second", "state"));
	}

	@Test
	public void resetProtectsLiveUnsavedProgressAndExistingBackup()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		state.addStock(Resource.LOGS, 400);
		String liveProgress = store.snapshot(state);
		String replacement = store.snapshot(healthyState());
		configuration.values.put("account/state-backup", "legacy recovery");
		store.reset(session, liveProgress, replacement);
		assertEquals(liveProgress, configuration.get("account", "state-before-reset"));
		assertEquals("legacy recovery", configuration.get("account", "state-backup"));
		assertEquals(replacement, configuration.get("account", "state"));
		store.reset(session, replacement, replacement);
		assertEquals(replacement, configuration.get("account", "state-before-reset"));
	}

	@Test
	public void restoreSwapsLiveProgressAndCanSwapBack()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		String backup = store.snapshot(state);
		configuration.values.put("account/state-before-reset", backup);
		state.addStock(Resource.LOGS, 400);
		String live = store.snapshot(state);
		assertEquals(backup, store.restorableSnapshot(session));
		store.restore(session, live, backup);
		assertEquals(backup, configuration.get("account", "state"));
		assertEquals(live, configuration.get("account", "state-before-reset"));
		store.restore(session, backup, live);
		assertEquals(live, configuration.get("account", "state"));
		assertEquals(backup, configuration.get("account", "state-before-reset"));
	}

	@Test
	public void absentInvalidOrChangedSnapshotCannotRestore()
	{
		SettlementStore.Session session = existing(healthyState());
		String live = session.getExpectedJson();
		assertEquals(null, store.restorableSnapshot(session));
		configuration.values.put("account/state-before-reset", "invalid");
		assertEquals(null, store.restorableSnapshot(session));
		rejected(() -> store.restore(session, live, "invalid"));
		rejected(() -> store.restore(session, live, live));
		assertEquals(0, configuration.writes);
	}

	@Test
	public void restoreFailureRollsBackBothRecords()
	{
		SettlementStore.Session session = existing(healthyState());
		String original = session.getExpectedJson();
		SettlementState other = healthyState();
		other.addStock(Resource.LOGS, 500);
		String backup = store.snapshot(other);
		configuration.values.put("account/state-before-reset", backup);
		for (String key : new String[]{"state", "state-before-reset"})
		{
			configuration.failKey = key;
			rejected(() -> store.restore(session, original, backup));
			assertEquals(original, configuration.get("account", "state"));
			assertEquals(backup, configuration.get("account", "state-before-reset"));
			assertTrue(session.isWritable());
		}
	}

	@Test
	public void restoreWithFailedReadbackRetainsRecoveryUntilRetry()
	{
		SettlementStore.Session session = existing(healthyState());
		String original = session.getExpectedJson();
		SettlementState other = healthyState();
		other.addStock(Resource.LOGS, 500);
		String backup = store.snapshot(other);
		configuration.values.put("account/state-before-reset", backup);
		configuration.failReadback = true;
		rejected(() -> store.restore(session, original, backup));
		assertFalse(session.isWritable());
		configuration.failReadback = false;
		configuration.readFailure = false;
		store.recoverRestore(session);
		assertTrue(session.isWritable());
		assertEquals(original, configuration.get("account", "state"));
		assertEquals(backup, configuration.get("account", "state-before-reset"));
	}

	@Test
	public void restoreAcceptsConfirmedWritesThatThrowAfterApplying()
	{
		SettlementStore.Session session = existing(healthyState());
		String original = session.getExpectedJson();
		SettlementState other = healthyState();
		other.addStock(Resource.LOGS, 500);
		String backup = store.snapshot(other);
		configuration.values.put("account/state-before-reset", backup);
		configuration.failKey = "state";
		configuration.failAfterWrite = true;
		store.restore(session, original, backup);
		assertEquals(backup, session.getExpectedJson());
		assertEquals(original, configuration.get("account", "state-before-reset"));
	}

	@Test
	public void failedRollbackBlocksSavingUntilRecoveryCanFinish()
	{
		SettlementStore.Session session = existing(healthyState());
		String original = session.getExpectedJson();
		SettlementState other = healthyState();
		other.addStock(Resource.LOGS, 500);
		String backup = store.snapshot(other);
		configuration.values.put("account/state-before-reset", backup);
		configuration.failWrites = Set.of(2, 4);
		rejected(() -> store.restore(session, original, backup));
		assertFalse(session.isWritable());
		rejected(() -> store.save(session, original));
		store.recoverRestore(session);
		assertTrue(session.isWritable());
		assertEquals(original, configuration.get("account", "state"));
		assertEquals(backup, configuration.get("account", "state-before-reset"));
		store.save(session, original);
	}

	@Test
	public void restoreRejectsExternalPrimaryChangesAndOtherProfiles()
	{
		SettlementStore.Session session = existing(healthyState());
		String original = session.getExpectedJson();
		configuration.values.put("other/state-before-reset", original);
		assertEquals(null, store.restorableSnapshot(session));
		configuration.values.put("account/state-before-reset", original);
		configuration.values.put("account/state", "external change");
		rejected(() -> store.restore(session, original, original));
		assertEquals("external change", configuration.get("account", "state"));
		assertEquals(0, configuration.writes);
	}

	@Test
	public void protectedSessionCannotRestoreEvenWithValidSnapshot()
	{
		String backup = store.snapshot(healthyState());
		configuration.values.put("account/state", "invalid");
		configuration.values.put("account/state-before-reset", backup);
		SettlementStore.Session session = store.load("account");
		assertEquals(null, store.restorableSnapshot(session));
		rejected(() -> store.restore(session, backup, backup));
		assertEquals(0, configuration.writes);
	}

	@Test
	public void resetReplacesAnInvalidPreviousRestorePoint()
	{
		SettlementStore.Session session = existing(healthyState());
		String original = session.getExpectedJson();
		configuration.values.put("account/state-before-reset", "invalid");
		store.reset(session, original, original);
		assertEquals(original, store.restorableSnapshot(session));
	}

	@Test
	public void backupFailureCancelsReset()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		String original = session.getExpectedJson();
		configuration.failKey = "state-before-reset";
		rejected(() -> store.reset(session, original, store.snapshot(healthyState())));
		assertEquals(original, configuration.get("account", "state"));
	}

	@Test
	public void resetCommitFailureKeepsOriginalAndRecoverySnapshot()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		String original = session.getExpectedJson();
		state.addStock(Resource.LOGS, 100);
		String live = store.snapshot(state);
		configuration.failKey = "state";
		rejected(() -> store.reset(session, live, store.snapshot(healthyState())));
		assertEquals(original, configuration.get("account", "state"));
		assertEquals(live, configuration.get("account", "state-before-reset"));
	}

	@Test
	public void resetAppliedBeforeFailureCanRetryLiveProgressWithoutLosingBackup()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		state.addStock(Resource.LOGS, 100);
		String live = store.snapshot(state);
		String replacement = store.snapshot(healthyState());
		configuration.failKey = "state";
		configuration.failAfterWrite = true;
		rejected(() -> store.reset(session, live, replacement));
		assertEquals(replacement, configuration.get("account", "state"));
		assertEquals(live, configuration.get("account", "state-before-reset"));
		configuration.failKey = null;
		store.save(session, live);
		assertEquals(live, configuration.get("account", "state"));
		assertEquals(live, configuration.get("account", "state-before-reset"));
	}

	@Test
	public void missingBountyExpiryIsBlockedInsteadOfMigrated()
	{
		JsonObject record = gson.toJsonTree(healthyState()).getAsJsonObject();
		record.getAsJsonArray("bounties").get(0).getAsJsonObject().remove("expiresAtPlaytime");
		String original = gson.toJson(record);
		configuration.values.put("account/state", original);
		assertEquals(SettlementStore.Status.INVALID_DATA, store.load("account").getStatus());
		assertEquals(original, configuration.get("account", "state"));
		assertEquals(0, configuration.writes);
	}

	@Test
	public void invalidCandidateCannotOverwriteHealthySave()
	{
		SettlementState state = healthyState();
		SettlementStore.Session session = existing(state);
		JsonObject candidate = gson.toJsonTree(state).getAsJsonObject();
		candidate.getAsJsonArray("bounties").get(0).getAsJsonObject().addProperty("target", -1);
		rejected(() -> store.save(session, gson.toJson(candidate)));
		assertEquals(0, configuration.writes);
	}
}