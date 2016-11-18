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
import org.gradle.api.UncheckedIOException;

import org.gradle.internal.process.ArgCollector;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class IarArgWriter implements ArgCollector {
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private final PrintWriter writer;
    private final boolean backslashEscape;

    private IarArgWriter(PrintWriter writer, boolean backslashEscape) {
        this.writer = writer;
        this.backslashEscape = backslashEscape;
    }

    /**
     * Double quotes around args containing whitespace, platform line separators.
     */
    public static IarArgWriter windowsStyle(PrintWriter writer) {
        return new IarArgWriter(writer, false);
    }

    public static Transformer<IarArgWriter, PrintWriter> windowsStyleFactory() {
        return new Transformer<IarArgWriter, PrintWriter>() {
            public IarArgWriter transform(PrintWriter original) {
                return windowsStyle(original);
            }
        };
    }

    /**
     * Returns an args transformer that replaces the provided args with a generated args file containing the args. Uses platform text encoding.
     */
    public static Transformer<List<String>, List<String>> argsFileGenerator(final File argsFile, final Transformer<IarArgWriter, PrintWriter> argWriterFactory) {
        return new Transformer<List<String>, List<String>>() {
            @Override
            public List<String> transform(List<String> args) {
                if (args.isEmpty()) {
                    return args;
                }
                argsFile.getParentFile().mkdirs();
                try {
                    PrintWriter writer = new PrintWriter(argsFile);
                    try {
                        IarArgWriter argWriter = argWriterFactory.transform(writer);
                        argWriter.args(args);
                    } finally {
                        writer.close();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(String.format("Could not write options file '%s'.", argsFile.getAbsolutePath()), e);
                }
                return Arrays.asList("-f", argsFile.getAbsolutePath());

//                return Collections.singletonList("-f " + argsFile.getAbsolutePath());
            }
        };
    }

    /**
     * Writes a set of args on a single line, escaping and quoting as required.
     */
    public IarArgWriter args(Object... args) {
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (i > 0) {
                writer.print(' ');
            }
            String str = arg.toString();
            if (backslashEscape) {
                str = str.replace("\\", "\\\\").replace("\"", "\\\"");
            }
            if (WHITESPACE.matcher(str).find()) {
                writer.print('\"');
                writer.print(str);
                writer.print('\"');
            } else {
                writer.print(str);
            }
        }
        writer.println();
        return this;
    }

    public ArgCollector args(Iterable<?> args) {
        for (Object arg : args) {
            args(arg);
        }
        return this;
    }
}
