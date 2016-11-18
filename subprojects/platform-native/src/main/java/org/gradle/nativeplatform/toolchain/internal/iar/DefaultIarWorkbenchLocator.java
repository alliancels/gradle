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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.Transformer;
import org.gradle.internal.FileUtils;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.util.CollectionUtils;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class DefaultIarWorkbenchLocator implements IarWorkbenchLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIarWorkbenchLocator.class);

    private static final String REGISTRY_ROOTPATH_IAR_ARM = "SOFTWARE\\Wow6432Node\\IAR Systems\\Embedded Workbench\\5.0\\EWARM";
    private static final String PATH_ARM = "arm/";
    private static final String PATH_COMMON = "common/";
    private static final String PATH_COMMON_BIN = PATH_COMMON + "bin/";
    private static final String PATH_ARM_BIN = PATH_ARM + "bin/";
    private static final String COMPILER_FILENAME = "iccarm.exe";

    private final Map<File, IarWorkbenchInstall> foundInstalls = new HashMap<File, IarWorkbenchInstall>();
    private final OperatingSystem os;
    private final WindowsRegistry windowsRegistry;
    private final SystemInfo systemInfo;
    private boolean initialised;

    public DefaultIarWorkbenchLocator(OperatingSystem os, WindowsRegistry windowsRegistry, SystemInfo systemInfo) {
        this.os = os;
        this.windowsRegistry = windowsRegistry;
        this.systemInfo = systemInfo;
    }

    @Override
    public List<SearchResult> locateAllIarWorkbenchVersions() {
        initializeIarWorkbenchInstalls();

        List<IarWorkbenchInstall> sortedInstalls = CollectionUtils.sort(foundInstalls.values(), new Comparator<IarWorkbenchInstall>() {
            @Override
            public int compare(IarWorkbenchInstall o1, IarWorkbenchInstall o2) {
                return o2.getVersion().compareTo(o1.getVersion());
            }
        });

        if (sortedInstalls.isEmpty()) {
            return Lists.newArrayList((SearchResult)new InstallNotFound("Could not locate an IAR Embedded Workbench installation, using the Windows registry and system path."));
        } else {
            return CollectionUtils.collect(sortedInstalls, new Transformer<SearchResult, IarWorkbenchInstall>() {
                @Override
                public SearchResult transform(IarWorkbenchInstall iarWorkbenchInstall) {
                    return new InstallFound(iarWorkbenchInstall);
                }
            });
        }
    }

    @Override
    public SearchResult locateDefaultIarWorkbenchInstall() {
        return locateDefaultIarWorkbenchInstall(null);
    }

    @Override
    public SearchResult locateDefaultIarWorkbenchInstall(File candidate) {
        initializeIarWorkbenchInstalls();

        if (candidate != null) {
            return locateUserSpecifiedInstall(candidate);
        }

        return determineDefaultInstall();
    }


    private void initializeIarWorkbenchInstalls() {
        if (!initialised) {
            locateInstallsInRegistry();
            initialised = true;
        }
    }

    private void locateInstallsInRegistry() {
        List<String> iarArmVersions;
        try {
            iarArmVersions = windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, REGISTRY_ROOTPATH_IAR_ARM);
        } catch (MissingRegistryEntryException e) {
            LOGGER.debug("Unable to find IAR installation information in registry.");
            return;
        }

        for (String subKey : iarArmVersions) {

            LOGGER.debug("Found IAR EWARM version {}.", subKey);

            if (!subKey.matches("\\d+\\.\\d+\\.\\d+")) {
                // Ignore the other values
                continue;
            }
            File iarArmDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, REGISTRY_ROOTPATH_IAR_ARM + "\\" + subKey, "InstallPath"));

            iarArmDir = FileUtils.canonicalize(iarArmDir);
            File iarWorkbenchDir = iarArmDir;

            LOGGER.debug("Potential IAR ARM {} install found at {}.", subKey, iarArmDir);

            if (isIarArm(iarArmDir) && isIarWorkbench(iarWorkbenchDir)) {
                LOGGER.debug("Found IAR ARM {} at {}", subKey, iarArmDir);
                VersionNumber version = VersionNumber.parse(subKey);
                IarArmInstall iarArm = buildIarArmInstall("IAR ARM " + subKey, iarWorkbenchDir, iarArmDir, version);
                IarWorkbenchInstall iarWorkbench = new IarWorkbenchInstall(iarWorkbenchDir, iarArm);
                foundInstalls.put(iarWorkbenchDir, iarWorkbench);
            } else {
                LOGGER.debug("Ignoring candidate IAR ARM directory {} as it does not look like an IAR ARM installation.", iarArmDir);
            }
        }
    }

    private SearchResult locateUserSpecifiedInstall(File candidate) {
        File iarWorkbenchDir = FileUtils.canonicalize(candidate);
        File iarArmDir = iarWorkbenchDir;

        if (!isIarWorkbench(iarWorkbenchDir) || !isIarArm(iarArmDir)) {
            LOGGER.debug("Ignoring candidate IAR ARM install for {} as it does not look like an IAR ARM installation.", candidate);
            return new InstallNotFound(String.format("The specified installation directory '%s' does not appear to contain an IAR Workbench installation.", candidate));
        }

        if (!foundInstalls.containsKey(iarWorkbenchDir)) {
            IarArmInstall iarArm = buildIarArmInstall("IAR ARM from user provided path", iarWorkbenchDir, iarArmDir, VersionNumber.UNKNOWN);
            IarWorkbenchInstall iarWorkbench = new IarWorkbenchInstall(iarWorkbenchDir, iarArm);
            foundInstalls.put(iarWorkbenchDir, iarWorkbench);
        }
        return new InstallFound(foundInstalls.get(iarWorkbenchDir));
    }

    private IarArmInstall buildIarArmInstall(String name, File workbenchPath, File basePath, VersionNumber version) {

        List<ArchitectureDescriptorBuilder> architectureDescriptorBuilders = Lists.newArrayList();

        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.ARM);

        Map<Architecture, ArchitectureDescriptor> descriptors = Maps.newHashMap();
        for (ArchitectureDescriptorBuilder architectureDescriptorBuilder : architectureDescriptorBuilders) {
            ArchitectureDescriptor descriptor = architectureDescriptorBuilder.buildDescriptor(basePath, workbenchPath);
            if (descriptor.isInstalled()) {
                descriptors.put(architectureDescriptorBuilder.architecture, descriptor);
            }
        }

        return new IarArmInstall(name, version, descriptors);
    }

    private SearchResult determineDefaultInstall() {
        IarWorkbenchInstall candidate = null;

        for (IarWorkbenchInstall iarWorkbench : foundInstalls.values()) {
            if (candidate == null || iarWorkbench.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = iarWorkbench;
            }
        }

        return candidate == null ? new InstallNotFound("Could not locate an IAR Workbench installation, using the Windows registry.") : new InstallFound(candidate);
    }

    private static boolean isIarWorkbench(File candidate) {

        File workbench = new File(candidate, PATH_COMMON);
        boolean workbenchDirExists = workbench.isDirectory();

        if (workbenchDirExists) {
            LOGGER.debug("Found IAR Workbench at {}", workbench);
        } else {
            LOGGER.debug("Unable to find IAR Workbench at {}", workbench);
        }

        return workbenchDirExists;
    }

    private static boolean isIarArm(File candidate) {

        File compiler = new File(candidate, PATH_ARM_BIN + COMPILER_FILENAME);
        boolean compilerExists = compiler.isFile();

        if (compilerExists) {
            LOGGER.debug("Found IAR ARM at {}", compiler);
        } else {
            LOGGER.debug("Unable to find IAR ARM at {}", compiler);
        }

        return compilerExists;
    }

    private static class InstallFound implements SearchResult {
        private final IarWorkbenchInstall install;

        public InstallFound(IarWorkbenchInstall install) {
            this.install = Preconditions.checkNotNull(install);
        }

        @Override
        public IarWorkbenchInstall getIarWorkbench() {
            return install;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private static class InstallNotFound implements SearchResult {
        private final String message;

        private InstallNotFound(String message) {
            this.message = message;
        }

        @Override
        public IarWorkbenchInstall getIarWorkbench() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }

    static class DefaultArchitectureDescriptor implements org.gradle.nativeplatform.toolchain.internal.iar.ArchitectureDescriptor {
        private final File commonPath;
        private final File binPath;
        private final File libPath;
        private final File incPath;
        private final File assemblerPath;
        private final File compilerPath;
        private final File linkerPath;
        private final File archiverPath;
        private final Map<String, String> definitions;

        DefaultArchitectureDescriptor(File commonPath, File binPath, File libPath, File incPath,
                                      File assemblerPath, File compilerPath, File linkerPath,
                                      File archiverPath, Map<String, String> definitions) {
            this.commonPath = commonPath;
            this.binPath = binPath;
            this.libPath = libPath;
            this.incPath = incPath;
            this.assemblerPath = assemblerPath;
            this.compilerPath = compilerPath;
            this.linkerPath = linkerPath;
            this.archiverPath = archiverPath;
            this.definitions = definitions;
        }

        @Override
        public File getCommonPath() {
            return commonPath;
        }

        @Override
        public File getBinaryPath() {
            return binPath;
        }

        @Override
        public File getLibraryPath() {
            return libPath;
        }

        @Override
        public File getIncludePath() {
            return incPath;
        }

        @Override
        public File getAssemblerPath() {
            return assemblerPath;
        }

        @Override
        public File getCompilerPath() {
            return compilerPath;
        }

        @Override
        public File getLinkerPath() {
            return linkerPath;
        }

        @Override
        public File getArchiverPath() {
            return archiverPath;
        }

        @Override
        public Map<String, String> getDefinitions() {
            return definitions;
        }

        @Override
        public boolean isInstalled() {
            return commonPath.exists() && binPath.exists() && libPath.exists();
        }
    }

    enum ArchitectureDescriptorBuilder {

        ARM("arm", "arm/bin", "arm/lib", "arm/inc", "iasmarm.exe", "iccarm.exe", "ilinkarm.exe", "iarchive.exe");

        final Architecture architecture;
        final String binPath;
        final String libPath;
        final String incPath;
        final String asmFilename;
        final String compilerFilename;
        final String linkerFilename;
        final String archiverFilename;

        ArchitectureDescriptorBuilder(String architecture, String binPath, String libPath, String incPath, String asmFilename,
                                      String compilerFilename, String linkerFilename, String archiverFilename) {
            this.binPath = binPath;
            this.libPath = libPath;
            this.incPath = incPath;
            this.asmFilename = asmFilename;
            this.compilerFilename = compilerFilename;
            this.linkerFilename = linkerFilename;
            this.archiverFilename = archiverFilename;
            this.architecture = Architectures.forInput(architecture);
        }

        File getBinPath(File basePath) {
            return new File(basePath, binPath);
        }

        File getLibPath(File basePath) {
            return new File(basePath, libPath);
        }
        File getIncPath(File basePath) {
            return new File(basePath, incPath);
        }

        File getAssemblerPath(File basePath) {
            return new File(getBinPath(basePath), asmFilename);
        }
        File getCompilerPath(File basePath) {
            return new File(getBinPath(basePath), compilerFilename);
        }
        File getLinkerPath(File basePath) {
            return new File(getBinPath(basePath), linkerFilename);
        }
        File getArchiverPath(File basePath) {
            return new File(getBinPath(basePath), archiverFilename);
        }

        Map<String, String> getDefinitions() {
            return Maps.newHashMap();
        }

        ArchitectureDescriptor buildDescriptor(File basePath, File iarPath) {
            File commonBin = new File(iarPath, PATH_COMMON_BIN);

            return new DefaultArchitectureDescriptor(commonBin, getBinPath(basePath), getLibPath(basePath),
                getIncPath(basePath), getAssemblerPath(basePath), getCompilerPath(basePath), getLinkerPath(basePath),
                getArchiverPath(basePath), getDefinitions());
        }
    }
}
