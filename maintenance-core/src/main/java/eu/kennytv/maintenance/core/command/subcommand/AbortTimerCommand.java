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

package eu.kennytv.maintenance.core.command.subcommand;

import eu.kennytv.maintenance.core.MaintenancePlugin;
import eu.kennytv.maintenance.core.command.CommandInfo;
import eu.kennytv.maintenance.core.util.SenderInfo;

public final class AbortTimerCommand extends CommandInfo {

    public AbortTimerCommand(final MaintenancePlugin plugin) {
        super(plugin, "timer", "§6/maintenance aborttimer §7(If running, the current timer will be aborted)");
    }

    @Override
    public void execute(final SenderInfo sender, final String[] args) {
        if (args.length != 1) {
            sender.sendMessage(helpMessage);
            return;
        }

        if (!plugin.isTaskRunning()) {
            sender.sendMessage(getMessage("timerNotRunning"));
            return;
        }

        plugin.cancelTask();
        sender.sendMessage(getMessage("timerCancelled"));
    }
}