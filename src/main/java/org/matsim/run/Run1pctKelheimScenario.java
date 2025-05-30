package org.matsim.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.common.collect.Sets;
import org.matsim.analysis.KelheimMainModeIdentifier;
import org.matsim.analysis.ModeChoiceCoverageControlerListener;
import org.matsim.analysis.personMoney.PersonMoneyEventsAnalysisModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.*;
import org.matsim.contrib.vsp.pt.fare.DistanceBasedPtFareParams;
import org.matsim.contrib.vsp.pt.fare.PtFareConfigGroup;
import org.matsim.contrib.vsp.pt.fare.PtFareModule;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controller;
import org.matsim.core.controler.ControllerUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.annealing.ReplanningAnnealerConfigGroup;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import java.util.*;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.Coord;

import java.util.Set;

public class Run1pctKelheimScenario {
	private static final double SAMPLE = 0.01;

	public static void main(String[] args) {
		// ======= Load & adapt config =======
		String configPath = "input/v3.1/kelheim-v3.1-config.xml";
		Config config = ConfigUtils.loadConfig(configPath);

		SnzActivities.addScoringParams(config);

		config.controller().setOutputDirectory("./output/output-kelheim-v3.1-1pct-XDPolicy");
		config.plans().setInputFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/kelheim/kelheim-v3.0/input/kelheim-v3.0-1pct-plans.xml.gz");
		config.controller().setRunId("kelheim-v3.1-1pct");

		config.qsim().setFlowCapFactor(SAMPLE);
		config.qsim().setStorageCapFactor(SAMPLE);

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort);
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);

		config.global().setRandomSeed(4711);

		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);

		// Relative to config
		sw.defaultParams().shp = "../shp/dilutionArea.shp";
		sw.defaultParams().mapCenter = "11.89,48.91";
		sw.defaultParams().mapZoomLevel = 11d;
		sw.sampleSize = SAMPLE;

		PtFareConfigGroup ptFareConfigGroup = ConfigUtils.addOrGetModule(config, PtFareConfigGroup.class);
		DistanceBasedPtFareParams distanceBasedPtFareParams = ConfigUtils.addOrGetModule(config, DistanceBasedPtFareParams.class);

		// Set parameters
		ptFareConfigGroup.setApplyUpperBound(true);
		ptFareConfigGroup.setUpperBoundFactor(1.5);

		// Minimum fare (e.g. short trip or 1 zone ticket)
		distanceBasedPtFareParams.setMinFare(2.0);

		distanceBasedPtFareParams.setTransactionPartner("pt-operator");
		DistanceBasedPtFareParams.DistanceClassLinearFareFunctionParams shortDistance = distanceBasedPtFareParams.getOrCreateDistanceClassFareParams(50000);
		shortDistance.setFareIntercept(1.6);
		shortDistance.setFareSlope(0.00017);

		DistanceBasedPtFareParams.DistanceClassLinearFareFunctionParams longDistance = distanceBasedPtFareParams.getOrCreateDistanceClassFareParams(Double.POSITIVE_INFINITY);
		longDistance.setFareIntercept(30);
		longDistance.setFareSlope(0.00025);
		distanceBasedPtFareParams.setOrder(1);

		ptFareConfigGroup.addParameterSet(distanceBasedPtFareParams);

		//enable plan inheritance analysis
		config.planInheritance().setEnabled(true);

		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

		// ======= Load & adapt scenario =======
		Scenario scenario = ScenarioUtils.loadScenario(config);

		for (Link link : scenario.getNetwork().getLinks().values()) {
			Set<String> modes = link.getAllowedModes();

			// allow freight traffic together with cars
			if (modes.contains("car")) {
				Set<String> newModes = Sets.newHashSet(modes);
				newModes.add("freight");

				link.setAllowedModes(newModes);
			}
		}
		// === Add changes to network - XD policy ===
		addBridge(scenario.getNetwork());
		ModifyMainStreetLinks(scenario.getNetwork());
		ModifyBridgeLinks(scenario.getNetwork());

		new NetworkWriter(scenario.getNetwork()).write("output/output-kelheim-v3.1-1pct-XDPolicy/kelheim-v3.1-1pct.output_network.xml.gz");
		// ======= Load & adapt controller ======
		Controller controller = ControllerUtils.createController(scenario);

		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new PtFareModule());
				install(new SwissRailRaptorModule());
				install(new PersonMoneyEventsAnalysisModule());
				install(new SimWrapperModule());

				bind(AnalysisMainModeIdentifier.class).to(KelheimMainModeIdentifier.class);
				addControlerListenerBinding().to(ModeChoiceCoverageControlerListener.class);

				//use income-dependent marginal utility of money
				bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).asEagerSingleton();
			}
		});
		controller.run();
	}

	public static void addBridge(Network network) {
		NetworkFactory factory = network.getFactory();

		Id<Node> nodeId1 = Id.createNodeId("293670680");
		Id<Node> nodeId2 = Id.createNodeId("306169197");
		Id<Link> linkId1 = Id.createLinkId("myNewBridge");
		Id<Link> linkId2 = Id.createLinkId("myNewBridgeReverseDirection");

		// 假设这两个节点已存在
		Node fromNode = network.getNodes().get(nodeId1);
		Node toNode = network.getNodes().get(nodeId2);
		if (fromNode == null || toNode == null) {
			throw new IllegalArgumentException("One or both nodes not found in network: " + fromNode + ", " + toNode);
		}
		double length = calculateEuclideanDistance(fromNode.getCoord(), toNode.getCoord());
		// 正向 Link
		Link forward = factory.createLink(linkId1, fromNode, toNode);
		forward.setLength(length);
		forward.setFreespeed(100.0/3.6); // m/s (~54km/h)
		forward.setCapacity(1200);
		forward.setNumberOfLanes(2);
		forward.setAllowedModes(Set.of("car", "freight"));
		network.addLink(forward);

		// 反向 Link
		Link reverse = factory.createLink(linkId2, toNode, fromNode);
		reverse.setLength(length);
		reverse.setFreespeed(100.0/3.6);
		reverse.setCapacity(1200);
		reverse.setNumberOfLanes(2);
		reverse.setAllowedModes(Set.of("car", "freight"));
		network.addLink(reverse);

	}
	private static double calculateEuclideanDistance(Coord c1, Coord c2) {
		double dx = c1.getX() - c2.getX();
		double dy = c1.getY() - c2.getY();
		return Math.sqrt(dx * dx + dy * dy);
	}
	public static void ModifyMainStreetLinks(Network network) {
		List<Id<Link>> mainStreetLinkIds = Arrays.asList(
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

		for (Id<Link> linkId : mainStreetLinkIds) {
			Link link = network.getLinks().get(linkId);
			if (link != null) {
				link.setCapacity(1200); // double capacity
				link.setFreespeed(100/3.6); // increase speed
				link.setNumberOfLanes(2);
			}
		}
	}

	public static void ModifyBridgeLinks(Network network) {
		List<Id<Link>> bridgeLinkIds = Arrays.asList(
			Id.createLinkId("-618921346#0"), Id.createLinkId("969758034"),
			Id.createLinkId("828224690"), Id.createLinkId("-23987640"),
			Id.createLinkId("-585581112"), Id.createLinkId("860377873"),
			Id.createLinkId("860377872"), Id.createLinkId("969758024"),
			Id.createLinkId("969758023"), Id.createLinkId("4712334#0"),
			Id.createLinkId("4712334#1"), Id.createLinkId("969758023"),
			Id.createLinkId("827847899"), Id.createLinkId("969758027"),
			Id.createLinkId("969758026"), Id.createLinkId("969758025"),
			Id.createLinkId("969758024"), Id.createLinkId("827847900"),
			Id.createLinkId("827848756"), Id.createLinkId("827847902"),
			Id.createLinkId("969758036"), Id.createLinkId("969758034"),
			Id.createLinkId("969758033#0"), Id.createLinkId("241229718#0"),
			Id.createLinkId("241229718#1"), Id.createLinkId("969758035#0"),
			Id.createLinkId("828224689"), Id.createLinkId("828224688")
		);

		for (Id<Link> linkId : bridgeLinkIds) {
			Link link = network.getLinks().get(linkId);
			if (link != null) {
				link.setCapacity(link.getCapacity() * 0.5); // halve capacity
				link.setFreespeed(link.getFreespeed() * 0.8); // reduce speed
			}
		}
	}
}



// 添加一条桥梁连接两个已知 node

