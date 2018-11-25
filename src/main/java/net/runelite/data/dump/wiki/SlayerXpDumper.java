package net.runelite.data.dump.wiki;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.runelite.cache.NpcManager;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.util.Namer;
import net.runelite.data.App;
import net.runelite.data.dump.MediaWiki;

@Slf4j
public class SlayerXpDumper
{
	public static Optional<Float> checkAndGet(String data, List<String> potentialSearches, List<String> meansInvalid, int id, String name)
	{
		for (String search : potentialSearches) {
			if (data.contains("|"+search))
			{
				Pattern pattern = Pattern.compile(search + ".*=(.*)");
				Matcher matcher = pattern.matcher(data);
				while (matcher.find()) {
					String trimmedMatch = matcher.group(1).trim();
					for (String invalid : meansInvalid) {
						if (trimmedMatch.toLowerCase().equals(invalid))
						{
							return Optional.empty();
						}
					}
					try {
						String withoutCommas = trimmedMatch.replaceAll(",", "");
						float value = Float.parseFloat(withoutCommas);
						return Optional.of(value);
					} catch (NumberFormatException e) {
						log.error("Search for " + search + " for " + id + " " + name + " yielded `" + trimmedMatch + "` which could not be processed");
					}
				}
			}
		}
		return Optional.empty();
	}

	public static String findWithinBalancedBraces(String data)
	{
		StringBuilder inside = new StringBuilder();
		Stack<Character> stack  = new Stack<Character>();
		for(int i = 0; i < data.length(); i++) {
			char c = data.charAt(i);
			if(c == '{' ) {
				stack.push(c);
			} else if(c == '}') {
				if(stack.isEmpty() || stack.pop() != '{') {
					return inside.toString();
				}
				if (stack.isEmpty()) {
					return inside.toString();
				}
			} else {
				inside.append(c);
			}

		}
		return inside.toString();
	}

	public static boolean grab(String pageName, NpcDefinition npc,
							Map<String, List<Float>> xpDrops, final MediaWiki wiki,
							List<String> slayerXpNames, List<String> combatLvlNames,
							List<String> meansInvalid, boolean repeat)
	{
		if (npc.name.equalsIgnoreCase("NULL"))
		{
			return false;
		}

		final String name = Namer
				.removeTags(npc.name)
				.replace('\u00A0', ' ')
				.trim();

		if ((name.isEmpty() || xpDrops.containsKey(name)) && repeat)
		{
			log.info("Skipped " + npc.id + " " + name);
			return false;
		}

		final String data = wiki.getPageData(pageName, -1);

		if (npc.name.equalsIgnoreCase("Red dragon")) {
			System.out.println(data);
		}

		if (Strings.isNullOrEmpty(data))
		{
			return false;
		}

		List<Integer> indices = new ArrayList<>();
		int index = data.indexOf("{{Infobox Monster");
		while (index >= 0) {
			indices.add(index);
			index = data.indexOf("{{Infobox Monster", index + 1);
		}

		List<Float> slayerXps = new ArrayList<>();
		List<Float> combatLevels = new ArrayList<>();
		for (int theIndex : indices) {
			String sub = data.substring(theIndex, data.length());
			String inBraces = findWithinBalancedBraces(sub);
			float slayerXp = checkAndGet(inBraces, slayerXpNames, meansInvalid, npc.id, name).orElse(-1.0f);
			float combatLevel = checkAndGet(inBraces, combatLvlNames, meansInvalid, npc.id, name).orElse(-1.0f);
			slayerXps.add(slayerXp);
			combatLevels.add(combatLevel);
		}

		if (slayerXps.size() <= 0 || combatLevels.size() <= 0)
		{
			boolean oneSuccess = false;
			if (repeat) {
				oneSuccess |= grab(npc.name + " (monster)", npc,
						xpDrops, wiki,
						slayerXpNames, combatLvlNames,
						meansInvalid, false);
				oneSuccess |= grab(npc.name + " (common)", npc,
						xpDrops, wiki,
						slayerXpNames, combatLvlNames,
						meansInvalid, false);
				oneSuccess |= grab(npc.name + " (red)", npc,
						xpDrops, wiki,
						slayerXpNames, combatLvlNames,
						meansInvalid, false);
				oneSuccess |= grab(npc.name + " (green)", npc,
						xpDrops, wiki,
						slayerXpNames, combatLvlNames,
						meansInvalid, false);
				oneSuccess |= grab(npc.name + " (blue)", npc,
						xpDrops, wiki,
						slayerXpNames, combatLvlNames,
						meansInvalid, false);
				oneSuccess |= grab(npc.name + " (black)", npc,
						xpDrops, wiki,
						slayerXpNames, combatLvlNames,
						meansInvalid, false);
				if (!oneSuccess) {
					log.info("Not a killable {} {}", npc.id, name);
				}
			}
			return oneSuccess;
		}

		List<Float> interleaved = new ArrayList<Float>();

		for (int i = 0; i < slayerXps.size(); i++)
		{
			interleaved.add(slayerXps.get(i));
			interleaved.add(combatLevels.get(i));
		}

		if (xpDrops.containsKey(name))
		{
			xpDrops.get(name).addAll(interleaved);
		} else {
			xpDrops.put(name, interleaved);
		}
		log.info("Dumped slayer xp and combat level for {} {} xp is {} ", npc.id, name, interleaved);
		return true;
	}

	public static void dump(final Store store, final MediaWiki wiki) throws IOException
	{
		final File out = new File("runelite/runelite-client/src/main/resources/");
		out.mkdirs();

		log.info("Dumping slayer xp to {}", out);

		NpcManager npcManager = new NpcManager(store);
		npcManager.load();

		final Map<String, List<Float>> xpDrops = new LinkedHashMap<>();

		List<String> slayerXpNames = new ArrayList<>();
		slayerXpNames.add("slayxp");
		slayerXpNames.add("slayexp");

		List<String> combatLvlNames = new ArrayList<>();
		combatLvlNames.add("combat level");
		combatLvlNames.add("combat");
		combatLvlNames.add("cb");
		combatLvlNames.add("level");

		List<String> meansInvalid = new ArrayList<>();
		meansInvalid.add("no");
		meansInvalid.add("not assigned");
		meansInvalid.add("n/a");
		meansInvalid.add("none");

		for (NpcDefinition npc : npcManager.getNpcs()) {
			log.info("{} {}", npc.id, npc.name);
			grab(npc.name, npc,
					 xpDrops, wiki,
			slayerXpNames, combatLvlNames,
					meansInvalid, true);
		}

		try (FileWriter fw = new FileWriter(new File(out, "slayer_xp.json")))
		{
			fw.write(App.GSON.toJson(xpDrops));
		}

		log.info("Dumped {} slayer xp drops", xpDrops.size());
	}
}
