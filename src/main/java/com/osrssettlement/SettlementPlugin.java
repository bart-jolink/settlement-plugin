package com.osrssettlement;

import com.google.gson.Gson;
import com.osrssettlement.engine.SettlementEngine;
import com.osrssettlement.model.BossTier;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.ClueTier;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.persistence.SettlementStore;
import com.osrssettlement.tracking.BossRegistry;
import com.osrssettlement.tracking.ChatEventParser;
import com.osrssettlement.tracking.XpTracker;
import com.osrssettlement.ui.SettlementActions;
import com.osrssettlement.ui.SettlementPanel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "OSRS Settlement",
	description = "Build a settlement in the sidebar, fuelled by your skilling, Slayer, bossing and clue scrolls",
	tags = {"settlement", "town", "minigame", "building", "bounty", "xp", "boss", "clue"}
)
public class SettlementPlugin extends Plugin
{
	private static final int SAVE_INTERVAL_TICKS = 100;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SettlementStore store;

	@Inject
	private Gson gson;

	private final XpTracker xpTracker = new XpTracker();
	private final Random random = new Random();

	private SettlementPanel panel;
	private NavigationButton navButton;
	private final ProfileGuard profiles = new ProfileGuard();
	private PanelActions panelActions;
	private final Map<String, PendingSave> pending = new HashMap<>();
	private SettlementStore.Session session;
	private String protectionMessage;

	private static final class PendingSave
	{
		private final SettlementStore.Session session;
		private final SettlementEngine engine;
		private final String json;

		private PendingSave(SettlementStore.Session session, SettlementEngine engine, String json)
		{
			this.session = session;
			this.engine = engine;
			this.json = json;
		}
	}

	private SettlementEngine engine;
	private String profileKey;
	private boolean dirty;
	private boolean uiDirty;
	private int ticksSinceSave;

	@Override
	protected synchronized void startUp()
	{
		long startupGeneration = profiles.start();
		panelActions = new PanelActions();
		panel = new SettlementPanel(itemManager, panelActions);
		navButton = NavigationButton.builder()
			.tooltip("Settlement")
			.icon(ImageUtil.loadImageResource(SettlementPlugin.class, "icon.png"))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		clientThread.invoke(() ->
		{
			synchronized (SettlementPlugin.this)
			{
				if (!profiles.accepts(startupGeneration))
				{
					return;
				}
				loadProfile(configManager.getRSProfileKey());
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					xpTracker.snapshot(client::getSkillExperience);
				}
				else
				{
					xpTracker.reset();
				}
			}
		});
	}

	@Override
	protected synchronized void shutDown()
	{
		profiles.stop();
		flushPending();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;

		engine = null;
		profileKey = null;
		session = null;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		long selected = profiles.current();
		clientThread.invoke(() ->
		{
			synchronized (SettlementPlugin.this)
			{
				if (!profiles.accepts(selected))
				{
					return;
				}
				saveNow();
				loadProfile(configManager.getRSProfileKey());
				xpTracker.reset();
			}
		});
	}

	@Subscribe
	public synchronized void onClientShutdown(ClientShutdown event)
	{
		profiles.stop();
		flushPending();
	}

	private void flushPending()
	{
		for (PendingSave saved : new ArrayList<>(pending.values()))
		{
			if (saved.json == null)
			{
				log.warn("Settlement has invalid unsaved progress; shutdown will not overwrite saved data");
				continue;
			}
			try
			{
				store.save(saved.session, saved.json);
				pending.remove(saved.session.getProfileKey());
			}
			catch (RuntimeException exception)
			{
				log.warn("Settlement progress could not be saved; pending progress remains in memory", exception);
			}
		}
	}

	@Subscribe
	public synchronized void onGameStateChanged(GameStateChanged event)
	{
		if (!profiles.isRunning())
		{
			return;
		}
		switch (event.getGameState())
		{
			case LOGGING_IN:
			case HOPPING:
				xpTracker.reset();
				break;
			case LOGIN_SCREEN:
				xpTracker.reset();
				saveNow();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public synchronized void onGameTick(GameTick event)
	{
		if (!profiles.isRunning())
		{
			return;
		}
		if (xpTracker.tick())
		{
			xpTracker.snapshot(client::getSkillExperience);
		}

		if (engine == null || protectionMessage != null)
		{
			return;
		}

		engine.onTick();
		markChanged();

		if (++ticksSinceSave >= SAVE_INTERVAL_TICKS)
		{
			saveNow();
		}
		if (uiDirty)
		{
			pushUi();
		}
	}

	@Subscribe
	public synchronized void onStatChanged(StatChanged event)
	{
		if (!profiles.isRunning())
		{
			return;
		}
		int gained = xpTracker.onStatChanged(event.getSkill(), event.getXp());
		if (gained > 0 && engine != null && protectionMessage == null)
		{
			engine.onXp(event.getSkill(), gained);
			markChanged();
		}
	}

	@Subscribe
	public synchronized void onChatMessage(ChatMessage event)
	{
		if (!profiles.isRunning() || protectionMessage != null || engine == null
			|| (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}

		String message = event.getMessage();
		String boss = ChatEventParser.parseKillCount(message);
		if (boss != null)
		{
			BossTier tier = BossRegistry.tierOf(boss);
			if (tier != null)
			{
				engine.onBossKill(boss, tier, BossRegistry.isRaid(boss));
				markChanged();
			}
			return;
		}

		ClueTier clue = ChatEventParser.parseClue(message);
		if (clue != null)
		{
			engine.onClueCompleted(clue);
			markChanged();
		}
	}

	private void loadProfile(String key)
	{
		profiles.advance();
		profileKey = key;
		engine = null;
		session = null;
		dirty = false;
		ticksSinceSave = 0;
		protectionMessage = null;
		if (key == null)
		{
			pushUi();
			return;
		}
		PendingSave retained = pending.get(key);
		if (retained != null)
		{
			session = retained.session;
			engine = retained.engine;
			dirty = true;
			protectionMessage = "Unsaved progress is retained for this account. Retry saving before continuing.";
			pushUi();
			return;
		}
		session = store.load(key);
		if (!session.isWritable())
		{
			protectionMessage = session.getMessage();
		}
		else
		{
			try
			{
				SettlementState state = session.getStatus() == SettlementStore.Status.NEW
					? SettlementEngine.newSettlement() : session.getState();
				SettlementEngine candidate = new SettlementEngine(state, random);
				store.snapshot(candidate.getState());
				engine = candidate;
				if (session.getStatus() == SettlementStore.Status.NEW)
				{
					markChanged();
					saveNow();
				}
			}
			catch (RuntimeException exception)
			{
				log.warn("Unable to initialize settlement; saved progress was preserved", exception);
				protectionMessage = "Saved progress could not be initialized safely. It has not been replaced.";
			}
		}
		pushUi();
	}

	private void markChanged()
	{
		dirty = true;
		uiDirty = true;
		String json = null;
		try
		{
			json = store.snapshot(engine.getState());
		}
		catch (RuntimeException exception)
		{
			log.warn("Unable to prepare settlement progress for saving", exception);
			protectionMessage = "Progress could not be safely saved. Your existing save has been preserved.";
		}
		pending.put(profileKey, new PendingSave(session, engine, json));
		if (protectionMessage != null)
		{
			pushUi();
		}
	}

	private void saveNow()
	{
		if (!dirty || engine == null || profileKey == null)
		{
			return;
		}
		try
		{
			String json = store.snapshot(engine.getState());
			pending.put(profileKey, new PendingSave(session, engine, json));
			store.save(session, json);
			pending.remove(profileKey);
			dirty = false;
			ticksSinceSave = 0;
			protectionMessage = null;
		}
		catch (RuntimeException exception)
		{
			log.warn("Unable to save settlement progress", exception);
			protectionMessage = "Progress is unsaved and retained in memory. "
				+ "Retry before closing RuneLite. Changed or incompatible saved data will not be overwritten.";
			pushUi();
		}
	}

	private void pushUi()
	{
		pushUi(null);
	}

	private void pushUi(String rewardText)
	{
		uiDirty = false;
		SettlementPanel target = panel;
		if (target == null || !target.isActive() && protectionMessage == null && engine != null)
		{
			return;
		}

		long selected = profiles.current();
		PanelActions actions = panelActions;
		String message = protectionMessage;
		SettlementState snapshot = engine == null || message != null ? null
			: gson.fromJson(gson.toJson(engine.getState()), SettlementState.class);
		String restoreSnapshot = snapshot == null ? null : store.restorableSnapshot(session);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != target || !actions.view.show(selected))
			{
				return;
			}
			actions.restoreSnapshot = restoreSnapshot;
			if (message != null)
			{
				target.showProtected(message);
			}
			else
			{
				target.update(snapshot, restoreSnapshot != null);
			}
			if (rewardText != null && message == null)
			{
				target.showBountyReward(rewardText);
			}
		});
	}

	private class PanelActions implements SettlementActions
	{
		private final ProfileGuard.View view = profiles.newView();
		private volatile String restoreSnapshot;

		private void enqueue(long selected, Runnable action, boolean gameplay)
		{
			clientThread.invoke(() ->
			{
				synchronized (SettlementPlugin.this)
				{
					if (PanelActions.this != panelActions || !profiles.accepts(selected)
						|| gameplay && (engine == null || protectionMessage != null || !session.isWritable()))
					{
						return;
					}
					action.run();
				}
			});
		}

		@Override
		public void upgrade(Building building)
		{
			enqueue(view.selected(), () ->
			{
				if (engine != null && engine.upgrade(building).isOk())
				{
					markChanged();
					saveNow();
				}
				pushUi();
			}, true);
		}

		@Override
		public void claimBounty(int bountyId)
		{
			runRewardAction(() -> engine.claimBounty(bountyId));
		}

		@Override
		public void claimExpedition(ExpeditionId expeditionId)
		{
			runRewardAction(() -> engine.claimExpedition(expeditionId));
		}

		@Override
		public void tradeKeldagrimConsortium(Resource input, Resource output)
		{
			runRewardAction(() -> engine.tradeAtKeldagrimConsortium(input, output));
		}

		@Override
		public void setLabourPaused(boolean paused)
		{
			enqueue(view.selected(), () ->
			{
				if (engine != null && engine.getState().isLabourPaused() != paused)
				{
					engine.setLabourPaused(paused);
					markChanged();
					saveNow();
				}
				pushUi();
			}, true);
		}

		// action returns reward text, or null when nothing changed
		private void runRewardAction(Supplier<String> action)
		{
			enqueue(view.selected(), () ->
			{
				String rewardText = engine == null ? null : action.get();
				if (rewardText != null)
				{
					markChanged();
					saveNow();
				}
				pushUi(rewardText);
			}, true);
		}

		@Override
		public void reset()
		{
			prepareReset().run();
		}

		@Override
		public Runnable prepareReset()
		{
			long selected = view.selected();
			return () -> enqueue(selected, () ->
			{
				try
				{
					SettlementEngine replacement = new SettlementEngine(SettlementEngine.newSettlement(), random);
					store.reset(session, store.snapshot(engine.getState()), store.snapshot(replacement.getState()));
					engine = replacement;
					pending.remove(profileKey);
					dirty = false;
					ticksSinceSave = 0;
					xpTracker.reset();
					profiles.advance();
				}
				catch (RuntimeException exception)
				{
					log.warn("Settlement reset was cancelled", exception);
					dirty = true;
					markChanged();
					protectionMessage = "Reset could not be completed. " + exception.getMessage();
				}
				pushUi();
			}, true);
		}

		@Override
		public Runnable prepareRestore()
		{
			long selected = view.selected();
			String backup = restoreSnapshot;
			return () -> enqueue(selected, () ->
			{
				try
				{
					if (backup == null)
					{
						throw new IllegalStateException("No valid restore point is available.");
					}
					SettlementEngine replacement = new SettlementEngine(store.restoreState(backup), random);
					store.snapshot(replacement.getState());
					store.restore(session, store.snapshot(engine.getState()), backup);
					engine = replacement;
					pending.remove(profileKey);
					dirty = false;
					ticksSinceSave = 0;
					xpTracker.reset();
					profiles.advance();
				}
				catch (RuntimeException exception)
				{
					log.warn("Settlement restore was cancelled", exception);
					if (!session.isWritable())
					{
						markChanged();
						protectionMessage = "Restore could not be safely rolled back. Retry before closing RuneLite.";
					}
					else
					{
						SettlementPanel target = panel;
						SwingUtilities.invokeLater(() ->
						{
							if (panel == target && profiles.accepts(selected))
							{
								target.showRestoreError(exception.getMessage());
							}
						});
					}
				}
				pushUi();
			}, true);
		}

		@Override
		public void retry()
		{
			enqueue(view.selected(), () ->
			{
				if (dirty)
				{
					try
					{
						store.recoverRestore(session);
					}
					catch (RuntimeException exception)
					{
						log.warn("Settlement restore recovery is still pending", exception);
						pushUi();
						return;
					}
					saveNow();
					xpTracker.reset();
				}
				else
				{
					loadProfile(profileKey);
					xpTracker.reset();
				}
				pushUi();
			}, false);
		}

		@Override
		public void refresh()
		{
			enqueue(profiles.current(), SettlementPlugin.this::pushUi, false);
		}
	}
}
