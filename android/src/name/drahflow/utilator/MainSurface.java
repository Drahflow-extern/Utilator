package name.drahflow.utilator;

import android.app.*;
import android.graphics.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.util.*;
import java.util.*;

import static name.drahflow.utilator.Util.*;

class MainSurface extends WidgetView {
	private long minimumUtility = 0;

	private Map<String, Object> currentTask;
	private Timer timer;
	private Date startedAt;
	private int timeRunning;
	private Utilator ctx;

	public MainSurface(Utilator ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	public void setTask(String gid) {
		if(gid == null) {
			currentTask = null;
			return;
		}

		currentTask = ctx.db.loadTask(gid);

		Log.i("Utilator", "MainSurface, loaded task: " + currentTask);

		if(timer != null) {
			timer.cancel();
			timer = null;
			timeRunning = 0;
		}

		invalidate();
	}

	public void onDraw(Canvas c) {
		super.onDraw(c);

		if(currentTask == null) {
			String noTask = "No useful task currently exists.";
			c.drawText(noTask, (getWidth() - PRIMARY_COLOR.measureText(noTask)) / 2, 100, PRIMARY_COLOR);
			return;
		}

		int y = 16;
		for(String line: loadString(currentTask, "title").split("\n")) {
			y = drawWrapped(c, line, 10, 320, y, PRIMARY_COLOR) + 20;
		}

		for(String line: loadString(currentTask, "description").split("\n")) {
			y = drawWrapped(c, line, 10, 320, y, PRIMARY_COLOR) + 20;
		}

		float importance = Distribution.calculateImportance(ctx, ctx.db, new Date(), currentTask);
		y = drawWrapped(c, importance * 3600 + " u / h", 10, 320, y, PRIMARY_COLOR) + 20;

		c.drawLine(0, 198, getWidth() * (loadInt(currentTask, "seconds_taken") + timeRunning) /
				loadInt(currentTask, "seconds_estimate"), 198, PRIMARY_COLOR);
		String estStr = humanTime(loadInt(currentTask, "seconds_estimate"));
		c.drawText(estStr, (getWidth() - PRIMARY_COLOR.measureText(estStr)) / 2, 198, PRIMARY_COLOR);
	}

	@Override protected void setupWidgets() {
		super.setupWidgets();

		widgets.add(new Widget() {
			{
				activateZone = new Rect(0, 200, getWidth(), 220);
			}
			@Override public void onDraw(Canvas c) {
				c.drawLine(0, 200, getWidth(), 200, SECONDARY_COLOR);
				c.drawLine(0, 220, getWidth(), 220, SECONDARY_COLOR);

				int minimumUtilityX = reverseExponentialMap(minimumUtility, getWidth());
				c.drawLine(minimumUtilityX, 200, minimumUtilityX, 220, PRIMARY_COLOR);

				String min = ((float)minimumUtility / 1000) + " u/h";
				c.drawText(min, (getWidth() - PRIMARY_COLOR.measureText(min)), 217, PRIMARY_COLOR);
			}

			@Override public void onMove(int x, int y) {
				minimumUtility = exponentialMap(x, getWidth());
			}
		});
		widgets.add(new Button() {
			{
				activateZone = new Rect(0, 220, getWidth() / 3, 280);
				title = "stuff";
				actions = new Rect[] {
					new Rect(0, 0, getWidth() / 3, 180),
					new Rect(getWidth() / 3, 0, getWidth() * 2 / 3, 180)
				};
				actionNames = new String[] {
					"timer",
					"simul"
				};
			}

			@Override public void invokeAction(int n) {
				switch(n) {
					case 0:
						Log.i("Utilator", "MainSurface, task started");
						startedAt = new Date();

						if(timer != null) timer.cancel();
						timer = new Timer();

						timer.schedule(new TimerTask() {
							public void run() {
								post(new Runnable() {
									public void run() {
										timeRunning = (int)(new Date().getTime() - startedAt.getTime()) / 1000;
										invalidate();
									}
								});
							}
						}, 0, 1000);
						break;
					case 1:
						Log.i("Utilator", "MainSurface, switching to simulation");

						SimulationSurface simulation = new SimulationSurface(ctx);
						ctx.setContentView(simulation);
						break;
				}
			}
		});
		widgets.add(new Button() {
			{
				activateZone = new Rect(getWidth() / 3, 220, getWidth() * 2 / 3, 280);
				title = "finish";
				actions = new Rect[] {
					new Rect(0, 0, getWidth() / 3, getHeight()),
					new Rect(getWidth() / 3, 0, getWidth() * 2 / 3, 180),
					new Rect(getWidth() * 2 / 3, 0, getWidth(), 180)
				};
				actionNames = new String[] {
					"retry", "done", "already"
				};
			}

			public long retryTime;

			@Override public void onMove(int x, int y) {
				retryTime = exponentialMap(y, getHeight());
				actionNames[0] = "retry " + humanTime(retryTime) + " / " + isoFullDate(new Date(new Date().getTime() + retryTime * 1000));
				super.onMove(x, y);
			}

			@Override public void invokeAction(int n) {
				if(currentTask == null) {
					toast("There is no task");
					return;
				}

				switch(n) {
					case 0:
						Log.i("Utilator", "MainSurface, task failed, retry in " + retryTime);
						if(ctx.db.loadTaskLikelyhoodTime(loadString(currentTask, "gid")).isEmpty()) {
							ctx.db.addLikelyhoodTime(loadString(currentTask, "gid"), "0constant:990");
						}

						ctx.db.addLikelyhoodTime(
								loadString(currentTask, "gid"),
								"2mulrange:" + isoFullDate(new Date()) +
								";" + isoFullDate(new Date(new Date().getTime() + retryTime * 1000)) +
								";0");
						ctx.db.touchTask(loadString(currentTask, "gid"));

						ctx.switchToBestTask();
						break;

					case 1:
						Log.i("Utilator", "MainSurface, task done in " + timeRunning);
						ctx.db.addTimeTaken(
								loadString(currentTask, "gid"),
								timeRunning);
						ctx.db.setStatus(
								loadString(currentTask, "gid"),
								100);
						ctx.db.touchTask(loadString(currentTask, "gid"));

						ctx.switchToBestTask();
						break;

					case 2:
						Log.i("Utilator", "MainSurface, task already done");
						ctx.db.setStatus(
								loadString(currentTask, "gid"),
								100);
						ctx.db.touchTask(loadString(currentTask, "gid"));

						ctx.switchToBestTask();
						break;
				}
			}
		});
		widgets.add(new Button() {
			{
				activateZone = new Rect(getWidth() * 2 / 3, 220, getWidth(), 280);
				title = "edit";
				actions = new Rect[] {
					new Rect(0, 0, getWidth() / 3, getHeight()),
					new Rect(getWidth() / 3, 0, getWidth() * 2 / 3, getHeight()),
					new Rect(getWidth() * 2 / 3, 0, getWidth(), 90),
					new Rect(getWidth() * 2 / 3, 90, getWidth(), 180)
				};
				actionNames = new String[] {
					"completion", "utility", "full edit", "new task"
				};
			}

			public int selectedStatus;
			public int selectedUtility;

			@Override public void onMove(int x, int y) {
				selectedStatus = y * 100 / getHeight();
				selectedUtility = (int)exponentialMap(y, getHeight(), x - getWidth() / 3, getWidth() / 3);

				actionNames[0] = "status " + selectedStatus + "%";
				actionNames[1] = "utility " + ((float)selectedUtility / 1000) + " u";
				super.onMove(x, y);
			}

			@Override public void invokeAction(int n) {
				switch(n) {
					case 0:
						if(currentTask == null) {
							toast("There is no task");
							return;
						}

						Log.i("Utilator", "MainSurface, task completion " + selectedStatus);
						ctx.db.setStatus(loadString(currentTask, "gid"), selectedStatus);
						ctx.db.touchTask(loadString(currentTask, "gid"));

						ctx.switchToBestTask();
						break;

					case 1:
						if(currentTask == null) {
							toast("There is no task");
							return;
						}

						Log.i("Utilator", "MainSurface, task utility changed to " + selectedUtility);
						ctx.db.setUtility(loadString(currentTask, "gid"), selectedUtility);
						ctx.db.touchTask(loadString(currentTask, "gid"));

						ctx.switchToBestTask();
						break;

					case 2: {
						if(currentTask == null) {
							toast("There is no task");
							return;
						}

						Log.i("Utilator", "MainSurface, editing task");
						EditSurface editor = new EditSurface(ctx);
						editor.setTask(loadString(currentTask, "gid"));

						ctx.setContentView(editor);
					} break;

					case 3: {
						Log.i("Utilator", "MainSurface, new task");

						Map<String, Object> task = new HashMap<String, Object>();
						task.put("title", "");
						task.put("description", "");
						task.put("seconds_taken", 0);
						task.put("seconds_estimate", 600);
						task.put("status", 0);
						
						String gid = ctx.db.createTask(task);
						ctx.db.touchTask(gid);

						EditSurface editor = new EditSurface(ctx);
						editor.setTask(gid);

						ctx.setContentView(editor);
					} break;
				}
			}
		});
	}

	public void onAttachedToWindow() {
	}

	public void onDetachFromWindow() {
		if(timer != null) timer.cancel();
	}

	public void reloadTask() {
		if(currentTask == null) {
			ctx.switchToBestTask();
		} else {
			setTask(loadString(currentTask, "gid"));
		}
	}
	
	private void toast(String msg) {
		Toast toast = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT);
		toast.show();
	}
}
