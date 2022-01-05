package org.unfoldingword.resourcecontainer;

/**
 * This utility compares variable length semver styled strings
 * 0.1.0, 10.0.1, 1.0, 1, 1.2.3.4
 *
 * All non-numeric characters will be removed. e.g. v1.0 will become 1.0
 * 1.0-alpha.1 will become 1.0.1
 */

class Semver {

    /**
     * Checks if v1 is greater than v2
     * @param v1
     * @param v2
     * @return
     */
    public static final boolean gt(String v1, String v2) {
        return compare(v1, v2) == 1;
    }

    /**
     * Checks if v1 is less than v2
     * @param v1
     * @param v2
     * @return
     */
    public static final boolean lt(String v1, String v2) {
        return compare(v1, v2) == -1;
    }

    /**
     * Checks if v1 is equal to v2
     * @param v1
     * @param v2
     * @return
     */
    public static final boolean eq(String v1, String v2) {
        return compare(v1, v2) == 0;
    }

    /**
     * Compares two version strings.
     * -1 v1 is less than v2
     * 0 both are equal
     * 1 v1 is greater than v2
     *
     * @param v1 the first string to compare
     * @param v2 the second string to compare
     * @return the comparison result
     */
    public static final int compare(String v1, String v2) {
        Version ver1 = new Version(v1);
        Version ver2 = new Version(v2);

        int max = Math.max(ver1.size(), ver2.size());
        for(int i = 0; i < max; i ++) {
            if(ver1.isWild(i) || ver2.isWild(i)) continue;
            if(ver1.get(i) > ver2.get(i)) return 1;
            if(ver1.get(i) < ver2.get(i)) return -1;
        }
        return 0;
    }

    /**
     * Utility for pumping values from a version string
     */
    private static class Version {
        private final String slices[];

        public Version(String v) {
            slices = v.split("\\.");
        }

        public int size() {
            return slices.length;
        }

        /**
         * Returns the value at the given semver index
         * @param index the position in the version
         * @return the integer value of the version position
         */
        public int get(int index) {
            if(index >= 0 && index < slices.length) {
                String value = clean(slices[index]);
                return Integer.parseInt(value);
            } else {
                return 0;
            }
        }

        /**
         * Checks if the value at the index is an asterisk (wild card)
         * @param index the position in the version
         * @return true if the value is a wildcard
         */
        public boolean isWild(int index) {
            if(index >= slices.length) index = slices.length -1;
            if(index < 0) index = 0;

            if(slices.length > 0) {
                return clean(slices[index]).equals("*");
            } else {
                return false;
            }
        }

        /**
         * Removes all non-numeric characters except for an asterisk.
         * @param val the value to be cleaned
         * @return a cleaned value
         */
        private String clean(String val) {
            String cleaned = val.replaceAll("[^\\d\\*]", "").trim();
            if(cleaned.isEmpty()) {
                return "0";
            } else {
                return cleaned;
            }
        }
    }
}
