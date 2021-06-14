/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.persistence;

import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.FileUtils;

import java.io.*;
import java.nio.file.Path;

@Slf4j
public class Persistence {
    private static Path usedTempFilePath;

    public static Serializable read(String storagePath) {
        try (FileInputStream fileInputStream = new FileInputStream(storagePath);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            return (Serializable) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            log.error(exception.toString(), exception);
            return null;
        }
    }

    public static void write(Serializable serializable, String directory, String fileName) {
        try {
            FileUtils.makeDirs(directory);
            File tempFile = getTempFile(fileName, directory);
            File storageFile = new File(directory, fileName);
            try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
                 ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                objectOutputStream.writeObject(serializable);
                objectOutputStream.flush();
                fileOutputStream.flush();
                fileOutputStream.getFD().sync();

                FileUtils.renameFile(tempFile, storageFile);
                usedTempFilePath = tempFile.toPath();
            } catch (IOException exception) {
                log.error(exception.toString(), exception);
                usedTempFilePath = null;
            } finally {
                FileUtils.deleteFile(tempFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static File getTempFile(String fileName, String dir) throws IOException {
        File tempFile = usedTempFilePath != null
                ? FileUtils.createNewFile(usedTempFilePath)
                : File.createTempFile("temp_" + fileName, null, new File(dir));
        // We don't use a new temp file path each time, as that causes the delete-on-exit hook to leak memory:
        tempFile.deleteOnExit();
        return tempFile;
    }
}
