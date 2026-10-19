package com.apk.axml.aXMLUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AttrValueResolver {

	private AttrValueResolver() {
	}

	private static final class Table {
		final boolean flags;
		final String[] names;
		final int[] values;
		final Map<String, Integer> byName = new HashMap<>();

		Table(boolean flags, String[] names, int[] values) {
			this.flags = flags;
			this.names = names;
			this.values = values;
			for (int i = 0; i < names.length; i++) byName.put(names[i], values[i]);
		}
	}

	private static volatile Map<String, Table> tables;

	private static Map<String, Table> tables() {
		Map<String, Table> loaded = tables;
		if (loaded != null) return loaded;
		synchronized (AttrValueResolver.class) {
			if (tables == null) tables = load();
			return tables;
		}
	}

	private static Map<String, Table> load() {
		Map<String, Table> out = new HashMap<>(512);
		for (String line : AttrValueTables.DATA.split("\n")) {
			if (line.isEmpty()) continue;
			int first = line.indexOf(':');
			if (first <= 0) continue;
			int second = line.indexOf(':', first + 1);
			if (second <= first) continue;
			String attr = line.substring(0, first);
			boolean flags = line.substring(first + 1, second).equals("F");
			String body = line.substring(second + 1);
			List<String> names = new ArrayList<>();
			List<Integer> values = new ArrayList<>();
			for (String pair : body.split(",")) {
				int eq = pair.indexOf('=');
				if (eq <= 0) continue;
				try {
					values.add((int) Long.parseLong(pair.substring(eq + 1)));
					names.add(pair.substring(0, eq));
				} catch (NumberFormatException ignored) {
				}
			}
			if (names.isEmpty()) continue;
			int[] vals = new int[values.size()];
			for (int i = 0; i < vals.length; i++) vals[i] = values.get(i);
			out.put(attr, new Table(flags, names.toArray(new String[0]), vals));
		}
		return out;
	}

	public static boolean isKnown(String attrName) {
		return attrName != null && tables().containsKey(attrName);
	}

	public static String resolve(String attrName, int data) {
		if (attrName == null) return null;
		Table table = tables().get(attrName);
		if (table == null) return null;
		if (!table.flags) {
			for (int i = 0; i < table.values.length; i++) {
				if (table.values[i] == data) return table.names[i];
			}
			return null;
		}
		return resolveFlags(table, data);
	}

	public static Integer parse(String attrName, String text) {
		if (attrName == null || text == null) return null;
		Table table = tables().get(attrName);
		if (table == null) return null;
		String value = text.trim();
		if (value.isEmpty()) return null;
		if (!table.flags) return lookup(table, value);
		int total = 0;
		boolean any = false;
		for (String token : value.split("\\|")) {
			String name = token.trim();
			if (name.isEmpty()) continue;
			Integer bit = lookup(table, name);
			if (bit == null) return null;
			total |= bit;
			any = true;
		}
		return any ? total : null;
	}

	private static Integer lookup(Table table, String name) {
		Integer value = table.byName.get(name);
		if (value != null) return value;
		for (int i = 0; i < table.names.length; i++) {
			if (table.names[i].equalsIgnoreCase(name)) return table.values[i];
		}
		return null;
	}

	private static String resolveFlags(Table table, int data) {
		if (data == 0) {
			for (int i = 0; i < table.values.length; i++) {
				if (table.values[i] == 0) return table.names[i];
			}
			return null;
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < table.values.length; i++) {
			int bit = table.values[i];
			if (bit == 0 || (data & bit) != bit) continue;
			order.add(i);
		}
		Collections.sort(order, new Comparator<Integer>() {
			@Override
			public int compare(Integer a, Integer b) {
				int bitsA = Integer.bitCount(table.values[a]);
				int bitsB = Integer.bitCount(table.values[b]);
				if (bitsA != bitsB) return bitsB - bitsA;
				return a - b;
			}
		});
		List<String> picked = new ArrayList<>();
		if (!cover(table, order, 0, data, picked, 0, new int[]{4000})) return null;
		StringBuilder sb = new StringBuilder();
		for (String name : picked) {
			if (sb.length() > 0) sb.append('|');
			sb.append(name);
		}
		return sb.toString();
	}

	private static boolean cover(Table table, List<Integer> order, int from, int remaining,
								 List<String> picked, int depth, int[] budget) {
		if (remaining == 0) return true;
		if (depth >= 16 || budget[0]-- <= 0) return false;
		for (int i = from; i < order.size(); i++) {
			int index = order.get(i);
			int bit = table.values[index];
			if (bit == 0 || (remaining & bit) != bit) continue;
			picked.add(table.names[index]);
			if (cover(table, order, i + 1, remaining & ~bit, picked, depth + 1, budget)) return true;
			picked.remove(picked.size() - 1);
		}
		return false;
	}
}
