/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2020, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-encoding-taglib.
 *
 * ao-encoding-taglib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-encoding-taglib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-encoding-taglib.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoapps.encoding.taglib.legacy.i18n;

import com.aoapps.hodgepodge.i18n.EditableResourceBundle;
import com.aoapps.hodgepodge.i18n.EditableResourceBundleSet;
import java.io.File;
import java.util.Locale;

/**
 * @author  AO Industries, Inc.
 */
public final class ApplicationResources extends EditableResourceBundle {

	static final EditableResourceBundleSet bundleSet = new EditableResourceBundleSet(
		ApplicationResources.class,
		Locale.ROOT,
		Locale.JAPANESE
	);

	static File getSourceFile(String filename) {
		return new File(System.getProperty("user.home") + "/maven2/ao/oss/encoding-taglib/src/main/resources/com/aoapps/encoding/taglib/legacy/i18n", filename);
	}

	public ApplicationResources() {
		super(Locale.ROOT, bundleSet, getSourceFile("ApplicationResources.properties"));
	}
}
