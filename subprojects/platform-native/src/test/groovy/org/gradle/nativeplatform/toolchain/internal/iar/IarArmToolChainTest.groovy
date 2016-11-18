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

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationProcessor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.text.TreeFormatter
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.IarArmPlatformToolChain
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import spock.lang.Specification

class IarArmToolChainTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    final ExecActionFactory execActionFactory = Mock(ExecActionFactory)
    final BuildOperationProcessor buildOperationProcessor = Stub(BuildOperationProcessor)
    final IarWorkbenchLocator.SearchResult iarWorkbenchLookup = Stub(IarWorkbenchLocator.SearchResult)
    final Instantiator instantiator = DirectInstantiator.INSTANCE
    IarArmToolChain toolChain

    final IarWorkbenchLocator iarWorkbenchLocator = Stub(IarWorkbenchLocator) {
        locateDefaultIarWorkbenchInstall(_) >> iarWorkbenchLookup
    }

    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
    }

    def setup() {
        toolChain = new IarArmToolChain("iarArm", buildOperationProcessor, operatingSystem, fileResolver, execActionFactory, iarWorkbenchLocator, instantiator)
    }

    def "installs an unavailable tool chain when not windows"() {
        given:
        def operatingSystem = Stub(OperatingSystem)
        operatingSystem.isWindows() >> false
        def toolChain = new IarArmToolChain("iarArm", buildOperationProcessor, operatingSystem, fileResolver, execActionFactory, iarWorkbenchLocator, instantiator)

        when:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == 'IAR is not available on this operating system.'
    }

    def "is not available when IAR installation cannot be located"() {
        when:
        iarWorkbenchLookup.available >> false
        iarWorkbenchLookup.explain(_) >> { TreeVisitor<String> visitor -> visitor.node("IAR install not found anywhere") }

        and:
        def result = toolChain.select(Stub(NativePlatformInternal))

        then:
        !result.available
        getMessage(result) == "IAR install not found anywhere"
    }

    def "is available when IAR installation can be located and IAR install supports target platform"() {
        when:
        def iarWorkbench = Stub(IarWorkbenchInstall)
        def iarArm = Stub(IarArmInstall)
        def platform = Stub(NativePlatformInternal)
        iarWorkbenchLookup.available >> true
        iarWorkbenchLookup.iarWorkbench >> iarWorkbench
        iarWorkbenchLookup.iarWorkbench >> Stub(IarWorkbenchInstall)
        iarWorkbench.iarArm >> iarArm
        iarArm.isSupportedPlatform(platform) >> true

        and:
        def platformToolChain = toolChain.select(platform)

        then:
        platformToolChain.available
    }

    def "resolves install directory"() {
        when:
        toolChain.installDir = "The Path"

        then:
        fileResolver.resolve("The Path") >> file("one")

        and:
        toolChain.installDir == file("one")
    }

    def "provided action can configure platform tool chain"() {
        given:
        def platform = Stub(NativePlatformInternal)
        def iarWorkbench = Stub(IarWorkbenchInstall)
        def iarArm = Stub(IarArmInstall)
        iarWorkbenchLookup.available >> true
        iarWorkbenchLookup.iarWorkbench >> iarWorkbench
        iarWorkbenchLookup.iarWorkbench >> Stub(IarWorkbenchInstall)
        iarWorkbench.iarArm >> iarArm
        iarArm.isSupportedPlatform(platform) >> true

        def action = Mock(Action)
        toolChain.eachPlatform(action)

        when:
        toolChain.select(platform)

        then:
        1 * action.execute(_) >> { IarArmPlatformToolChain platformToolChain ->
            assert platformToolChain.platform == platform
            assert platformToolChain.assembler
            assert platformToolChain.cCompiler
            assert platformToolChain.cppCompiler
            assert platformToolChain.linker
            assert platformToolChain.staticLibArchiver
        }
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }

    def getMessage(ToolSearchResult result) {
        def formatter = new TreeFormatter()
        result.explain(formatter)
        return formatter.toString()
    }
}
