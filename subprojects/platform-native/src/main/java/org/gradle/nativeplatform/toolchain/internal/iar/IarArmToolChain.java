/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.IarArm;
import org.gradle.nativeplatform.toolchain.IarArmPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider;
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class IarArmToolChain extends ExtendableToolChain<IarArmPlatformToolChain> implements IarArm, NativeToolChainInternal {

    private final String name;
    private final OperatingSystem operatingSystem;

    protected static final Logger LOGGER = LoggerFactory.getLogger(IarArmToolChain.class);

    public static final String DEFAULT_NAME = "iarArm";

    private final ExecActionFactory execActionFactory;
    private final IarWorkbenchLocator iarWorkbenchLocator;
    private final Instantiator instantiator;
    private File installDir;
    private IarArmInstall iarArm;
    private ToolChainAvailability availability;

    public IarArmToolChain(String name, BuildOperationProcessor buildOperationProcessor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory,
                            IarWorkbenchLocator iarWorkbenchLocator, Instantiator instantiator) {
        super(name, buildOperationProcessor, operatingSystem, fileResolver);

        this.name = name;
        this.operatingSystem = operatingSystem;
        this.execActionFactory = execActionFactory;
        this.iarWorkbenchLocator = iarWorkbenchLocator;
        this.instantiator = instantiator;
    }

    @Override
    protected String getTypeName() {
        return "IAR";
    }

    @Override
    public File getInstallDir() {
        return installDir;
    }

    @Override
    public void setInstallDir(Object installDirPath) {
        this.installDir = resolve(installDirPath);
    }

    @Override
    public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
        ToolChainAvailability result = new ToolChainAvailability();
        result.mustBeAvailable(getAvailability());
        if (iarArm != null && !iarArm.isSupportedPlatform(targetPlatform)) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
        }
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        DefaultIarArmPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultIarArmPlatformToolChain.class, targetPlatform, instantiator);
        configureActions.execute(configurableToolChain);

        return new IarArmPlatformToolProvider(buildOperationProcessor, targetPlatform.getOperatingSystem(), configurableToolChain.tools, iarArm, targetPlatform, execActionFactory);
    }

    private ToolChainAvailability getAvailability() {
        if (availability == null) {
            availability = new ToolChainAvailability();
            checkAvailable(availability);
        }
        return availability;
    }

    private void checkAvailable(ToolChainAvailability availability) {
        if (!operatingSystem.isWindows()) {
            availability.unavailable("IAR is not available on this operating system.");
            return;
        }
        IarWorkbenchLocator.SearchResult iarWorkbenchSearchResult = iarWorkbenchLocator.locateDefaultIarWorkbenchInstall(installDir);
        availability.mustBeAvailable(iarWorkbenchSearchResult);
        if (iarWorkbenchSearchResult.isAvailable()) {
            iarArm = iarWorkbenchSearchResult.getIarWorkbench().getIarArm();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return "Tool chain '" + getName() + "' (" + getTypeName() + ")";
    }
}
