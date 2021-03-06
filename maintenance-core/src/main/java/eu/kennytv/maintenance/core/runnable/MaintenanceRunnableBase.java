/*
 * Maintenance - https://git.io/maintenancemode
 * Copyright (C) 2018 KennyTV (https://github.com/KennyTV)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.kennytv.maintenance.core.runnable;

import eu.kennytv.maintenance.core.MaintenancePlugin;
import eu.kennytv.maintenance.core.Settings;
import eu.kennytv.maintenance.core.util.Task;

public abstract class MaintenanceRunnableBase implements Runnable {
    protected final MaintenancePlugin plugin;
    protected final Settings settings;
    protected final boolean enable;
    private final Task task;
    protected int seconds;

    protected MaintenanceRunnableBase(final MaintenancePlugin plugin, final Settings settings, final int seconds, final boolean enable) {
        this.plugin = plugin;
        this.settings = settings;
        this.seconds = seconds;
        this.enable = enable;
        this.task = plugin.startMaintenanceRunnable(this);
    }

    @Override
    public void run() {
        if (seconds == 0) {
            finish();
        } else if (settings.getBroadcastIntervals().contains(seconds)) {
            broadcast(enable ? startMessageKey() : endMessageKey());
        }

        seconds--;
    }

    public String getTime() {
        final int preHours = this.seconds / 60;
        final int minutes = preHours % 60;
        final int seconds = this.seconds % 60;

        final StringBuilder buider = new StringBuilder();
        append(buider, "hour", preHours / 60);
        append(buider, "minute", minutes);
        append(buider, "second", seconds);
        return buider.toString();
    }

    private void append(final StringBuilder builder, final String timeUnit, final int time) {
        if (time == 0) return;
        if (builder.length() != 0)
            builder.append(" ");
        builder.append(time).append(" ").append(settings.getMessage(time == 1 ? timeUnit : timeUnit + "s"));
    }

    public boolean shouldEnable() {
        return enable;
    }

    public int getSecondsLeft() {
        return seconds;
    }

    public Task getTask() {
        return task;
    }

    protected void broadcast(final String message) {
        plugin.broadcast(message);
    }

    protected abstract void finish();

    protected abstract String startMessageKey();

    protected abstract String endMessageKey();
}
