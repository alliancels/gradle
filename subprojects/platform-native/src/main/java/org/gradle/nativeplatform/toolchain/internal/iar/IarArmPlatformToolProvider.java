/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.*;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.Map;

public class IarArmPlatformToolProvider extends AbstractPlatformToolProvider {
    private final Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations;
    IarArmInstall iarArm;
    private final NativePlatformInternal targetPlatform;
    private final ExecActionFactory execActionFactory;

    IarArmPlatformToolProvider(BuildOperationProcessor buildOperationProcessor, OperatingSystemInternal operatingSystem,
                               Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations,
                               IarArmInstall iarArm, NativePlatformInternal targetPlatform, ExecActionFactory execActionFactory) {
        super(buildOperationProcessor, operatingSystem);
        this.commandLineToolConfigurations = commandLineToolConfigurations;
        this.iarArm = iarArm;
        this.targetPlatform = targetPlatform;
        this.execActionFactory = execActionFactory;
    }

    @Override
    public String getStaticLibraryName(String libraryName) {
        return super.getStaticLibraryName(libraryName).replaceFirst("\\.lib", ".a");
    }

    @Override
    public String getExecutableName(String executablePath) {
        return super.getExecutableName(executablePath).replaceFirst("\\.exe", ".out");
    }

    @Override
    protected Compiler<CppCompileSpec> createCppCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C++ compiler", iarArm.getCompiler(targetPlatform));
        CppCompiler cppCompiler = new CppCompiler(buildOperationProcessor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), addIncludePathAndDefinitions(CppCompileSpec.class), getObjectFileExtension(), true);
        return new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, getObjectFileExtension());
    }

    @Override
    protected Compiler<CCompileSpec> createCCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C compiler", iarArm.getCompiler(targetPlatform));
        CCompiler cCompiler = new CCompiler(buildOperationProcessor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.C_COMPILER)), addIncludePathAndDefinitions(CCompileSpec.class), getObjectFileExtension(), true);
        return new OutputCleaningCompiler<CCompileSpec>(cCompiler, getObjectFileExtension());
    }

    @Override
    protected Compiler<AssembleSpec> createAssembler() {
        CommandLineToolInvocationWorker commandLineTool = tool("Assembler", iarArm.getAssembler(targetPlatform));
        return new Assembler(buildOperationProcessor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.ASSEMBLER)), addIncludePathAndDefinitions(AssembleSpec.class), getObjectFileExtension(), true);
    }

    @Override
    protected Compiler<LinkerSpec> createLinker() {
        CommandLineToolInvocationWorker commandLineTool = tool("Linker", iarArm.getLinker(targetPlatform));
        return new Linker(buildOperationProcessor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.LINKER)), addLibraryPath());
    }

    @Override
    protected Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        CommandLineToolInvocationWorker commandLineTool = tool("Static library archiver", iarArm.getArchiver(targetPlatform));
        return new StaticLibraryArchiver(buildOperationProcessor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.STATIC_LIB_ARCHIVER)), Transformers.<StaticLibraryArchiverSpec>noOpTransformer());
    }

    private CommandLineToolInvocationWorker tool(String toolName, File exe) {
        return new DefaultCommandLineToolInvocationWorker(toolName, exe, execActionFactory);
    }

    private CommandLineToolContext context(CommandLineToolConfigurationInternal commandLineToolConfiguration) {
        MutableCommandLineToolContext invocationContext = new DefaultMutableCommandLineToolContext();
        invocationContext.addPath(iarArm.getCommonPath(targetPlatform));
        // Clear environment variables that might effect cl.exe & link.exe
        clearEnvironmentVars(invocationContext, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB");

        invocationContext.setArgAction(commandLineToolConfiguration.getArgAction());
        return invocationContext;
    }

    private void clearEnvironmentVars(MutableCommandLineToolContext invocation, String... names) {
        // TODO: This check should really be done in the compiler process
        Map<String, ?> environmentVariables = Jvm.current().getInheritableEnvironmentVariables(System.getenv());
        for (String name : names) {
            Object value = environmentVariables.get(name);
            if (value != null) {
                IarArmToolChain.LOGGER.warn("Ignoring value '{}' set for environment variable '{}'.", value, name);
                invocation.addEnvironmentVar(name, "");
            }
        }
    }

    private <T extends NativeCompileSpec> Transformer<T, T> addIncludePathAndDefinitions(Class<T> type) {
        return new Transformer<T, T>() {
            public T transform(T original) {
                original.include(iarArm.getIncludePath(targetPlatform));
                for (Map.Entry<String, String> definition : iarArm.getDefinitions(targetPlatform).entrySet()) {
                    original.define(definition.getKey(), definition.getValue());
                }
                return original;
            }
        };
    }

    private Transformer<LinkerSpec, LinkerSpec> addLibraryPath() {
        return new Transformer<LinkerSpec, LinkerSpec>() {
            public LinkerSpec transform(LinkerSpec original) {
                original.libraryPath(iarArm.getLibraryPath(targetPlatform));
                return original;
            }
        };
    }

    @Override
    public String getObjectFileExtension() {
        return ".o";
    }
}
