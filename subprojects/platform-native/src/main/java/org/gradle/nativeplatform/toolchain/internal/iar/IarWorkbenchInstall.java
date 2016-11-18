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
import org.gradle.api.Named;
import org.gradle.util.VersionNumber;

import java.io.File;

public class IarWorkbenchInstall implements Named {
    private final IarArmInstall iarArmInstall;
    private final File baseDir;

    public IarWorkbenchInstall(File baseDir, IarArmInstall iarArmInstall) {
        this.baseDir = baseDir;
        this.iarArmInstall = Preconditions.checkNotNull(iarArmInstall);
    }

    @Override
    public String getName() {
        return iarArmInstall.getName();
    }

    public VersionNumber getVersion() {
        return iarArmInstall.getVersion();
    }

    public File getIarWorkbenchDir() {
        return baseDir;
    }

    public IarArmInstall getIarArm() {
        return iarArmInstall;
    }
}
