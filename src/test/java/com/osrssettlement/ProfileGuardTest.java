package com.osrssettlement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ProfileGuardTest
{
	@Test
	public void queuedActionsAndResetOrRestoreConfirmationCannotCrossProfiles()
	{
		ProfileGuard guard = new ProfileGuard();
		guard.start();
		ProfileGuard.View view = guard.newView();
		assertTrue(view.show(guard.current()));
		long confirmedReset = view.selected();
		guard.advance();
		assertFalse(guard.accepts(confirmedReset));
		assertFalse(guard.accepts(view.selected()));
		assertTrue(view.show(guard.current()));
		assertTrue(guard.accepts(view.selected()));
		long confirmedRestore = view.selected();
		guard.advance();
		assertFalse(guard.accepts(confirmedReset));
		assertFalse(guard.accepts(confirmedRestore));
	}

	@Test
	public void oldSidebarCannotAdoptNewSidebarSession()
	{
		ProfileGuard guard = new ProfileGuard();
		guard.start();
		ProfileGuard.View oldView = guard.newView();
		oldView.show(guard.current());
		long oldSnapshot = guard.current();
		guard.stop();
		assertFalse(guard.accepts(oldView.selected()));
		guard.start();
		ProfileGuard.View newView = guard.newView();
		assertFalse(guard.accepts(newView.selected()));
		assertFalse(oldView.show(oldSnapshot));
		assertTrue(newView.show(guard.current()));
		assertTrue(guard.accepts(newView.selected()));
		assertFalse(guard.accepts(oldView.selected()));
	}

	@Test
	public void staleSnapshotsCannotRedisplayPreviousProfile()
	{
		ProfileGuard guard = new ProfileGuard();
		guard.start();
		ProfileGuard.View view = guard.newView();
		long previous = guard.current();
		guard.advance();
		assertTrue(view.show(guard.current()));
		assertFalse(view.show(previous));
		assertTrue(guard.accepts(view.selected()));
		guard.stop();
		assertFalse(view.show(guard.current()));
	}
}