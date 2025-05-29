package org.matsim.analysis;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.*;
	import org.matsim.api.core.v01.events.handler.*;
	import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.vehicles.Vehicle;

import java.util.*;


public class PolicyAnalysis {

	// 设置多段link组成的区域
	private static final Set<Id<Link>> MAIN_ROAD = Set.of(
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
		);
	private static final Set<Id<Link>> OLD_BRIDGE = Set.of(
		Id.createLinkId("-618921346#0"), Id.createLinkId("969758034"),
		Id.createLinkId("828224690"), Id.createLinkId("-23987640"),
		Id.createLinkId("-585581112"), Id.createLinkId("860377873"),Id.createLinkId("860377872"),
		Id.createLinkId("969758024"), Id.createLinkId("969758023"),
		Id.createLinkId("4712334#0"), Id.createLinkId("4712334#1"),
		Id.createLinkId("969758023"), Id.createLinkId("827847899"),
		Id.createLinkId("969758027"), Id.createLinkId("969758026"),
		Id.createLinkId("969758025"), Id.createLinkId("969758024"),
		Id.createLinkId("827847900"), Id.createLinkId("827848756"),
		Id.createLinkId("827847902"),Id.createLinkId("969758036"),
		Id.createLinkId("969758034"), Id.createLinkId("969758033#0"),
		Id.createLinkId("241229718#0"), Id.createLinkId("241229718#1"),
		Id.createLinkId("969758035#0"),Id.createLinkId("828224689"),
		Id.createLinkId("828224688")
		);
	private static final Set<Id<Link>> NEW_BRIDGE = Set.of(
		Id.createLinkId("myNewBridge"),Id.createLinkId("myNewBridgeReverseDirection"));


	private static final double PEAK_START = 7 * 3600;
	private static final double PEAK_END = 9 * 3600;

	public static void main(String[] args) {
		String networkFile = "output/output-kelheim-v3.1-1pct-XDPolicy/kelheim-v3.1-1pct.output_network.xml.gz";
		String baseEventsFile = "output/output-kelheim-v3.1-1pct-XDPolicybaseline/kelheim-v3.1-1pct.output_events.xml.gz";
		String policyEventsFile = "output/output-kelheim-v3.1-1pct-XDPolicy/kelheim-v3.1-1pct.output_events.xml.gz";

		Network network = loadNetwork(networkFile);

		System.out.println("=== Analyzing Base Case ===");
		Result base = analyzeEvents(baseEventsFile, network);
		System.out.println("=== Analyzing Policy Case ===");
		Result policy = analyzeEvents(policyEventsFile, network);

		compareResults(base, policy);
	}

	private static Network loadNetwork(String filename) {
		Network network = org.matsim.core.network.NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(filename);

		// 添加新桥 link，如果它们尚未出现在 network 文件中
		addCustomLinks(network);

		return network;
	}

	private static void addCustomLinks(Network network) {
		Id<Node> nodeId1 = Id.createNodeId("306169197");
		Id<Node> nodeId2 = Id.createNodeId("306169197"); // 注意你这里写的是同一个 node，是否有误？
		Id<Link> linkId1 = Id.createLinkId("myNewBridge");
		Id<Link> linkId2 = Id.createLinkId("myNewBridgeReverseDirection");

		Node fromNode = network.getNodes().get(nodeId1);
		Node toNode = network.getNodes().get(nodeId2);

		if (fromNode == null || toNode == null) {
			throw new IllegalArgumentException("One or both nodes not found in network: " + fromNode + ", " + toNode);
		}

		if (!network.getLinks().containsKey(linkId1)) {
			double length = calculateEuclideanDistance(fromNode.getCoord(), toNode.getCoord());

			var factory = network.getFactory();

			Link forward = factory.createLink(linkId1, fromNode, toNode);
			forward.setLength(length);
			forward.setFreespeed(100.0 / 3.6);
			forward.setCapacity(1200);
			forward.setNumberOfLanes(2);
			forward.setAllowedModes(Set.of("car", "freight"));
			network.addLink(forward);

			Link reverse = factory.createLink(linkId2, toNode, fromNode);
			reverse.setLength(length);
			reverse.setFreespeed(100.0 / 3.6);
			reverse.setCapacity(1200);
			reverse.setNumberOfLanes(2);
			reverse.setAllowedModes(Set.of("car", "freight"));
			network.addLink(reverse);
		}
	}


	private static double calculateEuclideanDistance(Coord c1, Coord c2) {
		double dx = c1.getX() - c2.getX();
		double dy = c1.getY() - c2.getY();
		return Math.sqrt(dx * dx + dy * dy);
	}


	private static Result analyzeEvents(String eventsFile, Network network) {
		EventsManagerImpl manager = new EventsManagerImpl();
		TripAnalyzer analyzer = new TripAnalyzer(network);
		manager.addHandler(analyzer);

		new MatsimEventsReader(manager).readFile(eventsFile);
		return analyzer.getResult();
	}

	private static void compareResults(Result base, Result policy) {
		System.out.println("Affected agents (base): " + base.affectedAgents.size());
		System.out.println("Affected agents (policy): " + policy.affectedAgents.size());

		System.out.println("Avg distance (km): base = " + base.avgDistanceKM() +
			", policy = " + policy.avgDistanceKM());
		System.out.println("Avg travel time (s): base = " + base.avgTime() +
			", policy = " + policy.avgTime());

		for (String key : base.zoneDistance.keySet()) {
			int baseFlow = base.zoneDistance.get(key).size();
			int policyFlow = policy.zoneDistance.getOrDefault(key, List.of()).size();
			System.out.println("Zone " + key + " flow: base = " + baseFlow + ", policy = " + policyFlow);
		}

		System.out.println("Speed Ratio in Peak Hour:");
		for (String key : base.peakTravelTime.keySet()) {
			double baseRatio = base.speedRatio(key);
			double policyRatio = policy.speedRatio(key);
			System.out.println(key + ": base = " + baseRatio + ", policy = " + policyRatio);
		}
	}

	static class Result {
		Set<Id<Person>> affectedAgents = new HashSet<>();
		Map<Id<Person>, Double> personDistance = new HashMap<>();
		Map<Id<Person>, Double> personTime = new HashMap<>();
		Map<String, List<Id<Person>>> zoneDistance = new HashMap<>();
		Map<String, List<Double>> peakTravelTime = new HashMap<>();
		Map<String, List<Double>> peakDistance = new HashMap<>();

		double avgDistanceKM() {
			return personDistance.values().stream().mapToDouble(d -> d).average().orElse(0) / 1000;
		}

		double avgTime() {
			return personTime.values().stream().mapToDouble(t -> t).average().orElse(0);
		}

		double speedRatio(String zone) {
			double dist = peakDistance.getOrDefault(zone, List.of()).stream().mapToDouble(d -> d).sum();
			double time = peakTravelTime.getOrDefault(zone, List.of()).stream().mapToDouble(t -> t).sum();
			return time > 0 ? dist / time : 0;
		}
	}

	static class TripAnalyzer implements LinkEnterEventHandler, PersonDepartureEventHandler, PersonArrivalEventHandler {
		private final Network network;
		private final Result result = new Result();
		private final Map<Id<Person>, Double> departTime = new HashMap<>();
		private final Map<Id<Person>, Double> distance = new HashMap<>();
		private final Map<Id<Person>, Set<String>> personZoneUsage = new HashSetMap<>();
		private final Map<Id<Vehicle>, Id<Person>> vehicleToPerson = new HashMap<Id<org.matsim.vehicles.Vehicle>, Id<Person>>();


		TripAnalyzer(Network network) {
			this.network = network;
		}

		@Override
		public void handleEvent(LinkEnterEvent event) {
			Id<Person> personId = vehicleToPerson.get(event.getVehicleId());
			if (personId == null) return;  // 忽略非 car 模式或未注册的车辆

			Link link = network.getLinks().get(event.getLinkId());
			double len = link.getLength();
			distance.merge(personId, len, Double::sum);

			String zone = getZone(event.getLinkId());
			if (zone != null) {
				result.affectedAgents.add(personId);
				result.zoneDistance.computeIfAbsent(zone, k -> new ArrayList<>()).add(personId);

				if (event.getTime() >= PEAK_START && event.getTime() <= PEAK_END) {
					result.peakDistance.computeIfAbsent(zone, k -> new ArrayList<>()).add(len);
				}

				personZoneUsage.computeIfAbsent(personId, k -> new HashSet<>()).add(zone);
			}

		}

		@Override
		public void handleEvent(PersonDepartureEvent event) {
			if (event.getLegMode().equals(TransportMode.car)) {
				departTime.put(event.getPersonId(), event.getTime());
				vehicleToPerson.put(Id.createVehicleId(event.getPersonId()), event.getPersonId());
			}

		}

		@Override
		public void handleEvent(PersonArrivalEvent event) {
			if (!departTime.containsKey(event.getPersonId())) return;
			double duration = event.getTime() - departTime.remove(event.getPersonId());
			result.personTime.put(event.getPersonId(), duration);
			result.personDistance.put(event.getPersonId(), distance.getOrDefault(event.getPersonId(), 0.0));

			for (String zone : personZoneUsage.getOrDefault(event.getPersonId(), Set.of())) {
				if (event.getTime() >= PEAK_START && event.getTime() <= PEAK_END) {
					result.peakTravelTime.computeIfAbsent(zone, k -> new ArrayList<>()).add(duration);
				}
			}
			distance.remove(event.getPersonId());
		}

		public Result getResult() {
			return result;
		}

		private String getZone(Id<Link> id) {
			if (MAIN_ROAD.contains(id)) return "main";
			if (OLD_BRIDGE.contains(id)) return "old";
			if (NEW_BRIDGE.contains(id)) return "new";
			return null;
		}
	}

	// 简单 HashSet Map 用于 map<key, set<value>>
	static class HashSetMap<K, V> extends HashMap<K, Set<V>> {
		public Set<V> computeIfAbsent(K key, java.util.function.Function<? super K, ? extends Set<V>> mappingFunction) {
			return super.computeIfAbsent(key, mappingFunction);
		}
	}
}

