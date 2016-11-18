package org.gradle.nativeplatform.toolchain.internal.iar;

import com.google.common.collect.Lists;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import java.io.File;
import java.util.List;

/**
 * Maps common options for ASM compiling with IAR
 */
public class IarArmAssemblerArgsTransformer<T extends NativeCompileSpec> implements ArgsTransformer<T> {
    @Override
    public List<String> transform(T spec) {
        List<String> args = Lists.newArrayList();
        addIncludeArgs(spec, args);
        addMacroArgs(spec, args);
        addUserArgs(spec, args);
        addToolSpecificArgs(spec, args);
        return args;
    }

    protected void addToolSpecificArgs(T spec, List<String> args) {
        args.add(getLanguageOption());
    }

    protected void addIncludeArgs(T spec, List<String> args) {
        for (File file : spec.getIncludeRoots()) {
            args.add("-I" + file.getAbsolutePath());
        }
    }

    protected void addMacroArgs(T spec, List<String> args) {
        for (String macroArg : new MacroArgsConverter().transform(spec.getMacros())) {
            args.add("-D" + macroArg);
        }
    }

    protected void addUserArgs(T spec, List<String> args) {
        args.addAll(spec.getAllArgs());
    }

    /**
     * Returns compiler specific language option
     * @return compiler language option or empty string if the language does not require it
     */
    protected String getLanguageOption() {
        return "";
    }
}
