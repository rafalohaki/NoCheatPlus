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
package fr.neatmonster.nocheatplus.stats;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;

import fr.neatmonster.nocheatplus.logging.StaticLog;

/**
 * A not too fat timings class re-used from other plugins.
 * @author asofold
 *
 */
public final class Timings {
	
        public static final class Entry{
                /** Total accumulated time. */
                private long val = 0;
                /** Number of recorded samples. */
                private long n = 0;
                /** Minimum observed value. */
                private long min = Long.MAX_VALUE;
                /** Maximum observed value. */
                private long max = Long.MIN_VALUE;

                long getVal() {
                        return val;
                }

                void setVal(final long val) {
                        this.val = val;
                }

                void addVal(final long delta) {
                        this.val += delta;
                }

                long getCount() {
                        return n;
                }

                void setCount(final long count) {
                        this.n = count;
                }

                void incrementCount() {
                        this.n += 1;
                }

                long getMin() {
                        return min;
                }

                void setMin(final long value) {
                        this.min = value;
                }

                long getMax() {
                        return max;
                }

                void setMax(final long value) {
                        this.max = value;
                }

                void updateRange(final long value) {
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                }
        }
	
	private long tsStats = 0;
	private long periodStats = 12345;
	private long nVerbose = 500;
	private long nDone = 0;
	private boolean logStats = false;
	private boolean showRange = true;
	
	private final Map<Integer, Entry> entries = new HashMap<Integer, Timings.Entry>();
	private final DecimalFormat f;
	private final String label;
	
	/**
	 * Map id to name.
	 */
	private final Map<Integer, String> idKeyMap = new HashMap<Integer, String>();
	
	/**
	 * Map exact name to id. 
	 */
	private final Map<String, Integer> keyIdMap = new HashMap<String, Integer>();
	
	int maxId = 0;
	
	public Timings(){
		this("[STATS]");
	}
	
	public Timings(final String label){
		this.label = label;
		f = new DecimalFormat();
		f.setGroupingUsed(true);
		f.setGroupingSize(3);
		DecimalFormatSymbols s = f.getDecimalFormatSymbols();
		s.setGroupingSeparator(',');
		f.setDecimalFormatSymbols(s);
	}
	
	public final void addStats(final Integer key, final long value){
                Entry entry = entries.get(key);
                if (entry != null) {
                        entry.incrementCount();
                        entry.addVal(value);
                        entry.updateRange(value);
                } else {
                        entry = new Entry();
                        entry.setVal(value);
                        entry.setCount(1);
                        entries.put(key, entry);
                        entry.setMin(value);
                        entry.setMax(value);
                }
		if (!logStats) return;
		nDone++;
		if ( nDone>nVerbose){
			nDone = 0;
			long ts = System.currentTimeMillis();
			if ( ts > tsStats+periodStats){
				tsStats = ts;
				// print out stats !
				StaticLog.logInfo(getStatsStr());
			}
		}
	}
	
	/**
	 * Get stats representation without ChatColor.
	 * @return
	 */
	public final String getStatsStr() {
		return getStatsStr(false);
	}
	
	public final String getStatsStr(final boolean colors) {
		final StringBuilder b = new StringBuilder(400);
		b.append(label+" ");
		boolean first = true;
		for (final Integer id : entries.keySet()){
			if ( !first) b.append(" | ");
                        final Entry entry = entries.get(id);
                        String av = f.format(entry.getVal() / entry.getCount());
                        String key = getKey(id);
                        String n = f.format(entry.getCount());
			if (colors){
				key = ChatColor.GREEN + key + ChatColor.WHITE;
				n = ChatColor.AQUA + n + ChatColor.WHITE;
				av = ChatColor.YELLOW + av + ChatColor.WHITE;
			}
			b.append(key+" av="+av+" n="+n);
                        if (showRange) {
                                b.append(" rg=")
                                 .append(f.format(entry.getMin()))
                                 .append("..")
                                 .append(f.format(entry.getMax()));
                        }
			first = false;
		}
		return b.toString();
	}
	
	/**
	 * Always returns some string, if not key is there, stating that no key is there.
	 * @param id
	 * @return
	 */
	public final String getKey(final Integer id) {
		String key = idKeyMap.get(id);
		if (key == null){
			key = "<no key for id: "+id+">";
			idKeyMap.put(id, key);
			keyIdMap.put(key, id);
		}
		return key;
		
	}
	
	/**
	 * Get a new id for the key.
	 * @param key
	 * @return
	 */
	public final Integer getNewId(final String key){
		maxId++;
		while (idKeyMap.containsKey(maxId)){
			maxId++; // probably not going to happen...
		}
		idKeyMap.put(maxId, key);
		keyIdMap.put(key, maxId);
		return maxId;
	}
	
	/**
	 * 
	 * @param key
	 * @param create if to create a key - id mapping if not existent.
	 * @return
	 */
	public final Integer getId(final String key, final boolean create){
		final Integer id = keyIdMap.get(key);
		if (id == null){
			if (create) return getNewId(key);
			else return null;
		}
		else return id;
	}
	
	/**
	 * Gets the id if present, returns null otherwise.
	 * @param key not null
	 * @return Key or null.
	 */
	public final Integer getId(final String key){
		return keyIdMap.get(key);
	}

	public final void clear(){
		entries.clear();
	}
	
	public final void setLogStats(final boolean log){
		logStats = log;
	}
	
	public final void setShowRange(final boolean set){
		showRange = set;
	}

}
