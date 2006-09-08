package org.jetel.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Jan Hadrava
 * 
 * Class generating collection of filenames which match
 * given wildcard patterns.
 * The pattern may describe either relative or absolute filenames.
 * '*' represents any count of characters, '?' represents one character.
 * Wildcards cannot be followed by directory separators. 
 */
public class WcardPattern {

	/**
	 * Filename filter for wildcard matching.
	 */
	private static class wcardFilter implements FilenameFilter {
		/**
		 * Regex pattern equivalent to specified wildcard pattern.
		 */
		private Pattern rePattern;
		
		/**
		 * ctor. Creates regex pattern so that it is equivalent to given wildcard pattern. 
		 * @param str Wildcard pattern. 
		 */
		public wcardFilter(String str) {

			StringBuffer regex = new StringBuffer(str);
			regex.insert(0, REGEX_START_ANCHOR + REGEX_START_QUOTE);
			for (int wcardIdx = 0; wcardIdx < WCARD_CHAR.length; wcardIdx++) {
				regex.replace(0, regex.length(), 
						regex.toString().replace("" + WCARD_CHAR[wcardIdx],
								REGEX_END_QUOTE + REGEX_SUBST[wcardIdx] + REGEX_START_QUOTE));
			}
			regex.append(REGEX_END_QUOTE + REGEX_END_ANCHOR);

			// Create compiled regex pattern
			rePattern = Pattern.compile(regex.toString());
		}

		/**
		 * Part of FilenameFilter interface.
		 */
		public boolean accept(File dir, String name) {
			return rePattern.matcher(name).matches();
		}

	}

	
	/**
	 * Wildcard characters.
	 */
	private final static char[] WCARD_CHAR = {'*', '?'};
	/**
	 * Regex substitutions for wildcards. 
	 */
	private final static String[] REGEX_SUBST = {".*", "."};
	/**
	 * Start sequence for regex quoting.
	 */
	private final static String REGEX_START_QUOTE = "\\Q";
	/**
	 * End sequence for regex quoting
	 */
	private final static String REGEX_END_QUOTE = "\\E";
	/**
	 * Regex start anchor.
	 */
	private final static char REGEX_START_ANCHOR = '^';
	/**
	 * Regex end anchor.
	 */
	private final static char REGEX_END_ANCHOR = '$';
	
	/**
	 * Collection of filename patterns.
	 */
	private List<String> patterns;
	
	/**
	 * ctor. Creates instance with empty set of patterns.
	 * It doesn't match any filenames initially.  
	 */
	public WcardPattern() {
		patterns = new ArrayList<String>(1);
	}
	
	/**
	 * Adds filename pattern.
	 * @param pat Pattern to be added.
	 */
	public void addPattern(String pat) {
		patterns.add(pat);
	}

	/**
	 * Splits specified pattern in two parts - directory which cannot contain any wildcard
	 * and filename pattern containing wildcards. When specified pattern doesn't contain
	 * any wildcards, doesn't do anything. 
	 * @param pat Pattern to be split.
	 * @param dir Directory name.
	 * @param filePat Filename pattern. 
	 * @return false for pattern without wildcards, true otherwise. 
	 */
	private boolean splitPattern(String pat, StringBuffer dir, StringBuffer filePat) {

		dir.setLength(0);
		filePat.setLength(0);

		for (int charIdx = 0; charIdx < pat.length(); charIdx++) {
			for (int wcardIdx = 0; wcardIdx < WCARD_CHAR.length; wcardIdx++) {
				if (pat.charAt(charIdx) == WCARD_CHAR[wcardIdx]) {	// current char is a wildcard
//					// get position of last preceding directory separator
					int sepPos = pat.lastIndexOf(File.separatorChar, charIdx);
					if (sepPos == -1) {		// no directory separator
						dir.insert(0, "." + File.separatorChar);
						filePat.insert(0, pat);
					} else { // split
						dir.insert(0, pat.substring(0, sepPos + 1));
						filePat.insert(0, pat.substring(sepPos + 1));
					}
					return true;	// pattern contains at least one wildcard
				}
			}
		}
		// no wildcard in pattern
		return false;
	}
	
	/**
	 * Generates filenames matching current set of patterns.
	 * @return Set of matching filenames.
	 */
	public List<String> filenames() {
		List<String> mfiles = new ArrayList<String>();
		for (int i = 0; i < patterns.size(); i++) {
			StringBuffer dirName = new StringBuffer();
			StringBuffer filePat = new StringBuffer();
			if (!splitPattern(patterns.get(i), dirName, filePat)) {	// no wildcards
				mfiles.add(patterns.get(i));
			} else {
				File dir = new File(dirName.toString());
				if (dir.exists()) {
					FilenameFilter filter = new wcardFilter(filePat.toString());
					String[] curMatch = dir.list(filter);
					for (int fnIdx = 0; fnIdx < curMatch.length; fnIdx++) {
						mfiles.add(dirName.toString() + curMatch[fnIdx]);
					}
				}
			}
		}
		return mfiles;
	}

}
