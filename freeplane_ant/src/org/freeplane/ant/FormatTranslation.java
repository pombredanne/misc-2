/**
 * FormatTranslation.java
 *
 * Copyright (C) 2010,  Volker Boerchers
 *
 * FormatTranslation.java is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * FormatTranslation.java is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package org.freeplane.ant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/** formats a translation file and writes the result to another file.
 * The following transformations are made:
 * <ol>
 * <li> sort lines (case insensitive)
 * <li> remove duplicates
 * <li> if a key is present multiple times entries marked as [translate me]
 *      and [auto] are removed in favor of normal entries.
 * <li> newline style is changed to the platform default.
 * </ol>
 * 
 * Attributes:
 * <ul>
 * <li> dir: the input directory (default: ".")
 * <li> outputDir: the output directory. Overwrites existing files if outputDir
 *      equals the input directory (default: the input directory)
 * <li> includes: wildcard pattern (default: all regular files).
 * <li> excludes: wildcard pattern, overrules includes (default: no excludes).
 * </ul>
 * 
 * Build messages:
 * <table border=1>
 * <tr><th>Message</th><th>Action</th><th>Description</th></tr>
 * <tr><td>&lt;file&gt;: no key/val: &lt;line&gt;</td><td>drop line</td><td>broken line with an empty key or without an '=' sign</td></tr>
 * <tr><td>&lt;file&gt;: drop duplicate: &lt;line&gt;</td><td>drop line</td><td>two completely identical lines</td></tr>
 * <tr><td>&lt;file&gt;: drop: &lt;line&gt;</td><td>drop line</td>
 *     <td>this translation is dropped since a better one was found
 *        (quality: [translate me] -> [auto] -> manually translated)
 *     </td>
 * </tr>
 * <tr><td>&lt;file&gt;: drop one of two of equal quality (revisit!):keep: &lt;line&gt;</td><td>keep line</td>
 *     <td>for one key two manual translations were found. This one (arbitrarily chosen) will be kept.
 *         Printout of the complete line allows to correct an action of FormatTranslation via Copy and Past
 *         if it chose the wrong tranlation.
 *     </td>
 * </tr>
 * <tr><td>&lt;file&gt;: drop one of two of equal quality (revisit!):drop: &lt;line&gt;</td><td>drop line</td>
 *     <td>accompanies the :keep: line: This is the line that is dropped.
 *     </td>
 * </tr>
 * </table>
 * Note that CheckTranslation does not remove anything but produces the same messages!
 */
public class FormatTranslation extends Task {
	static Comparator<String> KEY_COMPARATOR = new Comparator<String>() {
		public int compare(String s1, String s2) {
			int n1 = s1.length(), n2 = s2.length();
			for (int i1 = 0, i2 = 0; i1 < n1 && i2 < n2; i1++, i2++) {
				char c1 = s1.charAt(i1);
				char c2 = s2.charAt(i2);
				boolean c1Terminated = c1 == ' ' || c1 == '\t' || c1 == '=';
				boolean c2Terminated = c2 == ' ' || c2 == '\t' || c2 == '=';
				if (c1Terminated && c2Terminated)
					return 0;
				if (c1Terminated && !c2Terminated)
					return -1;
				if (c2Terminated && !c1Terminated)
					return 1;
				if (c1 != c2) {
					c1 = Character.toUpperCase(c1);
					c2 = Character.toUpperCase(c2);
					if (c1 != c2) {
						c1 = Character.toLowerCase(c1);
						c2 = Character.toLowerCase(c2);
						if (c1 != c2) {
							return c1 - c2;
						}
					}
				}
			}
			return n1 - n2;
		}
	};
	private final static int QUALITY_NULL = 0; // for empty values
	private final static int QUALITY_TRANSLATE_ME = 1;
	private final static int QUALITY_AUTO_TRANSLATED = 2;
	private final static int QUALITY_MANUALLY_TRANSLATED = 3;
	private File outputDir;
	private boolean writeIfUnchanged = false;
	private File inputDir = new File(".");
	private ArrayList<Pattern> includePatterns = new ArrayList<Pattern>();
	private ArrayList<Pattern> excludePatterns = new ArrayList<Pattern>();
	private String lineSeparator = System.getProperty("line.separator");

	public void execute() {
		final int countFormatted = executeImpl(false);
		log("formatted " + countFormatted + " files");
	}

	public int checkOnly() {
		return executeImpl(true);
	}

	/** returns the number of unformatted files. */
	private int executeImpl(boolean checkOnly) {
		validate();
		File[] inputFiles = inputDir
		    .listFiles(new TranslationUtils.IncludeFileFilter(includePatterns, excludePatterns));
		try {
			int countFormattingRequired = 0;
			for (int i = 0; i < inputFiles.length; i++) {
				File inputFile = inputFiles[i];
				log("processing " + inputFile + "...", Project.MSG_DEBUG);
				String[] lines = TranslationUtils.readLines(inputFile);
				String[] sortedLines = processLines(inputFile, lines.clone());
				final boolean formattingRequired = !Arrays.equals(lines, sortedLines);
				if (formattingRequired) {
					++countFormattingRequired;
					if (checkOnly)
						warn(inputFile + " requires proper formatting");
					else
						log("formatted " + inputFile, Project.MSG_DEBUG);
				}
				if (formattingRequired || writeIfUnchanged) {
					File outputFile = new File(outputDir, inputFile.getName());
					TranslationUtils.writeFile(outputFile, sortedLines, lineSeparator);
				}
			}
			return countFormattingRequired;
		}
		catch (IOException e) {
			throw new BuildException(e);
		}
	}

	private void validate() {
		if (inputDir == null)
			throw new BuildException("missing attribute 'dir'");
		if (outputDir == null)
			outputDir = inputDir;
		if (!inputDir.isDirectory())
			throw new BuildException("input directory '" + inputDir + "' does not exist");
		if (!outputDir.isDirectory() && !outputDir.mkdirs())
			throw new BuildException("cannot create output directory '" + outputDir + "'");
	}

	private String[] processLines(File inputFile, String[] lines) {
		Arrays.sort(lines, KEY_COMPARATOR);
		ArrayList<String> result = new ArrayList<String>(lines.length);
		String lastKey = null;
		String lastValue = null;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].indexOf('#') == 0 || lines[i].matches("\\s*"))
				continue;
			final String[] keyValue = lines[i].split("\\s*=\\s*", 2);
			if (keyValue.length != 2 || keyValue[0].length() == 0) {
				// broken line: no '=' sign or empty key (we had " = ======")
				warn(inputFile.getName() + ": no key/val: " + lines[i]);
				continue;
			}
			if (keyValue[1].matches("(\\[auto\\]|\\[translate me\\])?")) {
				warn(inputFile.getName() + ": empty translation: " + lines[i]);
			}
			final String thisKey = keyValue[0];
			final String thisValue = keyValue[1];
			if (lastKey != null && thisKey.equals(lastKey)) {
				if (quality(thisValue) < quality(lastValue)) {
					log(inputFile.getName() + ": drop " + TranslationUtils.toLine(lastKey, thisValue));
					continue;
				}
				else if (quality(thisValue) == quality(lastValue)) {
					if (thisValue.equals(lastValue)) {
						log(inputFile.getName() + ": drop duplicate " + TranslationUtils.toLine(lastKey, thisValue));
					}
					else if (quality(thisValue) == QUALITY_MANUALLY_TRANSLATED) {
						warn(inputFile.getName() //
						        + ": drop one of two of equal quality (revisit!):keep: "
						        + TranslationUtils.toLine(lastKey, lastValue));
						warn(inputFile.getName() //
						        + ": drop one of two of equal quality (revisit!):drop: "
						        + TranslationUtils.toLine(thisKey, thisValue));
					}
					else {
						log(inputFile.getName() + ": drop " + TranslationUtils.toLine(lastKey, thisValue));
					}
					continue;
				}
				else {
					log(inputFile.getName() + ": drop " + TranslationUtils.toLine(lastKey, lastValue));
				}
				lastValue = thisValue;
			}
			else {
				if (lastKey != null)
					result.add(TranslationUtils.toLine(lastKey, lastValue));
				lastKey = thisKey;
				lastValue = thisValue;
			}
		}
		if (lastKey != null)
			result.add(TranslationUtils.toLine(lastKey, lastValue));
		String[] resultArray = new String[result.size()];
		return result.toArray(resultArray);
	}

	private int quality(String value) {
		if (value.length() == 0)
			return QUALITY_NULL;
		if (value.indexOf("[translate me]") > 0)
			return QUALITY_TRANSLATE_ME;
		if (value.indexOf("[auto]") > 0)
			return QUALITY_AUTO_TRANSLATED;
		return QUALITY_MANUALLY_TRANSLATED;
	}

	private void warn(String msg) {
		log(msg, Project.MSG_WARN);
	}

	/** per default output files will only be created if the output would
	 * differ from the input file. Set attribute <code>writeIfUnchanged</code>
	 * to "true" to enforce file creation. */
	public void setWriteIfUnchanged(boolean writeIfUnchanged) {
		this.writeIfUnchanged = writeIfUnchanged;
	}

	public void setDir(String inputDir) {
		setDir(new File(inputDir));
	}

	public void setDir(File inputDir) {
		this.inputDir = inputDir;
	}

	public void setIncludes(String pattern) {
		includePatterns.add(Pattern.compile(TranslationUtils.wildcardToRegex(pattern)));
	}

	public void setExcludes(String pattern) {
		excludePatterns.add(Pattern.compile(TranslationUtils.wildcardToRegex(pattern)));
	}

	/** parameter is set in the build file via the attribute "outputDir" */
	public void setOutputDir(String outputDir) {
		setOutputDir(new File(outputDir));
	}

	/** parameter is set in the build file via the attribute "outputDir" */
	public void setOutputDir(File outputDir) {
		this.outputDir = outputDir;
	}

	/** parameter is set in the build file via the attribute "eolStyle" */
	public void setEolStyle(String eolStyle) {
		if (eolStyle.toLowerCase().startsWith("unix"))
			lineSeparator = "\n";
		else if (eolStyle.toLowerCase().startsWith("win"))
			lineSeparator = "\r\n";
		else if (eolStyle.toLowerCase().startsWith("mac"))
			lineSeparator = "\r";
		else
			throw new BuildException("unknown eolStyle, known: unix|win|mac");
	}
}
