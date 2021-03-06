/* The MIT License
 * (c) Copyright Sysdeo SA 2001-2002
 * (c) Copyright Eclipse Tomcat Plugin 2014-2016
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or 
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */package net.sf.eclipse.tomcat;

/*
 * All Rights Reserved.
 */
import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class TomcatProjectWARPropertyPage implements TomcatPluginResources {

	private Button exportSourceCheck;
	private Text warLocationText;
	private final TomcatProjectPropertyPage page;

	private static final int TEXT_FIELD_WIDTH = 200;

	public TomcatProjectWARPropertyPage(TomcatProjectPropertyPage page) {
		this.page = page;
	}

	/**
	 * returns a control which consists of the ui elements of this page
	 */
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());

		createWarLocationGroup(composite);

		return composite;
	}

	public void createWarLocationGroup(Composite parent) {
		Composite warLocationGroup = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		warLocationGroup.setLayout(layout);
		warLocationGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// location label
		Label warLocationLabel = new Label(warLocationGroup, SWT.NONE);
		warLocationLabel.setText(WIZARD_PROJECT_WARLOCATION_LABEL);
		warLocationLabel.setEnabled(true);

		// project location entry field
		warLocationText = new Text(warLocationGroup, SWT.BORDER);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = TEXT_FIELD_WIDTH;
		warLocationText.setLayoutData(data);
		warLocationText.setText(this.getWarLocation());
		warLocationText.setEnabled(true);

		Button browseButton = new Button(warLocationGroup, SWT.PUSH);
		browseButton.setText(BROWSE_BUTTON_LABEL);
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
            public void widgetSelected(SelectionEvent evt) {
				String newValue = warFieldChange();
				if (newValue != null) {
					warLocationText.setText(newValue);
				}
			}
		});

		browseButton.setEnabled(true);

		exportSourceCheck = new Button(warLocationGroup, SWT.CHECK | SWT.LEFT);
		exportSourceCheck.setText(WIZARD_PROJECT_EXPORTSOURCE_LABEL);
		data = new GridData();
		data.horizontalSpan = 3;
		exportSourceCheck.setLayoutData(data);
		exportSourceCheck.setEnabled(true);
		exportSourceCheck.setSelection(this.getExportSource());
	}

	protected String getWarLocation() {
		String result = "";
		try {
			TomcatProject prj = page.getTomcatProject();
			if (prj != null) {
                result = prj.getWarLocation();
            }
		} catch (CoreException ex) {
			// result = "";
		}
		return result;
	}

	protected boolean getExportSource() {
		boolean result = false;
		try {
			TomcatProject prj = page.getTomcatProject();
			if (prj != null) {
                result = prj.getExportSource();
            }
		} catch (CoreException ex) {
			// result = false;
		}
		return result;
	}

	/**
	 * performes the ok action for this property page
	 */
	public boolean performOk() {
		try {
			TomcatProject prj = page.getTomcatProject();
			prj.setWarLocation(warLocationText.getText());
			prj.setExportSource(exportSourceCheck.getSelection());
			prj.saveProperties();
		} catch (Exception ex) {
			TomcatLauncherPlugin.log(ex.getMessage());
		}

		return true;
	}

	protected String warFieldChange() {
		File f = new File(warLocationText.getText());
		if (!f.exists()) {
            f = null;
        }
		File d = getFile(f);
		if (d == null) {
            return null;
        }

		return d.getAbsolutePath();
	}


	/**
	 * Helper to open the file chooser dialog.
	 */
	private File getFile(File startingDirectory) {

		FileDialog dialog = new FileDialog(page.getShell(), SWT.OPEN);
		if (startingDirectory != null) {
            dialog.setFileName(startingDirectory.getPath());
        }
		String file = dialog.open();
		if (file != null) {
			file = file.trim();
			if (file.length() > 0) {
                return new File(file);
            }
		}

		return null;
	}

}
