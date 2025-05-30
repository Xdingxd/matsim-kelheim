package org.matsim.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class MyAnalysis {

	private static final Set<Id<Link>> MAIN_STREET_LINKS = new HashSet<>(Arrays.asList(
		Id.createLinkId("-487456219#0"), Id.createLinkId("-487456219#1"),
		Id.createLinkId("-487456219#2"), Id.createLinkId("-487456219#3"),
		Id.createLinkId("-4712335#5"), Id.createLinkId("-4712335#4"),
		Id.createLinkId("-4712335#3"), Id.createLinkId("-4712335#2"),
		Id.createLinkId("-4712335#1"), Id.createLinkId("-4712335#0"),
		Id.createLinkId("827847899"), Id.createLinkId("4712334#0"),
		Id.createLinkId("4712334#1"), Id.createLinkId("827847901"),
		Id.createLinkId("-487455692#6"), Id.createLinkId("-487455692#5"),
		Id.createLinkId("-487455692#3"), Id.createLinkId("-487455692#2"),
		Id.createLinkId("-487455692#0"), Id.createLinkId("-202895446#11"),
		Id.createLinkId("-202895446#10"), Id.createLinkId("-202895446#8"),
		Id.createLinkId("-202895446#7"), Id.createLinkId("-202895446#6"),
		Id.createLinkId("-202895446#5"), Id.createLinkId("-202895446#4"),
		Id.createLinkId("-202895446#3"), Id.createLinkId("-202895446#0")
	));

	private static final Set<Id<Link>> BRIDGE_MIDDLE_LINKS = new HashSet<>(Arrays.asList(
		Id.createLinkId("-618921346#0"), Id.createLinkId("969758034"),
		Id.createLinkId("828224690"), Id.createLinkId("-23987640"),
		Id.createLinkId("-585581112"), Id.createLinkId("860377873"), Id.createLinkId("860377872"),
		Id.createLinkId("969758024"), Id.createLinkId("969758023"),
		Id.createLinkId("4712334#0"), Id.createLinkId("4712334#1"),
		Id.createLinkId("969758023"), Id.createLinkId("827847899"),
		Id.createLinkId("969758027"), Id.createLinkId("969758026"),
		Id.createLinkId("969758025"), Id.createLinkId("969758024"),
		Id.createLinkId("827847900"), Id.createLinkId("827848756"),
		Id.createLinkId("827847902"), Id.createLinkId("969758036"),
		Id.createLinkId("969758034"), Id.createLinkId("969758033#0"),
		Id.createLinkId("241229718#0"), Id.createLinkId("241229718#1"),
		Id.createLinkId("969758035#0"), Id.createLinkId("828224689"),
		Id.createLinkId("828224688")
	));

	private static final Set<Id<Link>> NEW_BRIDGE_LINKS = new HashSet<>(Arrays.asList(
		Id.createLinkId("myNewBridge"), Id.createLinkId("myNewBridgeReverseDirection")
	));


	private static final int TIME_BIN_SIZE = 900;
	private static final int MAX_TIME_BINS = 96;

	public static void main(String[] args) throws Exception {
		String networkFile = "path/to/network.xml.gz";
		String baselineEvents = "path/to/baseline.events.xml.gz";
		String policyEvents = "path/to/policy.events.xml.gz";
		String outputFile = "output/congestion_comparison.csv";

		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);


		CongestionAnalysisResult baselineResult = analyzeCongestion(baselineEvents, network);

		CongestionAnalysisResult policyResult = analyzeCongestion(policyEvents, network);


		saveComparisonResults(baselineResult, policyResult, outputFile);

		System.out.println("Congestion comparison completed. Results saved to: " + outputFile);
	}

	private static CongestionAnalysisResult analyzeCongestion(String eventsFile, Network network) {
		EventsManager eventsManager = EventsUtils.createEventsManager();
		CongestionAnalysisHandler handler = new CongestionAnalysisHandler(network);
		eventsManager.addHandler(handler);
		new MatsimEventsReader(eventsManager).readFile(eventsFile);
		return handler.getResult();
	}

	public static void saveComparisonResults(CongestionAnalysisResult baseline,
											 CongestionAnalysisResult policy,
											 String outputPath) throws Exception {
		try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {

			writer.println("TimeBin,SectionType,BaseAvgTT,PolicyAvgTT,TTChangePercent," +
				"BaseAvgSpeed,PolicyAvgSpeed,SpeedChangePercent," +
				"BaseSpeedRatio,PolicySpeedRatio,SpeedRatioChange," +
				"BaseCongestion,PolicyCongestion,CongestionChange");


			for (int timeBin = 0; timeBin < MAX_TIME_BINS; timeBin++) {

				writeSectionComparison(writer, timeBin, "MainStreet",
					baseline.mainStreetStats.get(timeBin),
					policy.mainStreetStats.get(timeBin));


				writeSectionComparison(writer, timeBin, "BridgeMiddle",
					baseline.bridgeMiddleStats.get(timeBin),
					policy.bridgeMiddleStats.get(timeBin));


				writeSectionComparison(writer, timeBin, "NewBridge",
					baseline.newBridgeStats.get(timeBin),
					policy.newBridgeStats.get(timeBin));
			}
		}
	}

	private static void writeSectionComparison(PrintWriter writer, int timeBin, String sectionType,
											   TimeBinStats baseStats, TimeBinStats policyStats) {

		if (baseStats == null || policyStats == null ||
			baseStats.vehicleCount == 0 || policyStats.vehicleCount == 0) {
			return;
		}

		//
		double ttChangePercent = calculateChangePercent(baseStats.avgTravelTime, policyStats.avgTravelTime);
		double speedChangePercent = calculateChangePercent(baseStats.avgSpeed, policyStats.avgSpeed);
		double speedRatioChange = policyStats.avgSpeedRatio - baseStats.avgSpeedRatio;

		//
		String congestionChange = determineCongestionChange(baseStats.congestionLevel, policyStats.congestionLevel);

		//
		writer.printf("%d,%s,%.2f,%.2f,%.2f%%,%.2f,%.2f,%.2f%%,%.3f,%.3f,%.3f,%s,%s,%s%n",
			timeBin,
			sectionType,
			baseStats.avgTravelTime,
			policyStats.avgTravelTime,
			ttChangePercent,
			baseStats.avgSpeed,
			policyStats.avgSpeed,
			speedChangePercent,
			baseStats.avgSpeedRatio,
			policyStats.avgSpeedRatio,
			speedRatioChange,
			baseStats.congestionLevel,
			policyStats.congestionLevel,
			congestionChange);
	}

	private static double calculateChangePercent(double baseValue, double policyValue) {
		if (baseValue == 0) return 0.0;
		return ((policyValue - baseValue) / baseValue) * 100;
	}

	private static String determineCongestionChange(String baseLevel, String policyLevel) {
		if (baseLevel.equals(policyLevel)) return "NoChange";

		//
		Map<String, Integer> levels = Map.of(
			"FreeFlow", 0,
			"Light", 1,
			"Moderate", 2,
			"Heavy", 3,
			"Severe", 4
		);

		int baseScore = levels.getOrDefault(baseLevel, 0);
		int policyScore = levels.getOrDefault(policyLevel, 0);

		return baseScore < policyScore ? "Worsened" : "Improved";
	}

	static class CongestionAnalysisHandler implements LinkEnterEventHandler, LinkLeaveEventHandler {
		private final Network network;
		private final Map<Id<org.matsim.vehicles.Vehicle>, Double> linkEnterTimes = new HashMap<Id<org.matsim.vehicles.Vehicle>, Double>();
		private final CongestionAnalysisResult result = new CongestionAnalysisResult();

		//
		private final Map<Id<Link>, LinkInfo> linkInfoMap = new HashMap<>();

		public CongestionAnalysisHandler(Network network) {
			this.network = network;
			initializeLinkInfo();
		}

		private void initializeLinkInfo() {
			//
			for (Id<Link> linkId : MAIN_STREET_LINKS) {
				Link link = network.getLinks().get(linkId);
				if (link != null) {
					linkInfoMap.put(linkId, new LinkInfo(link, SectionType.MAIN_STREET));
				}
			}

			//
			for (Id<Link> linkId : BRIDGE_MIDDLE_LINKS) {
				Link link = network.getLinks().get(linkId);
				if (link != null) {
					linkInfoMap.put(linkId, new LinkInfo(link, SectionType.BRIDGE_MIDDLE));
				}
			}

			//
			for (Id<Link> linkId : NEW_BRIDGE_LINKS) {
				Link link = network.getLinks().get(linkId);
				if (link != null) {
					linkInfoMap.put(linkId, new LinkInfo(link, SectionType.NEW_BRIDGE));
				}
			}
		}

		@Override
		public void handleEvent(LinkEnterEvent event) {
			//
			if (linkInfoMap.containsKey(event.getLinkId())) {
				linkEnterTimes.put(event.getVehicleId(), event.getTime());
			}
		}

		@Override
		public void handleEvent(LinkLeaveEvent event) {
			Double enterTime = linkEnterTimes.remove(event.getVehicleId());
			if (enterTime == null) return;

			Id<Link> linkId = event.getLinkId();
			LinkInfo linkInfo = linkInfoMap.get(linkId);
			if (linkInfo == null) return;

			double travelTime = event.getTime() - enterTime;
			double length = linkInfo.length;
			double actualSpeed = length / travelTime;
			double freeSpeed = linkInfo.freeSpeed;
			double speedRatio = actualSpeed / freeSpeed;
			String congestionLevel = determineCongestionLevel(speedRatio);

			int timeBin = (int) (event.getTime() / TIME_BIN_SIZE);
			if (timeBin >= MAX_TIME_BINS) return;

			result.updateStats(linkInfo.sectionType, timeBin, travelTime, actualSpeed, speedRatio, congestionLevel);
		}

		private String determineCongestionLevel(double speedRatio) {
			if (speedRatio >= 0.8) return "FreeFlow";
			else if (speedRatio >= 0.6) return "Light";
			else if (speedRatio >= 0.4) return "Moderate";
			else if (speedRatio >= 0.2) return "Heavy";
			else return "Severe";
		}

		public CongestionAnalysisResult getResult() {
			result.finalizeStats();
			return result;
		}
	}

	static class CongestionAnalysisResult {

		public final Map<Integer, TimeBinStats> mainStreetStats = new HashMap<>();
		public final Map<Integer, TimeBinStats> bridgeMiddleStats = new HashMap<>();
		public final Map<Integer, TimeBinStats> newBridgeStats = new HashMap<>();

		public void updateStats(SectionType sectionType, int timeBin,
								double travelTime, double speed,
								double speedRatio, String congestionLevel) {
			Map<Integer, TimeBinStats> targetMap = getTargetMap(sectionType);

			TimeBinStats stats = targetMap.computeIfAbsent(timeBin, k -> new TimeBinStats());
			stats.totalTravelTime += travelTime;
			stats.totalSpeed += speed;
			stats.totalSpeedRatio += speedRatio;
			stats.vehicleCount++;

			stats.congestionDistribution.merge(congestionLevel, 1, Integer::sum);
		}

		private Map<Integer, TimeBinStats> getTargetMap(SectionType sectionType) {
			switch (sectionType) {
				case MAIN_STREET: return mainStreetStats;
				case BRIDGE_MIDDLE: return bridgeMiddleStats;
				case NEW_BRIDGE: return newBridgeStats;
				default: throw new IllegalArgumentException("Unknown section type");
			}
		}

		public void finalizeStats() {

			finalizeSectionStats(mainStreetStats);
			finalizeSectionStats(bridgeMiddleStats);
			finalizeSectionStats(newBridgeStats);
		}

		private void finalizeSectionStats(Map<Integer, TimeBinStats> statsMap) {
			for (TimeBinStats stats : statsMap.values()) {
				if (stats.vehicleCount > 0) {
					stats.avgTravelTime = stats.totalTravelTime / stats.vehicleCount;
					stats.avgSpeed = stats.totalSpeed / stats.vehicleCount;
					stats.avgSpeedRatio = stats.totalSpeedRatio / stats.vehicleCount;
					stats.congestionLevel = determineDominantCongestion(stats.congestionDistribution);
				}
			}
		}

		private String determineDominantCongestion(Map<String, Integer> distribution) {
			return distribution.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse("NoData");
		}
	}

	static class TimeBinStats {
		double totalTravelTime = 0;
		double totalSpeed = 0;
		double totalSpeedRatio = 0;
		int vehicleCount = 0;

		double avgTravelTime = 0;
		double avgSpeed = 0;
		double avgSpeedRatio = 0;
		String congestionLevel = "NoData";

		Map<String, Integer> congestionDistribution = new HashMap<>();
	}

	static class LinkInfo {
		final double length;
		final double freeSpeed;
		final SectionType sectionType;

		LinkInfo(Link link, SectionType sectionType) {
			this.length = link.getLength();
			this.freeSpeed = link.getFreespeed();
			this.sectionType = sectionType;
		}
	}

	enum SectionType {
		MAIN_STREET, BRIDGE_MIDDLE, NEW_BRIDGE
	}

}
