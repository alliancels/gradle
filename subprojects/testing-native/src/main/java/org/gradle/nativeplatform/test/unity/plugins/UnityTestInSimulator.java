package org.gradle.nativeplatform.test.unity.plugins;

import org.gradle.api.*;
import org.gradle.model.*;
import org.gradle.nativeplatform.test.unity.UnitySimulator;
import org.gradle.nativeplatform.test.unity.UnityTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.unity.tasks.RunUnityTestExecutable;

import java.io.File;

@Incubating
public class UnityTestInSimulator implements Plugin<Project>{

    static final String ExeArgString = "#EXE";

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(UnityPlugin.class );
    }

    static class Rules extends RuleSource{

        @Model
        void unitySimulator(UnitySimulator simulator){ }

        @Finalize
        void configureRunTask(@Each UnityTestSuiteBinarySpec binary, UnitySimulator simulator) {

            final RunUnityTestExecutable runTask = (RunUnityTestExecutable)binary.getTasks().getRun();

            //configure the arguments, substituting #EXE for the executable file path
            for(String arg: simulator.getArgs()) {
                if(arg.equals(ExeArgString)) {
                    runTask.args(binary.getExecutableFile().toString());
                }
                else {
                    runTask.args(arg);
                }
            }

            //set the executable to the simulator path
            runTask.setExecutable(simulator.getSimulatorPath());
        }

        @Validate
        void validateSimulatorPath(final UnitySimulator simulator) {

            //The simulator path must be defined
            if(simulator.getSimulatorPath() == null) {
                throw new GradleException("unitySimulator.simulatorPath property must be defined.");
            }

            //and must point to a file
            File simulatorFile = new File(simulator.getSimulatorPath());
            if(!simulatorFile.exists()) {
                System.out.println("File: " + simulatorFile.toString());
                throw new GradleException(("unitySimulator.simulatorPath must point to a file "));
            }
        }

        @Validate
        void validateArgsContainsExe(final UnitySimulator simulator)
        {
            //arguments must contain the executable argument
            if(!simulator.getArgs().contains(ExeArgString))
            {
                throw new GradleException("unitySimulator.args must contain an argument with value '#EXE'" +
                    ", to be substituted with the output executable path.");
            }
        }
    }
}
