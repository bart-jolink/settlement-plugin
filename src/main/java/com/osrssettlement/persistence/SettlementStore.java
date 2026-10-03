package com.osrssettlement.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.osrssettlement.engine.Balance;
import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.BountyType;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.RewardType;
import com.osrssettlement.model.SettlementState;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
public class SettlementStore
{
	public static final String CONFIG_GROUP = "osrs-settlement";
	static final String STATE_KEY = "state";
	static final String RESET_BACKUP_KEY = "state-before-reset";

	public enum Status
	{
		NEW, READY, UNSUPPORTED_VERSION, INVALID_DATA, STORAGE_ERROR
	}

	interface Configuration
	{
		String get(String profile, String key);
		void set(String profile, String key, String value);
	}

	@Getter
	public static final class Session
	{
		private final String profileKey;
		private final Status status;
		private final SettlementState state;
		private final String message;
		private String expectedJson;
		private String attemptedJson;
		private boolean writable;
		private String recoveryPrimary;
		private String recoveryBackup;
		private String recoveryLive;
		private String checkedBackup;
		private boolean checkedBackupValid;

		private Session(String profileKey, Status status, SettlementState state, String message, String json)
		{
			this.profileKey = profileKey;
			this.status = status;
			this.state = state;
			this.message = message;
			this.expectedJson = json;
			this.writable = status == Status.NEW || status == Status.READY;
		}
	}

	private final Configuration configuration;
	private final Gson gson;

	@Inject
	SettlementStore(ConfigManager configManager, Gson gson)
	{
		this(new Configuration()
		{
			@Override
			public String get(String profile, String key)
			{
				return configManager.getConfiguration(CONFIG_GROUP, profile, key);
			}

			@Override
			public void set(String profile, String key, String value)
			{
				configManager.setConfiguration(CONFIG_GROUP, profile, key, value);
			}
		}, gson);
	}

	SettlementStore(Configuration configuration, Gson gson)
	{
		this.configuration = configuration;
		this.gson = gson;
	}

	public synchronized Session load(String profileKey)
	{
		if (profileKey == null)
		{
			return new Session(null, Status.STORAGE_ERROR, null, "No account loaded.", null);
		}
		String json;
		try
		{
			json = configuration.get(profileKey, STATE_KEY);
			if (json == null)
			{
				return hasBackup(profileKey)
					? new Session(profileKey, Status.INVALID_DATA, null,
						"Saved progress is missing, but protected recovery data exists.", null)
					: new Session(profileKey, Status.NEW, null, null, null);
			}
		}
		catch (RuntimeException exception)
		{
			log.warn("Unable to read settlement configuration", exception);
			return new Session(profileKey, Status.STORAGE_ERROR, null,
				"Unable to read saved progress. No replacement settlement was created.", null);
		}
		try
		{
			return new Session(profileKey, Status.READY, fromJson(gson, json), null, json);
		}
		catch (UnsupportedVersionException exception)
		{
			return new Session(profileKey, Status.UNSUPPORTED_VERSION, null,
				"This settlement uses an unsupported data version. Its saved progress has been preserved.", json);
		}
		catch (RuntimeException exception)
		{
			log.warn("Unable to validate saved settlement", exception);
			return new Session(profileKey, Status.INVALID_DATA, null,
				"Saved progress could not be safely loaded. It has not been replaced or repaired.", json);
		}
	}

	public String snapshot(SettlementState state)
	{
		String json = gson.toJson(state);
		fromJson(gson, json);
		return json;
	}

	public synchronized void save(Session session, String json)
	{
		requireWritable(session);
		fromJson(gson, json);
		String current = configuration.get(session.profileKey, STATE_KEY);
		if (session.attemptedJson != null && Objects.equals(current, session.attemptedJson))
		{
			session.expectedJson = current;
		}
		if (!Objects.equals(current, session.expectedJson)
			|| current == null && hasBackup(session.profileKey))
		{
			session.writable = false;
			throw new IllegalStateException("Saved progress changed outside this session. No overwrite was attempted.");
		}
		try
		{
			session.attemptedJson = json;
			configuration.set(session.profileKey, STATE_KEY, json);
		}
		catch (RuntimeException exception)
		{
			try
			{
				if (Objects.equals(configuration.get(session.profileKey, STATE_KEY), json))
				{
					session.expectedJson = json;
				}
			}
			catch (RuntimeException readFailure)
			{
				exception.addSuppressed(readFailure);
			}
			throw exception;
		}
		if (!Objects.equals(configuration.get(session.profileKey, STATE_KEY), json))
		{
			session.writable = false;
			throw new IllegalStateException("Saved progress could not be confirmed. Further writes are blocked.");
		}
		session.expectedJson = json;
		session.attemptedJson = null;
	}

	public synchronized void reset(Session session, String previous, String replacement)
	{
		requireWritable(session);
		fromJson(gson, previous);
		fromJson(gson, replacement);
		if (session.expectedJson == null
			|| !Objects.equals(configuration.get(session.profileKey, STATE_KEY), session.expectedJson))
		{
			throw new IllegalStateException("Saved progress changed or has not been saved. Reset was cancelled.");
		}
		configuration.set(session.profileKey, RESET_BACKUP_KEY, previous);
		if (!previous.equals(configuration.get(session.profileKey, RESET_BACKUP_KEY)))
		{
			throw new IllegalStateException("The pre-reset backup could not be confirmed. Reset was cancelled.");
		}
		save(session, replacement);
	}

	public synchronized String restorableSnapshot(Session session)
	{
		if (session == null || !session.writable || session.expectedJson == null)
		{
			return null;
		}
		try
		{
			String json = configuration.get(session.profileKey, RESET_BACKUP_KEY);
			if (json == null)
			{
				return null;
			}
			if (!json.equals(session.checkedBackup))
			{
				session.checkedBackup = json;
				session.checkedBackupValid = false;
				fromJson(gson, json);
				session.checkedBackupValid = true;
			}
			return session.checkedBackupValid ? json : null;
		}
		catch (RuntimeException exception)
		{
			log.debug("Pre-reset snapshot is unavailable", exception);
			return null;
		}
	}

	public SettlementState restoreState(String json)
	{
		return fromJson(gson, json);
	}

	public synchronized void restore(Session session, String live, String backup)
	{
		requireWritable(session);
		fromJson(gson, live);
		fromJson(gson, backup);
		if (session.expectedJson == null
			|| !Objects.equals(configuration.get(session.profileKey, STATE_KEY), session.expectedJson)
			|| !Objects.equals(configuration.get(session.profileKey, RESET_BACKUP_KEY), backup))
		{
			throw new IllegalStateException("Saved progress changed. Restore was cancelled.");
		}
		session.recoveryPrimary = session.expectedJson;
		session.recoveryBackup = backup;
		session.recoveryLive = live;
		session.writable = false;
		try
		{
			writeVerified(session.profileKey, STATE_KEY, backup);
			writeVerified(session.profileKey, RESET_BACKUP_KEY, live);
			session.expectedJson = backup;
			session.attemptedJson = null;
			session.recoveryPrimary = null;
			session.recoveryBackup = null;
			session.recoveryLive = null;
			session.writable = true;
		}
		catch (RuntimeException exception)
		{
			try
			{
				recoverRestore(session);
			}
			catch (RuntimeException recoveryFailure)
			{
				exception.addSuppressed(recoveryFailure);
			}
			throw exception;
		}
	}

	public synchronized void recoverRestore(Session session)
	{
		if (session == null || session.recoveryPrimary == null)
		{
			return;
		}
		String primary = configuration.get(session.profileKey, STATE_KEY);
		String backup = configuration.get(session.profileKey, RESET_BACKUP_KEY);
		if (!(Objects.equals(primary, session.recoveryPrimary) || Objects.equals(primary, session.recoveryBackup))
			|| !(Objects.equals(backup, session.recoveryBackup) || Objects.equals(backup, session.recoveryLive)))
		{
			throw new IllegalStateException("Saved progress changed during restore recovery. No overwrite was attempted.");
		}
		writeVerified(session.profileKey, RESET_BACKUP_KEY, session.recoveryBackup);
		writeVerified(session.profileKey, STATE_KEY, session.recoveryPrimary);
		session.expectedJson = session.recoveryPrimary;
		session.attemptedJson = null;
		session.recoveryPrimary = null;
		session.recoveryBackup = null;
		session.recoveryLive = null;
		session.writable = true;
	}

	private void writeVerified(String profile, String key, String json)
	{
		try
		{
			configuration.set(profile, key, json);
		}
		catch (RuntimeException exception)
		{
			if (!Objects.equals(configuration.get(profile, key), json))
			{
				throw exception;
			}
		}
		if (!Objects.equals(configuration.get(profile, key), json))
		{
			throw new IllegalStateException("Settlement write could not be confirmed.");
		}
	}

	private boolean hasBackup(String profile)
	{
		return configuration.get(profile, RESET_BACKUP_KEY) != null;
	}

	private static void requireWritable(Session session)
	{
		if (session == null || session.profileKey == null || !session.writable)
		{
			throw new IllegalStateException("This settlement is protected from writes.");
		}
	}

	static SettlementState fromJson(Gson gson, String json)
	{
		JsonElement root;
		try (JsonReader reader = new JsonReader(new StringReader(json)))
		{
			root = readJson(reader, 0);
			valid(reader.peek() == JsonToken.END_DOCUMENT);
		}
		catch (IOException exception)
		{
			throw new JsonParseException("Invalid settlement JSON", exception);
		}
		if (!root.isJsonObject())
		{
			throw new JsonParseException("Expected a settlement object");
		}
		JsonElement version = root.getAsJsonObject().get("schemaVersion");
		if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber())
		{
			throw new JsonParseException("Missing schema version");
		}
		if (version.getAsBigDecimal().intValueExact() != SettlementState.SCHEMA_VERSION)
		{
			throw new UnsupportedVersionException();
		}
		SettlementState state = gson.fromJson(root, SettlementState.class);
		if (!preserves(root, gson.toJsonTree(state)))
		{
			throw new JsonParseException("Unknown or incompatible saved fields");
		}
		validate(state);
		return state;
	}

	private static JsonElement readJson(JsonReader reader, int depth) throws IOException
	{
		valid(depth <= 32);
		switch (reader.peek())
		{
			case BEGIN_OBJECT:
				JsonObject object = new JsonObject();
				reader.beginObject();
				while (reader.hasNext())
				{
					String name = reader.nextName();
					valid(!object.has(name));
					object.add(name, readJson(reader, depth + 1));
				}
				reader.endObject();
				return object;
			case BEGIN_ARRAY:
				JsonArray array = new JsonArray();
				reader.beginArray();
				while (reader.hasNext())
				{
					array.add(readJson(reader, depth + 1));
				}
				reader.endArray();
				return array;
			case STRING:
				return new JsonPrimitive(reader.nextString());
			case NUMBER:
				return new JsonPrimitive(new BigDecimal(reader.nextString()));
			case BOOLEAN:
				return new JsonPrimitive(reader.nextBoolean());
			case NULL:
				reader.nextNull();
				return com.google.gson.JsonNull.INSTANCE;
			default:
				throw new JsonParseException("Expected a JSON value");
		}
	}

	private static boolean preserves(JsonElement source, JsonElement result)
	{
		if (source.isJsonObject() && result != null && result.isJsonObject())
		{
			JsonObject target = result.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject().entrySet())
			{
				if (!preserves(entry.getValue(), target.get(entry.getKey())))
				{
					return false;
				}
			}
			return true;
		}
		if (source.isJsonArray() && result != null && result.isJsonArray())
		{
			if (source.getAsJsonArray().size() != result.getAsJsonArray().size())
			{
				return false;
			}
			for (int index = 0; index < source.getAsJsonArray().size(); index++)
			{
				if (!preserves(source.getAsJsonArray().get(index), result.getAsJsonArray().get(index)))
				{
					return false;
				}
			}
			return true;
		}
		if (source.isJsonPrimitive() && source.getAsJsonPrimitive().isNumber()
			&& result != null && result.isJsonPrimitive() && result.getAsJsonPrimitive().isNumber())
		{
			return source.getAsBigDecimal().compareTo(result.getAsBigDecimal()) == 0;
		}
		return source.equals(result);
	}

	private static void validate(SettlementState state)
	{
		valid(state.getStock() != null && state.getLabour() != null && state.getLevels() != null
			&& state.getBlueprints() != null && state.getBounties() != null && state.getBoosts() != null
			&& state.getEvents() != null && state.getExpeditions() != null && state.getLog() != null);
		state.getStock().forEach((key, value) -> valid(key != null && nonnegative(value)));
		state.getLabour().forEach((key, value) -> valid(key != null && nonnegative(value)));
		state.getLevels().forEach((key, value) -> valid(key != null && value != null && value >= 0
			&& (key == Building.WONDER || value <= Balance.MAX_BUILDING_LEVEL
				&& value <= state.getLevel(Building.TOWN_HALL))));
		valid(state.getLevel(Building.TOWN_HALL) >= 1);
		valid(!state.getBlueprints().contains(null) && state.getPlaytimeTicks() >= 0
			&& state.getNextEventAtPlaytime() >= 0 && state.getNextBountyId() > 0 && state.getBountiesCompleted() >= 0);
		state.getBoosts().forEach(boost -> valid(boost != null && boost.getResource() != null
			&& Double.isFinite(boost.getMultiplier()) && boost.getMultiplier() >= 1 && boost.getRemainingTicks() > 0));
		state.getEvents().forEach(event -> valid(event != null && event.getEvent() != null && event.getEndsAtPlaytime() >= 0));
		state.getExpeditions().forEach((key, progress) ->
		{
			valid(key != null && progress != null && progress.getObjectives() != null);
			progress.getObjectives().forEach((objective, amount) -> valid(objective != null && nonnegative(amount)));
		});
		Set<Integer> ids = new HashSet<>();
		for (Bounty bounty : state.getBounties())
		{
			valid(bounty != null && bounty.getType() != null && bounty.getRarity() != null
				&& bounty.getId() > 0 && ids.add(bounty.getId()) && bounty.getId() < state.getNextBountyId()
				&& bounty.getTarget() > 0 && nonnegative(bounty.getProgress()) && bounty.getExpiresAtPlaytime() > 0
				&& bounty.getReward() != null && bounty.getReward().getType() != null);
			valid(bounty.getType() != BountyType.SKILL_XP || bounty.getSkill() != null);
			valid(bounty.getType() != BountyType.KIND_XP || bounty.getKind() != null);
			valid(bounty.getType() != BountyType.DELIVER || bounty.getResource() != null);
			valid(bounty.getReward().getResources() != null);
			bounty.getReward().getResources().forEach((key, amount) -> valid(key != null && amount != null && amount >= 0));
			valid(bounty.getReward().getType() != RewardType.BLUEPRINT || bounty.getReward().getBlueprint() != null);
			valid(bounty.getReward().getType() != RewardType.BOOST || bounty.getReward().getBoostResource() != null
				&& Double.isFinite(bounty.getReward().getBoostMultiplier()) && bounty.getReward().getBoostMultiplier() >= 1
				&& bounty.getReward().getBoostTicks() > 0);
		}
		state.getLog().forEach(entry -> valid(entry != null && entry.getMessage() != null && entry.getPlaytimeTicks() >= 0));
	}

	private static boolean nonnegative(Double value)
	{
		return value != null && Double.isFinite(value) && value >= 0;
	}

	private static void valid(boolean condition)
	{
		if (!condition)
		{
			throw new JsonParseException("Invalid settlement progress");
		}
	}

	private static final class UnsupportedVersionException extends JsonParseException
	{
		private UnsupportedVersionException()
		{
			super("Unsupported settlement schema");
		}
	}
}