/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.test.unity.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.plugins.CLangPlugin;
import org.gradle.model.*;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeExecutableFileSpec;
import org.gradle.nativeplatform.NativeInstallationSpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.NativeComponents;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.test.NativeTestSuiteSpec;
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinarySpecInternal;
import org.gradle.nativeplatform.test.unity.UnityTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.unity.UnityTestSuiteSpec;
import org.gradle.nativeplatform.test.unity.internal.DefaultUnityTestSuiteBinary;
import org.gradle.nativeplatform.test.unity.internal.DefaultUnityTestSuiteSpec;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.nativeplatform.test.unity.tasks.RunUnityTestExecutable;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;
import java.util.Collection;

import static org.gradle.nativeplatform.test.internal.NativeTestSuites.testedBinariesWithType;

import static org.gradle.nativeplatform.internal.configure.NativeBinaryRules.executableFileFor;
import static org.gradle.nativeplatform.internal.configure.NativeBinaryRules.installationDirFor;

/**
 * A plugin that sets up the infrastructure for testing native binaries with Unity.
 */
@Incubating
public class UnityPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin.class);
        project.getPluginManager().apply(CLangPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        private static final String CUNIT_LAUNCHER_SOURCE_SET = "cunitLauncher";

        @ComponentType
        public void registerUnityTestSuiteSpecType(TypeBuilder<UnityTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultUnityTestSuiteSpec.class);
        }

        private CSourceSet findLauncherSources(UnityTestSuiteSpec suite) {
            return suite.getSources().withType(CSourceSet.class).get(CUNIT_LAUNCHER_SOURCE_SET);
        }

        @ComponentType
        public void registerUnityTestBinaryType(TypeBuilder<UnityTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultUnityTestSuiteBinary.class);
        }

        @ComponentBinaries
        public void createUnityTestBinaries(ModelMap<UnityTestSuiteBinarySpec> binaries,
                                            UnityTestSuiteSpec testSuite,
                                            @Path("buildDir") final File buildDir,
                                            final ServiceRegistry serviceRegistry,
                                            final ITaskFactory taskFactory) {
            createUnityTestSuiteBinaries(binaries, testSuite, "UnityExe", buildDir, serviceRegistry);
        }

        private void createUnityTestSuiteBinaries(ModelMap<UnityTestSuiteBinarySpec> binaries,
                                                  UnityTestSuiteSpec testSuite,
                                                  String typeString,
                                                  final File buildDir,
                                                  ServiceRegistry serviceRegistry ) {
            //For each tested binary
            for (final NativeBinarySpec testedBinary : testedBinariesOf(testSuite)) {
                //Do not generate binaries for shared libraries
                if (testedBinary instanceof SharedLibraryBinary) {
                    continue;
                }
                createNativeTestSuiteBinary(binaries, testSuite, typeString, testedBinary, buildDir, serviceRegistry);
            }
        }

        private void createNativeTestSuiteBinary(ModelMap<UnityTestSuiteBinarySpec> binaries,
                                                 UnityTestSuiteSpec testSuite,
                                                 String typeString,
                                                 final NativeBinarySpec testedBinary,
                                                 final File buildDir, ServiceRegistry serviceRegistry) {

            final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, (NativeBinarySpecInternal) testedBinary, typeString);
            final NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);

            binaries.create(namingScheme.getBinaryName(), UnityTestSuiteBinarySpec.class, new Action<UnityTestSuiteBinarySpec>() {
                @Override
                public void execute(UnityTestSuiteBinarySpec binary) {
                    final NativeTestSuiteBinarySpecInternal testBinary = (NativeTestSuiteBinarySpecInternal)binary;

                    //configure the test binary
                    testBinary.setTestedBinary((NativeBinarySpecInternal) testedBinary);
                    testBinary.setNamingScheme(namingScheme);
                    testBinary.setResolver(resolver);
                    testBinary.setToolChain(testedBinary.getToolChain());

                    //configure the executable
                    NativeExecutableFileSpec executable = testBinary.getExecutable();
                    executable.setToolChain(testedBinary.getToolChain());
                    executable.setFile(executableFileFor(testBinary, buildDir));

                    //configure the install
                    NativeInstallationSpec installation = testBinary.getInstallation();
                    installation.setDirectory(installationDirFor(testBinary, buildDir));

                    //create the install, executable, and run tasks
                    NativeComponents.createInstallTask(testBinary, installation, executable, namingScheme);
                    NativeComponents.createExecutableTask(testBinary, testBinary.getExecutableFile());
                    createRunTask(testBinary, namingScheme.getTaskName("run"));
                }
            });
        }

        private static void createRunTask(final NativeTestSuiteBinarySpecInternal testBinary, String name) {
            testBinary.getTasks().create(name, RunUnityTestExecutable.class, new Action<RunUnityTestExecutable>() {
                @Override
                public void execute(RunUnityTestExecutable runTask) {
                    runTask.setDescription("Runs the " + testBinary);
                    testBinary.getTasks().add(runTask);
                }
            });
        }

        private Collection<NativeBinarySpec> testedBinariesOf(NativeTestSuiteSpec testSuite) {
            return testedBinariesWithType(NativeBinarySpec.class, testSuite);
        }

        private BinaryNamingScheme namingSchemeFor(NativeTestSuiteSpec testSuite, NativeBinarySpecInternal testedBinary, String typeString) {
            return testedBinary.getNamingScheme()
                .withComponentName(testSuite.getBaseName())
                .withBinaryType(typeString)
                .withRole("executable", true);
        }
    }

}
