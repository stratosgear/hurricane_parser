package net.gerakakis.carbonpoint.hurricane;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;

public class Parser {

	@Parameter(names = { "-v", "--verbose" }, description = "Level of verbosity (max:2)")
	private Integer verbose = 0;

	@Parameter(names = { "-y", "--year" }, description = "Year to lookup")
	private Integer lookupYear = 2009;

	@Parameter(names = { "-f", "--file" }, description = "Filename to read data from", required = true, converter = FileConverter.class)
	private File inputFile;

	private boolean yearFound = false;

	/*
	 * Helper class to hold all datalines for a hurricane.
	 */
	class Hurricane {
		private String name;
		private ArrayList<String> data = new ArrayList<String>();

		public Hurricane(String n) {
			name = n;
		}

		public String getName() {
			return name;
		}

		public ArrayList<String> getData() {
			return data;
		}

		public void addDataLine(String line) {
			data.add(line);
		}
	}

	/**
	 * Regex expression to parse a header line.
	 */
	Pattern headerRE = Pattern.compile("(EP|CP)\\d{2}(\\d{4}),\\s*(.*)\\s*,\\s*(\\d*),");

	/**
	 * Regex expression to parse a dataline. If we needed to calculate or parse more info out of the hurricane data, we
	 * would need to expand the regular expression to include all fields. As of now, the matcher will match only up to
	 * the max wind speed column.
	 */
	Pattern dataRE = Pattern
			.compile("\\d{4}\\d{2}\\d{2}\\s*,\\s*(\\d{4})\\s*,\\s*[LPIST]?\\s*,\\s*(TD|TS|HU|EX|SD|SS|LO|DB)?\\s*,\\s*[-+]?[0-9]*\\.?[0-9]+[NS]\\s*,\\s*[-+]?[0-9]*\\.?[0-9]+[EW]\\s*,\\s*(\\d*)");

	/**
	 * Where all the reading and parsing of the inputFile takes place.
	 */
	public void run() {

		print("Asked to print hurricane info for year %s.", lookupYear);

		BufferedReader br;
		try {
			if (verbose == 2)
				print("Verbose2: Opening file %s", inputFile.getAbsoluteFile());

			br = new BufferedReader(new FileReader(inputFile));

			// A line out of the input file.
			String line;

			// number of datalines for this hurricane
			int dataLines = 0;

			// The currently parsed hurricane.
			Hurricane hur = null;

			// The line number that we're currently parsing
			int lineNumber = 0;

			while ((line = br.readLine()) != null) {
				lineNumber++;

				// if we're out of datalines, then we oughta parse a header line.
				if (dataLines == 0) {

					Matcher m = headerRE.matcher(line);

					// if we match a header line, it means the previous hurricane data are complete
					if (m.matches()) {

						// if the hurricane object is valid (meaning, in the year we're looking for) then parse it.
						if (hur != null)
							parseHurricane(hur, lineNumber);

						// if the year is in the year we're interested in...
						if (Integer.parseInt(m.group(2)) == lookupYear) {
							// instantiate a new hurricane object
							hur = new Hurricane(m.group(3));
						} else {
							// otherwise, ignore it (marking it as null)

							if (verbose != 0)
								print("Verbose1: Ignoring hurricane %s of %s", m.group(3), m.group(2));

							hur = null;
						}
						// store how many datalines we should capture next.
						dataLines = Integer.parseInt(m.group(4));

					} else {
						// if we don't match a header line, either the line is wrong or the regex (investigate and fix)
						parseError("header", lineNumber, line);
					}
				} else {
					// read one more data line
					dataLines--;
					// if we care about this hurricane
					if (hur != null) {
						// capture the dataline
						hur.addDataLine(line);
					}
				}
			}
			// close the file; The program will exit anyways, so no need for complex resource handling.
			br.close();

			if (!yearFound) {
				print("No hurricanes found for the requested year.");
			}
		} catch (FileNotFoundException e) {
			print("Data file %s not found.", inputFile.getAbsolutePath());
		} catch (IOException e) {
			print("I/O error while reading data from datafile.");
		}
	}

	/**
	 * Helper method to save us some typing.
	 * 
	 * @param format
	 *            String.format pattern.
	 * @param args
	 *            String.format arguments to apply.
	 */
	private void print(String format, Object... args) {
		System.out.println(String.format(format, args));
	}

	/**
	 * Helper method to save us some typing.
	 * 
	 * @param str
	 *            The string to print.
	 */
	private void print(String str) {
		System.out.println(str);
	}

	/**
	 * Called to parse specific info out from a hurricane.
	 * 
	 * @param h
	 *            The Hurricane object containing all the hurricane data.
	 * @param hurricaneLineNumber
	 *            The line number in the dataFile where the hurricane data (header line and datalines) are starting.
	 */
	private void parseHurricane(Hurricane h, int hurricaneLineNumber) {

		int maxSpeed = 0;
		int lineNumber = 0;

		// iterate through all the data lines, looking for the max wind speed.
		for (String line : h.getData()) {
			lineNumber++;
			Matcher m = dataRE.matcher(line);
			if (m.lookingAt()) {

				int speed = Integer.parseInt(m.group(3));
				if (verbose == 2)
					print("Verbose2: Wind speed at %s o'clock was at %.2f km/h", m.group(1), speed * 1.852);

				maxSpeed = speed > maxSpeed ? speed : maxSpeed;
			} else {
				parseError("data", hurricaneLineNumber + lineNumber, line);
			}
		}
		// convert knots to km/h
		double kmh = maxSpeed * 1.852;

		print("Hurricane %s with max speed of %.2f km/h.", h.getName(), kmh);
		yearFound = true;
	}

	/**
	 * Called when the parsing of a line from the dataFile, fails to be parsed correctly.
	 * 
	 * @param location
	 *            Where the error occurred (header or data lines)
	 * @param lineNumber
	 *            The line number in the datafile where the error occurred.
	 * @param line
	 *            The offending line.
	 */
	private void parseError(String location, int lineNumber, String line) {
		print("Parse error while reading a %s line.", location);
		print("Offending line #%d: %s", lineNumber, line);
		System.exit(-1);
	}

	public static void main(String[] args) {

		// Initialize our hurricane datafile parser
		Parser p = new Parser();

		// Initialize the command line parser.
		JCommander jc = new JCommander(p);
		jc.setProgramName("Parser");
		try {
			jc.parse(args);
		} catch (Exception e) {
			// more likely there is a required parameter missing.
			jc.usage();
			System.exit(-1);
		}

		p.run();
	}
}