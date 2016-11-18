/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.iar;

import org.gradle.api.Named;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.Map;

public class IarArmInstall implements Named {

    private static final String COMPILER_FILENAME = "iccarm.exe";
    private static final String LINKER_FILENAME = "ilinkarm.exe";
    private static final String ARCHIVER_FILENAME = "iarchive.exe";
    private static final String ASSEMBLER_FILENAME = "iasmarm.exe";

    private final Map<Architecture, org.gradle.nativeplatform.toolchain.internal.iar.ArchitectureDescriptor> architectureDescriptors;
    private final String name;
    private final VersionNumber version;

    public IarArmInstall(String name, VersionNumber version,
                            Map<Architecture, org.gradle.nativeplatform.toolchain.internal.iar.ArchitectureDescriptor> architectureDescriptors) {
        this.name = name;
        this.version = version;
        this.architectureDescriptors = architectureDescriptors;
    }

    @Override
    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public boolean isSupportedPlatform(NativePlatformInternal targetPlatform) {
        return targetPlatform.getOperatingSystem().isWindows()
            && (architectureDescriptors.containsKey(getPlatformArchitecture(targetPlatform)));
    }

    public File getCommonPath(NativePlatformInternal targetPlatform) {
        return getDescriptor(targetPlatform).getCommonPath();
    }

    public File getCompiler(NativePlatformInternal targetPlatform) {
        return new File(getDescriptor(targetPlatform).getBinaryPath(), COMPILER_FILENAME);
    }

    public File getLinker(NativePlatformInternal targetPlatform) {
        return new File(getDescriptor(targetPlatform).getBinaryPath(), LINKER_FILENAME);
    }

    public File getArchiver(NativePlatformInternal targetPlatform) {
        return new File(getDescriptor(targetPlatform).getBinaryPath(), ARCHIVER_FILENAME);
    }

    public File getAssembler(NativePlatformInternal targetPlatform) {
        return new File(getDescriptor(targetPlatform).getBinaryPath(), ASSEMBLER_FILENAME);
    }

    public File getBinaryPath(NativePlatformInternal targetPlatform) {
        return getDescriptor(targetPlatform).getBinaryPath();
    }

    public File getLibraryPath(NativePlatformInternal targetPlatform) {
        return getDescriptor(targetPlatform).getLibraryPath();
    }

    public Map<String, String> getDefinitions(NativePlatformInternal targetPlatform) {
        return getDescriptor(targetPlatform).getDefinitions();
    }

    public File getIncludePath(NativePlatformInternal targetPlatform) {
        return getDescriptor(targetPlatform).getIncludePath();
    }

    private Architecture getPlatformArchitecture(NativePlatformInternal targetPlatform) {
        return targetPlatform.getArchitecture();
    }

    private org.gradle.nativeplatform.toolchain.internal.iar.ArchitectureDescriptor getDescriptor(NativePlatformInternal targetPlatform) {
        return architectureDescriptors.get(getPlatformArchitecture(targetPlatform));
    }
}
