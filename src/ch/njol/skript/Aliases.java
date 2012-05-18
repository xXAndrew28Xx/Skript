/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011, 2012 Peter Güttinger
 * 
 */

package ch.njol.skript;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;

import ch.njol.skript.util.ItemData;
import ch.njol.skript.util.ItemType;
import ch.njol.skript.util.Utils;
import ch.njol.util.Pair;

/**
 * @author Peter Güttinger
 * 
 */
public abstract class Aliases {
	
	/**
	 * Note to self: never use this, use {@link {@link #getAlias(String)} instead.
	 */
	private final static HashMap<String, ItemType> aliases = new HashMap<String, ItemType>(2000);
	
	private final static ItemType getAlias(String s) {
		ItemType t = TriggerFileLoader.currentAliases.get(s);
		if (t != null)
			return t;
		return aliases.get(s);
	}
	
	private final static HashMap<Integer, MaterialName> materialNames = new HashMap<Integer, MaterialName>((int) (Material.values().length * 1.6));
	
	private final static ItemType everything = new ItemType();
	static {
		everything.all = true;
		everything.add(new ItemData());
		// this is not an alias!
	}
	
	/**
	 * 
	 * @param name mixedcase string
	 * @param value
	 * @param variations
	 * @return
	 */
	private static HashMap<String, ItemType> getAliases(final String name, final ItemType value, final HashMap<String, HashMap<String, ItemType>> variations) {
		final HashMap<String, ItemType> r = new HashMap<String, ItemType>();
		Matcher m;
		if ((m = Pattern.compile("\\[(.+?)\\]").matcher(name)).find()) {
			r.putAll(getAliases(m.replaceFirst(""), value, variations));
			r.putAll(getAliases(m.replaceFirst("$1"), value, variations));
		} else if ((m = Pattern.compile("\\((.+?)\\)").matcher(name)).find()) {
			final String[] split = m.group(1).split("\\|");
			if (split.length == 1) {
				Skript.error("brackets have a special meaning in aliases and cannot be used as usual");
			}
			for (final String s : split) {
				r.putAll(getAliases(m.replaceFirst(s), value, variations));
			}
		} else if ((m = Pattern.compile("\\{(.+?)\\}").matcher(name)).find()) {
			if (variations.get(m.group(1)) != null) {
				boolean hasDefault = false;
				for (final Entry<String, ItemType> v : variations.get(m.group(1)).entrySet()) {
					String n;
					if (v.getKey().equalsIgnoreCase("{default}")) {
						hasDefault = true;
						n = m.replaceFirst("");
					} else {
						n = m.replaceFirst(v.getKey());
					}
					final ItemType t = v.getValue().intersection(value);
					if (t != null)
						r.putAll(getAliases(n, addInfo(t, n), variations));
					else
						Skript.warning("'" + n + "' results in an empty alias (i.e. it doesn't map to any id/data), it will thus be ignored");
				}
				if (!hasDefault)
					r.putAll(getAliases(m.replaceFirst(""), value, variations));
			} else {
				Skript.error("unknown variation {" + m.group(1) + "}");
			}
		} else {
			r.put(name, addInfo(value, name));
		}
		return r;
	}
	
	/**
	 * 
	 * @param t
	 * @param name lowercase string
	 * @return
	 */
	private static ItemType addInfo(final ItemType t, final String name) {
		ItemType i;
		if (name.endsWith(" block") && (i = getAlias(name.substring(0, name.length() - " block".length()))) != null) {
			i.setBlock(t);
		} else if (name.endsWith(" item") && (i = getAlias(name.substring(0, name.length() - " item".length()))) != null) {
			i.setItem(t);
		} else if ((i = getAlias(name + " item")) != null) {
			t.setItem(i);
		} else if ((i = getAlias(name + " block")) != null) {
			t.setBlock(i);
		}
		return t;
	}
	
	/**
	 * 
	 * @param name mixedcase string
	 * @param value
	 * @param variations
	 * @return
	 */
	static int addAliases(final String name, final String value, final HashMap<String, HashMap<String, ItemType>> variations) {
		final ItemType t = parseAlias(value);
		if (t == null) {
			Skript.getCurrentErrorSession().printErrors("'" + value + "' is invalid");
			return 0;
		}
		final HashMap<String, ItemType> as = getAliases(name, t, variations);
		boolean printedStartingWithNumberError = false;
		boolean printedSyntaxError = false;
		for (final Entry<String, ItemType> e : as.entrySet()) {
			final String s = e.getKey().trim().replaceAll("\\s+", " ");
			final String lc = s.toLowerCase(Locale.ENGLISH);
			if (lc.matches("\\d+ .*")) {
				if (!printedStartingWithNumberError) {
					Skript.error("aliases must not start with a number");
					printedStartingWithNumberError = true;
				}
				continue;
			}
			if (lc.contains(",") || lc.contains(" and ") || lc.contains(" or ")) {
				if (!printedSyntaxError) {
					Skript.error("aliases must not contain syntax elements (comma, 'and', 'or')");
					printedSyntaxError = true;
				}
				continue;
			}
			aliases.put(lc, e.getValue());
			//if (logSpam()) <- =P
			//	info("added alias " + s + " for " + e.getValue());
			
			if (e.getValue().getTypes().size() == 1) {
				final ItemData d = e.getValue().getTypes().get(0);
				MaterialName n = materialNames.get(Integer.valueOf(d.typeid));
				if (d.dataMin == -1 && d.dataMax == -1) {
					if (n != null) {
						if (n.name.equals("" + d.typeid))
							n.name = s;
						continue;
					}
					materialNames.put(Integer.valueOf(d.typeid), new MaterialName(d.typeid, s));
				} else {
					if (n == null)
						materialNames.put(Integer.valueOf(d.typeid), n = new MaterialName(d.typeid, "" + d.typeid));
					n.names.put(new Pair<Short, Short>(d.dataMin, d.dataMax), s);
				}
			}
		}
		return as.size();
	}
	
	private final static class MaterialName {
		private String name;
		private final int id;
		private final HashMap<Pair<Short, Short>, String> names = new HashMap<Pair<Short, Short>, String>();
		
		public MaterialName(final int id, final String name) {
			this.id = id;
			this.name = name;
		}
		
		public String get(final short dataMin, final short dataMax) {
			if (names == null)
				return name;
			final String s = names.get(new Pair<Short, Short>(dataMin, dataMax));
			if (s != null)
				return s;
			if (dataMin == -1 && dataMax == -1 || dataMin == 0 && dataMax == 0)
				return name;
			return name + ":" + (dataMin == -1 ? 0 : dataMin) + (dataMin == dataMax ? "" : "-" + (dataMax == -1 ? (id <= Skript.MAXBLOCKID ? 15 : Short.MAX_VALUE) : dataMax));
		}
	}
	
	/**
	 * Gets the custom name of of a material, or the default if none is set.
	 * 
	 * @param id
	 * @param data
	 * @return
	 */
	public final static String getMaterialName(final int id, final short data) {
		return getMaterialName(id, data, data);
	}
	
	public final static String getMaterialName(final int id, final short dataMin, final short dataMax) {
		final MaterialName n = materialNames.get(Integer.valueOf(id));
		if (n == null) {
			return "" + id;
		}
		return n.get(dataMin, dataMax);
	}
	
	/**
	 * @return how many ids are missing an alias
	 */
	final static int addMissingMaterialNames() {
		int r = 0;
		final StringBuilder missing = new StringBuilder("There are no aliases defined for the following ids: ");
		for (final Material m : Material.values()) {
			if (materialNames.get(Integer.valueOf(m.getId())) == null) {
				materialNames.put(Integer.valueOf(m.getId()), new MaterialName(m.getId(), m.toString().toLowerCase().replace('_', ' ')));
				missing.append(m.getId() + ", ");
				r++;
			}
		}
		final MaterialName m = materialNames.get(Integer.valueOf(-1));
		if (m == null) {
			materialNames.put(Integer.valueOf(-1), new MaterialName(-1, "anything"));
			missing.append("<any>, ");
			r++;
		}
		if (r > 0)
			Skript.warning(missing.substring(0, missing.length() - 2));
		return r;
	}
	
	/**
	 * Parses an ItemType to be used as an alias, i.e. it doesn't parse 'all'/'every' and the amount.
	 * 
	 * @param s mixed case string
	 * @return
	 */
	public static ItemType parseAlias(final String s) {
		if (s == null || s.isEmpty())
			return null;
		if (s.equals("*"))
			return everything;
		
		final ItemType t = new ItemType();
		
		final String[] types = s.split("\\s*,\\s*");
		for (final String type : types) {
			if (parseType(type, t) == null)
				return null;
		}
		
		return t;
	}
	
	/**
	 * Parses an ItemType
	 * 
	 * @param s
	 * @return The parsed ItemType or null if the input is invalid.
	 */
	public static ItemType parseItemType(String s) {
		if (s == null || s.isEmpty())
			return null;
		final String lc = s.toLowerCase(Locale.ENGLISH);
		if (s.contains(",") || lc.contains(" and ") || lc.contains(" or "))
			return null;
		//			throw new SkriptAPIException("Invalid method call");
		
		final ItemType t = new ItemType();
		
		if (lc.matches("\\d+ of (all|every) .+")) {
			t.amount = Integer.parseInt(s.split(" ", 2)[0]);
			t.all = true;
			s = s.split(" ", 4)[3];
		} else if (lc.matches("\\d+ (of )?.+")) {
			t.amount = Integer.parseInt(s.split(" ", 2)[0]);
			if (s.matches("\\d+ of .+"))
				s = s.split(" ", 3)[2];
			else
				s = s.split(" ", 2)[1];
		} else if (lc.matches("an? .+")) {
			t.amount = 1;
			s = s.split(" ", 2)[1];
		} else if (lc.matches("(all|every) .+")) {
			t.all = true;
			s = s.split(" ", 2)[1];
		}
		
		if (parseType(s, t) == null)
			return null;
		
		if (!t.hasTypes())
			return null;
		
		return t;
	}
	
	/**
	 * 
	 * @param s The string holding the type, can be either a number or an alias, plus an optional data part. Case does not matter.
	 * @param t The ItemType to add the parsed ItemData(s) to (i.e. this ItemType will be modified)
	 * @return The given item type or null if the input couldn't be parsed.
	 */
	private final static ItemType parseType(final String s, final ItemType t) {
		ItemType i;
		int c = s.indexOf(':');
		if (c == -1)
			c = s.length();
		final String type = s.substring(0, c);
		ItemData data = null;
		if (c != s.length()) {
			data = parseData(s.substring(c + 1));
			if (data == null) {
				Skript.error("'" + s.substring(c) + "' is no a valid item data");
				return null;
			}
		}
		if (type.isEmpty()) {
			t.add(data);
			return t;
		} else if (type.matches("\\d+")) {
			ItemData d = new ItemData();
			d.typeid = Integer.parseInt(type);
			if (Material.getMaterial(d.typeid) == null) {
				Skript.error("There doesn't exist a material with id " + d.typeid + "!");
				return null;
			}
			if (data != null) {
				if (d.typeid <= Skript.MAXBLOCKID && (data.dataMax > 15 || data.dataMin > 15)) {
					Skript.error("Blocks only have data values from 0 to 15");
					return null;
				}
				d = d.intersection(data);
			}
			t.add(d);
			return t;
		} else if ((i = getAlias(type, t.amount == 1, t.all)) != null) {
			for (ItemData d : i) {
				if (data != null) {
					if (d.typeid <= Skript.MAXBLOCKID && (data.dataMax > 15 || data.dataMin > 15)) {
						Skript.error("Blocks only have data values from 0 to 15");
						return null;
					}
					d = d.intersection(data);
				}
				t.add(d);
			}
			return t;
		}
		Skript.error("'" + s + "' is neither an id nor an alias");
		return null;
	}
	
	/**
	 * Gets an alias from the aliases defined in the config.
	 * 
	 * @param s The alias to get, case does not matter
	 * @param singular If false plural endings will be stripped
	 * @param ignorePluralCheck Prevents warnings about invalid plural.
	 * @return The ItemType represented by the given alias or null if no such alias exists.
	 */
	private final static ItemType getAlias(String s, final boolean singular, final boolean ignorePluralCheck) {
		ItemType i;
		String lc = s.toLowerCase(Locale.ENGLISH);
		if ((i = getAlias(lc)) != null)
			return i.clone();
		if (lc.startsWith("any ")) {
			return getAlias(s.substring("any ".length()), true, true);
		}
		final Pair<String, Boolean> p = Utils.getPlural(s);
		if (!ignorePluralCheck && !(p.second ^ singular))
			Skript.warning("Possible invalid plural detected in '" + s + "'");
		s = p.first;
		lc = s.toLowerCase(Locale.ENGLISH);
		if (lc.endsWith(" block")) {
			if ((i = getAlias(s.substring(0, s.length() - " block".length()), true, ignorePluralCheck)) != null) {
				for (final ItemData d : i)
					if (d.typeid > Skript.MAXBLOCKID)
						i.remove(d);
				if (!i.iterator().hasNext())
					return null;
				return i;
			}
		} else if (lc.endsWith(" item")) {
			if ((i = getAlias(s.substring(0, s.length() - " item".length()), true, ignorePluralCheck)) != null) {
				for (final ItemData d : i)
					if (d.typeid != -1 && d.typeid <= Skript.MAXBLOCKID)
						i.remove(d);
				if (!i.iterator().hasNext())
					return null;
				return i;
			}
		}
		return getAlias(lc);
	}
	
	/**
	 * gets the data part of an item data
	 * 
	 * @param s Everything after & not including ':'
	 * @return ItemData with only the dataMin and dataMax set
	 */
	private final static ItemData parseData(final String s) {
		if (!s.matches("(\\d+)?(-(\\d+)?)?"))
			return null;
		final ItemData t = new ItemData();
		int i = s.indexOf('-');
		if (i == -1)
			i = s.length();
		t.dataMin = (i == 0 ? -1 : Short.parseShort(s.substring(0, i)));
		t.dataMax = (i == s.length() ? t.dataMin : (i == s.length() - 1 ? -1 : Short.parseShort(s.substring(i + 1, s.length()))));
		if (t.dataMax != -1 && t.dataMax < t.dataMin) {
			Skript.error("the first number of a data range must be smaller than the second");
			return null;
		}
		return t;
	}
	
}
