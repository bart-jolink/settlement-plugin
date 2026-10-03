package com.osrssettlement;

final class ProfileGuard
{
	private volatile long generation;
	private volatile boolean running;

	long start()
	{
		generation++;
		running = true;
		return generation;
	}

	void stop()
	{
		running = false;
		generation++;
	}

	void advance()
	{
		generation++;
	}

	long current()
	{
		return generation;
	}

	boolean isRunning()
	{
		return running;
	}

	boolean accepts(long selected)
	{
		return running && selected == generation;
	}

	View newView()
	{
		return new View();
	}

	final class View
	{
		private volatile long displayed = -1;

		boolean show(long selected)
		{
			if (!accepts(selected))
			{
				return false;
			}
			displayed = selected;
			return true;
		}

		long selected()
		{
			return displayed;
		}
	}
}