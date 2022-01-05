package org.unfoldingword.tools.jtar;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Helps dealing with file permissions.
 */
public class PermissionUtils {

	/**
	 * XXX: When using standard Java permissions, we treat 'owner' and 'group' equally and give no
	 *      permissions for 'others'.
	 */
	private enum StandardFilePermission {
		EXECUTE(0110), WRITE(0220), READ(0440);

		private int mode;

		StandardFilePermission(int mode) {
			this.mode = mode;
		}
	}

	/**
	 * Get file permissions in octal mode, e.g. 0755.
	 *
	 * Note: it uses `java.nio.file.attribute.PosixFilePermission` if OS supports this, otherwise reverts to
	 * using standard Java file operations, e.g. `java.io.File#canExecute()`. In the first case permissions will
	 * be precisely as reported by the OS, in the second case 'owner' and 'group' will have equal permissions and
	 * 'others' will have no permissions, e.g. if file on Windows OS is `read-only` permissions will be `0550`.
	 *
	 * @throws NullPointerException if file is null.
	 * @throws IllegalArgumentException if file does not exist.
	 */
	public static int permissions(File f) {
		if(f == null) {
			throw new NullPointerException("File is null.");
		}
		if(!f.exists()) {
			throw new IllegalArgumentException("File " + f + " does not exist.");
		}

		return standardPermissions(f);
	}

	private static Set<StandardFilePermission> readStandardPermissions(File f) {
		Set<StandardFilePermission> permissions = new HashSet<>();
		if(f.canExecute()) {
			permissions.add(StandardFilePermission.EXECUTE);
		}
		if(f.canWrite()) {
			permissions.add(StandardFilePermission.WRITE);
		}
		if(f.canRead()) {
			permissions.add(StandardFilePermission.READ);
		}
		return permissions;
	}

	private static Integer standardPermissions(File f) {
		int number = 0;
		Set<StandardFilePermission> permissions = readStandardPermissions(f);
		for (StandardFilePermission permission : permissions) {
			number += permission.mode;
		}
		return number;
	}
}
