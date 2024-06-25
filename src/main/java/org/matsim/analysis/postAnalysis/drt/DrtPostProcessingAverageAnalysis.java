package org.matsim.analysis.postAnalysis.drt;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalTime;
import java.util.*;

import static org.matsim.application.ApplicationUtils.globFile;

@CommandLine.Command(name = "average-drt", description = "Calculates average drt stats based on several sim runs with different random seeds.")
@CommandSpec(
	requires = {"runs", "mode"},
	produces = {"avg_demand_stats.csv", "avg_supply_stats.csv"}
)
public class DrtPostProcessingAverageAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private InputOptions input = InputOptions.ofCommand(DrtPostProcessingAverageAnalysis.class);
	@CommandLine.Mixin
	private OutputOptions output = OutputOptions.ofCommand(DrtPostProcessingAverageAnalysis.class);
	@CommandLine.Option(names = "--no-runs", defaultValue = "5", description = "Number of simulation runs to be averaged.")
	private Integer noRuns;

	private final Map<String, List<Double>> demandStats = new HashMap<>();
	private final Map<String, List<Double>> supplyStats = new HashMap<>();
	private final Map<String, Double[]> demandAvgs = new HashMap<>();
	private final Map<String, Double[]> supplyAvgs = new HashMap<>();

	private final CsvOptions csv = new CsvOptions();

	String supplyInfo = "info[titleCase]";
	String value = "value";

	public static void main(String[] args) {
		new DrtPostProcessingAverageAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		String runs = input.getPath("runs");

		List<String> foldersSeeded = Arrays.stream(runs.split(",")).toList();

//		add stats from every run to map
		for (String folder : foldersSeeded) {

			String demandKpiCsv = globFile(Path.of(folder + "/analysis/" + input.getPath("mode")), "*demand_kpi.csv*").toString();
			String supplyKpiCsv = globFile(Path.of(folder + "/analysis/" + input.getPath("mode")), "*supply_kpi.csv*").toString();

			Table demand = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(demandKpiCsv))
				.sample(false)
				.separator(CsvOptions.detectDelimiter(demandKpiCsv)).build());

			Table supply = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(supplyKpiCsv))
				.sample(false)
				.separator(CsvOptions.detectDelimiter(supplyKpiCsv)).build());

//			get all demand stats
			for (int i = 0; i < demand.rowCount(); i++) {
				Row row = demand.row(i);

				if (!demandStats.containsKey(row.getString("Info"))) {
					demandStats.put(row.getString("Info"), new ArrayList<>());
				}

//				some values are in format hh:mm:ss or empty
				if (row.getString(value).isEmpty()) {
					demandStats.get(row.getString("Info")).add(0.);
				} else if (row.getString(value).contains(":")) {
					demandStats.get(row.getString("Info")).add((double) LocalTime.parse(row.getString(value)).toSecondOfDay());
				} else {
					demandStats.get(row.getString("Info")).add(Double.parseDouble(row.getString(value)));
				}
			}

//			get all supply stats
			for (int i = 0; i < supply.rowCount(); i++) {
				Row row = supply.row(i);

				if (!supplyStats.containsKey(row.getString(supplyInfo))) {
					supplyStats.put(row.getString(supplyInfo), new ArrayList<>());
				}

				if (row.getColumnType(value) == ColumnType.INTEGER) {
					supplyStats.get(row.getString(supplyInfo)).add((double) row.getInt(value));
				} else {
					supplyStats.get(row.getString(supplyInfo)).add(row.getDouble(value));
				}
			}
		}

		fillAvgMap(demandStats, demandAvgs);
		fillAvgMap(supplyStats, supplyAvgs);

//		ordered list of params to display them in same order as in single-run DrtDashboard
		List<String> orderedDemandParams = List.of("Handled Requests", "Passengers (Pax)", "Avg Group Size", "Pax per veh", "Pax per veh-h", "Pax per veh-km",
			"Rejections", "Rejection rate", "Avg. total travel time", "Avg. in-vehicle time", "Avg. wait time", "95th percentile wait time", "Avg. ride distance [km]",
			"Avg. direct distance [km]", "Avg. fare [MoneyUnit]");
		List<String> orderedSupplyParams = List.of("Number of stops", "Vehicles", "Total vehicle mileage [km]", "Empty ratio", "Total pax distance [km]",
			"Occupancy rate [pax-km/v-km]", "Detour ratio", "Total service hours");

		writeFile("avg_demand_stats.csv", demandAvgs, orderedDemandParams);
		writeFile("avg_supply_stats.csv", supplyAvgs, orderedSupplyParams);

		return 0;
	}

	private void writeFile(String fileName, Map<String, Double[]> values, List<String> orderedParams) throws IOException {
		try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(output.getPath(fileName)), CSVFormat.DEFAULT)) {
			DecimalFormat df = new DecimalFormat("#,###.##", new DecimalFormatSymbols(Locale.US));

//			as of 19.06.24 min and max values are not printed anymore. they will still be calculated though.
//			if one wants to include them to dashboard tables one just has to re-include them in this print method. -sme0624
			printer.printRecord("parameter", "mean", "median", "standard deviation");


			for (String param : orderedParams) {
				printer.printRecord(param, df.format(values.get(param)[0]), df.format(values.get(param)[1]), df.format(values.get(param)[2]));
			}
		}
	}

	private void fillAvgMap(Map<String, List<Double>> source, Map<String, Double[]> destination) {
		for (Map.Entry<String, List<Double>> e: source.entrySet()) {

			String key = e.getKey();
			Double[] values = new Double[5];

			double sum = 0.;

			for (double d : source.get(key)) {
				sum += d;
			}
			double mean = sum / source.get(key).size();

			values[0] = mean;
			values[1] = calcMedian(source.get(key));
			values[2] = calcStandardDeviation(source.get(key), mean);
			values[3] = Collections.min(source.get(key));
			values[4] = Collections.max(source.get(key));

			destination.put(key, values);
		}
	}

	private Double calcStandardDeviation(List<Double> values, double mean) {

		double sumSquaredDiff = 0;
		for (double num : values) {
			sumSquaredDiff += Math.pow(num - mean, 2);
		}

		return Math.sqrt(sumSquaredDiff / values.size());
	}

	private Double calcMedian(List<Double> values) {
		Collections.sort(values);

		int length = values.size();
		// Check if the length of the array is odd or even
		if (length % 2 != 0) {
			// If odd, return the middle element
			return values.get(length / 2);
		} else {
			// If even, return the average of the two middle elements
			int midIndex1 = length / 2 - 1;
			int midIndex2 = length / 2;
			return (values.get(midIndex1) + values.get(midIndex2)) / 2.0;
		}
	}
}

