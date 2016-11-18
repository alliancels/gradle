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

package org.gradle.nativeplatform.toolchain.internal.iar

import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.iar.DefaultIarWorkbenchLocator.ArchitectureDescriptorBuilder
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import org.gradle.util.VersionNumber
import org.junit.Rule

import spock.lang.Specification

class DefaultIarWorkbenchLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final WindowsRegistry windowsRegistry =  Stub(WindowsRegistry)
    final SystemInfo systemInfo =  Stub(SystemInfo)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
    }
    final IarWorkbenchLocator iarWorkbenchLocator = new DefaultIarWorkbenchLocator(operatingSystem, windowsRegistry, systemInfo)

    def "use highest iar version found in the registry"() {
        def dir1 = iarDir("vs1");
        def dir2 = iarDir("vs2");

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Wow6432Node\IAR Systems\Embedded Workbench\5.0\EWARM/) >> ["", "5.0", "6.0", "ignore-me"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Wow6432Node\IAR Systems\Embedded Workbench\5.0\EWARM/, "5.0") >> dir1.absolutePath + "/arm/bin"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Wow6432Node\IAR Systems\Embedded Workbench\5.0\EWARM/, "6.0") >> dir2.absolutePath + "/arm/bin"

        when:
        def result = iarWorkbenchLocator.locateDefaultIarWorkbenchInstall()

        then:
        result.available
        result.iarWorkbench.name == "EWARM"
        result.iarWorkbench.version == VersionNumber.parse("6.60")
        result.iarWorkbench.baseDir == dir2
        result.iarWorkbench.iarArm
    }

    def "can locate all versions of iar"() {
        def dir1 = iarDir("vs1");
        def dir2 = iarDir("vs2");
        def dir3 = iarDir("vs3")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Wow6432Node\IAR Systems\Embedded Workbench\5.0\EWARM/) >> ["", "5.0", "6.0", "7.0", "ignore-me"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Wow6432Node\IAR Systems\Embedded Workbench\5.0\EWARM/, "5.0") >> dir1.absolutePath + "/arm/bin"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Wow6432Node\IAR Systems\Embedded Workbench\5.0\EWARM/, "6.0") >> dir2.absolutePath + "/arm/bin"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Wow6432Node\IAR Systems\Embedded Workbench\5.0\EWARM/, "7.0") >> dir3.absolutePath + "/arm/bin"

        when:
        def allResults = iarWorkbenchLocator.locateDefaultIarWorkbenchInstall()

        then:
        allResults.size() == 3
        allResults.collect { it.iar.name } == [ "IAR ARM 7.0", "IAR ARM 6.0", "IAR ARM 5.0" ]
        allResults.every { it.available }
    }

    def "iar not available when nothing in registry"() {
        def visitor = Mock(TreeVisitor)

        given:
        windowsRegistry.getValueNames(_, _) >> { throw new MissingRegistryEntryException("not found") }

        when:
        def result = iarWorkbenchLocator.locateDefaultIarWorkbenchInstall()

        then:
        !result.available
        result.iarWorkbench == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate an IAR installation, using the Windows registry.")
    }

    def "iar workbench not available when locating all versions and nothing in registry"() {
        def visitor = Mock(TreeVisitor)

        given:
        windowsRegistry.getValueNames(_, _) >> { throw new MissingRegistryEntryException("not found") }

        when:
        def allResults = iarWorkbenchLocator.locateAllIarWorkbenchVersions()

        then:
        allResults.size() == 1
        !allResults.get(0).available
        allResults.get(0).iarWorkbench == null

        when:
        allResults.get(0).explain(visitor)

        then:
        1 * visitor.node("Could not locate an IAR installation, using the Windows registry.")
    }

    def iarDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createDir("common")
        dir.createFile("arm/bin/iccarm.exe")
        dir.createDir("arm/lib")
        return dir
    }

    boolean requires64BitInstall(ArchitectureDescriptorBuilder builders) {
        return builders in [ ArchitectureDescriptorBuilder.ARM ]
    }

    def platform(String name) {
        return Stub(NativePlatformInternal) {
            getArchitecture() >> {
                Architectures.forInput(name)
            }
        }
    }
}
