/*
 * Copyright (C) 2024-2025 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMekLab.
 *
 * MegaMekLab is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MegaMekLab is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MegaMek was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package megameklab.ui.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;

import megamek.common.Configuration;
import megamek.common.annotations.Nullable;
import megamek.common.loaders.BLKFile;
import megamek.common.preference.PreferenceManager;
import megamek.common.units.Entity;
import megamek.common.units.Mek;
import megamek.logging.MMLogger;
import megameklab.ui.FileNameManager;
import megameklab.ui.PopupMessages;
import megameklab.ui.dialog.MMLFileChooser;
import megameklab.util.CConfig;
import megameklab.util.UnitUtil;

public class MegaMekLabFileSaver {
    private static final String LICENSE_HEADER = """
          # MegaMek Data (C) %s by The MegaMek Team is licensed under CC BY-NC-SA 4.0.
          # To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-sa/4.0/
          #
          # NOTICE: The MegaMek organization is a non-profit group of volunteers
          # creating free software for the BattleTech community.
          #
          # MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
          # of The Topps Company, Inc. All Rights Reserved.
          #
          # Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
          # InMediaRes Productions, LLC.
          #
          # MechWarrior Copyright Microsoft Corporation. MegaMek Data was created under
          # Microsoft's "Game Content Usage Rules"
          # <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
          # affiliated with Microsoft.
          """.formatted(Calendar.getInstance().get(Calendar.YEAR));


    private final MMLFileChooser saveUnitFileChooser = new MMLFileChooser();
    private final MMLogger logger;

    public MegaMekLabFileSaver(MMLogger mainLogger, String dialogTitle) {
        logger = mainLogger;
        saveUnitFileChooser.setDialogTitle(dialogTitle);
    }

    /**
     * Constructs a file name for the current Entity using the chassis and model name and the correct extension for the
     * unit type. Any character that is not legal for a Windows filename is replaced by an underscore.
     *
     * @param entity The Entity
     *
     * @return A default filename for the Entity
     */
    public static String createUnitFilename(Entity entity) {
        String fileName = (entity.getChassis() + ' ' + entity.getModel()).trim();
        fileName = fileName.replaceAll("[/\\\\<>:\"|?*]", "_");
        return fileName + ((entity instanceof Mek) ? ".mtf" : ".blk");
    }

    /**
     * Tries to save the unit directly to its file, if it has a filename already. If it hasn't, it performs a Save
     * As...
     *
     */
    public String saveUnit(JFrame ownerFrame, FileNameManager fileNameManager, Entity entity) {
        String filePathName = fileNameManager.getFileName();
        // For safety, save automatically only to .mtf or .blk files, otherwise ask
        if (!(filePathName.endsWith(".mtf") || filePathName.endsWith(".blk"))
              || !new File(filePathName).exists()
              || fileNameManager.hasEntityNameChanged()) {
            File selectedFile = chooseSaveFile(ownerFrame, entity);
            if (selectedFile == null) {
                return null;
            }
            filePathName = selectedFile.getPath();
        }

        CConfig.setMostRecentFile(filePathName);
        return saveUnitTo(ownerFrame, new File(filePathName), entity);
    }

    public String saveUnitAs(JFrame ownerFrame, Entity entity) {

        File saveFile = chooseSaveFile(ownerFrame, entity);
        if (saveFile != null) {
            CConfig.setMostRecentFile(saveFile.toString());
            return saveUnitTo(ownerFrame, saveFile, entity);
        }
        return null;
    }

    // Replace owner class with EntitySource... somehow.
    private @Nullable File chooseSaveFile(JFrame ownerFrame, Entity entity) {
        if (entity instanceof Mek) {
            saveUnitFileChooser.setFileFilter(new FileNameExtensionFilter("Mek files", "mtf"));
        } else {
            saveUnitFileChooser.setFileFilter(new FileNameExtensionFilter("Unit files", "blk"));
        }
        File userUnitDir = userUnitDirectoryDefault();
        if (userUnitDir != null) {
            saveUnitFileChooser.setCurrentDirectory(userUnitDir);
            saveUnitFileChooser.setSelectedFile(new File(userUnitDir, createUnitFilename(entity)));
        } else {
            saveUnitFileChooser.setSelectedFile(new File(createUnitFilename(entity)));
        }
        int result = saveUnitFileChooser.showSaveDialog(ownerFrame);
        if ((result != JFileChooser.APPROVE_OPTION) || (saveUnitFileChooser.getSelectedFile() == null)) {
            return null;
        } else {
            return saveUnitFileChooser.getSelectedFile();
        }
    }

    /**
     * Custom units must live in the shared user directory (the {@code UserDir} preference) so that
     * MegaMek scans and displays them. Returns that directory as the default save location, but only
     * when the chooser's remembered directory is unset or points inside the bundled {@code data/}
     * tree — saving a custom unit into {@code data/} hides it from MegaMek and risks being wiped by
     * the build's data staging. Returns {@code null} to keep the remembered directory otherwise.
     */
    private @Nullable File userUnitDirectoryDefault() {
        String userDir = PreferenceManager.getClientPreferences().getUserDir();
        if ((userDir == null) || userDir.isBlank()) {
            return null;
        }
        File userDirFile = new File(userDir);
        if (!userDirFile.isDirectory()) {
            return null;
        }
        File current = saveUnitFileChooser.getCurrentDirectory();
        return ((current == null) || isInsideDataDirectory(current)) ? userDirFile : null;
    }

    private static boolean isInsideDataDirectory(File directory) {
        try {
            String canonical = directory.getCanonicalPath();
            String dataDir = Configuration.dataDir().getCanonicalPath();
            return canonical.equals(dataDir) || canonical.startsWith(dataDir + File.separator);
        } catch (IOException ignored) {
            return false;
        }
    }

    private String saveUnitTo(JFrame ownerFrame, File file, Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            try (FileOutputStream fos = new FileOutputStream(file);
                  PrintStream ps = new PrintStream(fos)) {
                if (CConfig.includeLicense()) {
                    ps.println(LICENSE_HEADER);
                }
                ps.println(UnitUtil.saveUnitToString(entity, true));
            }

            PopupMessages.showUnitSavedMessage(ownerFrame, entity, file);
            return file.toString();
        } catch (Exception ex) {
            PopupMessages.showFileWriteError(ownerFrame, ex.getMessage());
            logger.error("", ex);
            return null;
        }
    }
}
