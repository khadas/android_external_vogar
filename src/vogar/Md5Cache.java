/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vogar;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import vogar.commands.Command;

/**
 * Caches content by MD5.
 */
public final class Md5Cache {

    private final String keyPrefix;
    private final CacheFileInterface cacheFileInterface;

    /**
     * Creates a new cache accessor. There's only one directory on disk, so 'keyPrefix' is really
     * just a convenience for humans inspecting the cache.
     */
    public Md5Cache(String keyPrefix, CacheFileInterface cacheFileInterface) {
        this.keyPrefix = keyPrefix;
        this.cacheFileInterface = cacheFileInterface;
    }

    public boolean getFromCache(File output, String key, Command fallbackCommand) {
        cacheFileInterface.prepareDestination(output);
        if (cacheFileInterface.existsInCache(key)) {
            cacheFileInterface.copyFromCache(key, output);
            return true;
        }
        fallbackCommand.execute();
        insert(key, output);
        return false;
    }

    /**
     * Returns an ASCII hex representation of the MD5 of the content of 'file'.
     */
    private static String md5(File file) {
        byte[] digest = null;
        try {
            MessageDigest digester = MessageDigest.getInstance("MD5");
            byte[] bytes = new byte[8192];
            FileInputStream in = new FileInputStream(file);
            try {
                int byteCount;
                while ((byteCount = in.read(bytes)) > 0) {
                    digester.update(bytes, 0, byteCount);
                }
                digest = digester.digest();
            } finally {
                in.close();
            }
        } catch (Exception cause) {
            throw new RuntimeException("Unable to compute MD5 of \"" + file + "\"", cause);
        }
        return (digest == null) ? null : byteArrayToHexString(digest);
    }

    private static String byteArrayToHexString(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(Integer.toHexString((b >> 4) & 0xf));
            result.append(Integer.toHexString(b & 0xf));
        }
        return result.toString();
    }

    /**
     * Returns the appropriate key for a dex file corresponding to the contents of 'classpath'.
     * Returns null if we don't think it's possible to cache the given classpath.
     */
    public String makeKey(Classpath classpath) {
        // Do we have it in cache?
        String key = keyPrefix;
        for (File element : classpath.getElements()) {
            // We only cache dexed .jar files, not directories.
            if (!element.toString().endsWith(".jar")) {
                return null;
            }
            key += "-" + md5(element);
        }
        return key;
    }

    /**
     * Returns a key corresponding to the MD5ed contents of {@code file}.
     */
    public String makeKey(File file) {
        return keyPrefix + "-" + md5(file);
    }

    /**
     * Copy the file 'content' into the cache with the given 'key'.
     * This method assumes you're using the appropriate key for the content (and has no way to
     * check because the key is a function of the inputs that made the content, not the content
     * itself).
     * We accept a null so the caller doesn't have to pay attention to whether we think we can
     * cache the content or not.
     */
    private void insert(String key, File content) {
        if (key == null) {
            return;
        }
        Console.getInstance().verbose("inserting " + key);
        cacheFileInterface.copyToCache(content, key);
    }
}
