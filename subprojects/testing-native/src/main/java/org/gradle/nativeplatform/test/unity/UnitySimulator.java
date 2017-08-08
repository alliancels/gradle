package org.gradle.nativeplatform.test.unity;

import org.gradle.model.Managed;

import java.util.List;

@Managed
public interface UnitySimulator{

    String getSimulatorPath();
    void setSimulatorPath(String simPath);

    List<String> getArgs();
}
