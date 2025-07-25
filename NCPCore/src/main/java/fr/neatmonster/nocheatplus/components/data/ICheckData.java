/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.components.data;

/**
 * This is for future purposes. Might remove...<br>
 * Some checks in chat synchronize over data, so using this from exectueActions
 * can deadlock.<br>
 * One might think of making this an interface not for the internally used data,
 * but for copy of data for external use only. Then sync could go over other
 * objects for async access.
 *
 * @author asofold
 */
public interface ICheckData extends IData {

}
