package org.gradle.nativeplatform.test.unity.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A task to run a unity test executable and monitor the output for failed tests.
 */
public class RunUnityTestExecutable extends RunTestExecutable{

    /**
     * Regular expression used to detect a unity test run summary
     */
    private static final Pattern summaryPattern = Pattern.compile("\\s*(\\d+)\\s+Tests\\s+(\\d+)\\s+Failures\\s+(\\d+)\\s+Ignored\\s*");
    private static final String OutputFileName = "output.txt";

    @TaskAction
    @Override
    protected void exec(){
        //setup an output stream to capture the task output
        ByteArrayOutputStream out = new ByteArrayOutputStream(64*1024);
        this.setStandardOutput(out);

        //execute the task
        super.exec();

        //get the process output
        byte[] rawOutput = out.toByteArray();

        //write output to stdout and the output file
        echoToStdOut(rawOutput);
        writeToOutputFile(rawOutput);

        //search the output for failures
        ByteArrayInputStream in = new ByteArrayInputStream(rawOutput);
        searchForFailures(in);
    }

    /**
     * Writes task output to standard output.
     * @param output The bytes output by the task
     */
    private void echoToStdOut(byte[] output)
    {
        try {
            System.out.write(output);
        }
        catch (IOException e) {
            //warn of the failed std output
            getLogger().warn("Failed to write test output to the standard output stream", e);
        }
    }

    /**
     * Writes task output to a file
     * @param output The bytes output by the task
     */
    private void writeToOutputFile(byte[] output)
    {
        File outputFile = new File(getOutputDir(), OutputFileName);
        try{
             FileOutputStream outputFileStream = new FileOutputStream(new File(getOutputDir(), OutputFileName));
            try{
                outputFileStream.write(output);
            }
            finally {
                outputFileStream.close();
            }
        }
        catch(IOException e) {
            //warn of the filed output file
            getLogger().warn("Failed to write test output file: " + outputFile, e);
        }
    }

    /**
     * Searches for a Unity test run summary in an input stream.
     *
     * If the summary is not found, or there are failing tests,
     * a gradle exception is thrown
     * @param in
     */
    private void searchForFailures(InputStream in)
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        //we need to find the summary pattern
        boolean foundSummary = false;
        try {
            String line = reader.readLine();
            while (line != null && !foundSummary) {

                //get a matcher for the current line
                Matcher summaryMatcher;
                summaryMatcher = summaryPattern.matcher(line);

                //if we found the summary
                if(summaryMatcher.matches())
                {
                    foundSummary = true;

                    //check the number of failures
                    if(Integer.parseInt(summaryMatcher.group(2)) != 0)
                    {
                        handleTestFailures();
                    }
                }

                //read the next line
                line = reader.readLine();
            }
        }
        //This should never happen
        catch(IOException e)
        {
            throw new GradleException("Failed to read unity test output stream", e);
        }

        //Fail if the summary was never found
        if(!foundSummary)
        {
            throw new GradleException("Failed to find Unity test summary in test output");
        }
    }

    private void handleTestFailures() {
        String message = "There were failing tests";
        String resultsUrl = new ConsoleRenderer().asClickableFileUrl(getOutputDir());
        message = message.concat(". See the results at: " + resultsUrl);

        if (isIgnoreFailures()) {
            getLogger().warn(message);
        } else {
            throw new GradleException(message);
        }

    }
}
