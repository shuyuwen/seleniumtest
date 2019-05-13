
package seletest;

import org.testng.annotations.Test;

import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.tree.*;
import org.apache.commons.exec.*;
import org.openqa.selenium.*;
import org.testng.annotations.*;

import java.lang.reflect.*;
import java.util.*;
import java.sql.* ;
import java.io.*;

import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;




/**
 * Main testcase class
 * Test information is dynamically read from xml config file and passed to seleTest method to execute.
 */

public class SeleTest {
    /**
     * The threadDriverPool is a over-design for multi-threading testing environment.
     * The test-cases are originally designed to run in parallel using the parallel feature of testNG. <br>
     * However, since now we are only invoking a single test method and testNG does not support running single test
     * method in parallel, no multi-threading bothers now.<br>
     * It's kept here for potential future use.
     */
    // Use ThreadLocal to provide unique driver instances for each thread when testing in a multi-thread
    // environment, since the WebDriver itself is not thread-safe
    private ThreadLocal<AutoDriver> threadDriverPool = new ThreadLocal<>();

    /**
     * Bean class to encapsulate information about a testcase from xml config
     */
    private static class TestCase {
        String testName;
        List<HierarchicalConfiguration<ImmutableNode>> procedure;

        public TestCase(String testName,
                List<HierarchicalConfiguration<ImmutableNode>> procedure) {
            this.testName = testName;
            this.procedure = procedure;
        }
        /**
         * @return Test Object information array in a list
         */
        ArrayList<Object[]> toObjectArrays() {
            ArrayList<Object[]> result = new ArrayList<>();
            result.add(new Object[]{testName, procedure});
            return result;
        }
    }

    /**
     * Dynamically determine which tests needs to be run from xml config and pass related params to main test method.<br>
     * Only tests under &lt;test-to-run&gt; tag and tests that pass the environment check will be run
     *
     * @return return Objects describing tests to run
     */
    //@Parameters({"testfile"})
    @DataProvider(name = "testcaseData")
    public Object[][] testcaseData() {
        //String test_file="/net/slc09xpu/scratch/yshu/test_file.xml";
        System.out.println(".....Data Provider..................\n") ;
        System.out.println("base path:"+ConfigManager.getBasePath());
        HierarchicalConfiguration<ImmutableNode> testConfigRoot,testsToRun;
        testConfigRoot = ConfigManager.getConfigsByName("testcases");
        testsToRun = ConfigManager.getConfigsByName("test-to-run");
        List<HierarchicalConfiguration<ImmutableNode>> tests = testConfigRoot.childConfigurationsAt("");

        ArrayList<TestCase> testInfoPool = new ArrayList<>(); // Hold information to all tests

        // encapsulate xml data into testcase objects
        for (HierarchicalConfiguration<ImmutableNode> test :
                tests) {
                String groupName = test.getString("[@name]", "unknown test");
            testInfoPool.add(new TestCase(groupName,test.childConfigurationsAt(""))) ;
        }


        List<Object> testNames = testsToRun.getList("test");

        ArrayList<Object[]> resultInfoPool = new ArrayList<>();
        System.out.println("[testcaseData] The following tests will be run: ");
        for (Object testName : testNames) {
            for (TestCase test : testInfoPool) {
                if (testName.equals(test.testName)) {
                    System.out.println("    "+test.testName);
                    resultInfoPool.addAll(test.toObjectArrays());
                }
            }
        }
        // can not directly cast the list like
        // <code>(Object[][])testInfoPool.toArray()</code>
        Object[][] result = new Object[resultInfoPool.size()][2];
        for (int i = 0; i < resultInfoPool.size(); i++) {
            result[i] = resultInfoPool.get(i);
        }
        return result;

    }

    //@Parameters({"testfile"})
    @BeforeClass // Create a new running context for each test
    public void OpenURL() {
        // Base wait time for server to respond to tests in other languages
        System.out.println("..........Open URL and get driver........");
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        threadDriverPool.set(new AutoDriver());

        HierarchicalConfiguration<ImmutableNode> loginConfig;
        System.out.println("basePath: "+ConfigManager.getBasePath()) ;
        loginConfig = ConfigManager.getConfigsByName("login");
        String url = loginConfig.getString("url");

        threadDriverPool.get().getDriver().get(url); // open login page

        // Wait for the operation in other languages run previously to take effect
        //AutoDriver autoDriver = threadDriverPool.get();
    }

    /**
     * Close the browser after each testcase is finished.<br>
     * Before closing the browser, wait a short time for the last action on website to take effect, e.g. for a click
     * to resolve and redirect to another page.
     */
    @AfterClass
    public void closeBrowser() {
        try {
            // wait for the last operation to take effect
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        threadDriverPool.get().getDriver().close();
    }

    /**
     * Generic Test Method.
     * Will be invoked by different test names from testcaseData Method.
     *
     * @param testName    Read from xml testcase tag's name attribute
     * @param procedure   the set of operations
     */
    @Test(dataProvider = "testcaseData", alwaysRun = true)
    public void runTest(String testName,
                         List<HierarchicalConfiguration<ImmutableNode>> procedure) {
        String testInfoString = testName ;
        System.out.println("[Run Test] " + testInfoString);
        for (HierarchicalConfiguration<ImmutableNode> operations : procedure) {
            // only execute the operations when the operations group is marked the correct group number
            // or the operations is by default applied to all group(default case group number shall be 0)

            for (HierarchicalConfiguration<ImmutableNode> operation :
                    operations.childConfigurationsAt("")) {
                try {
                    threadDriverPool.get().executeOperation(operation);
                } catch (InvocationTargetException e) {
                    System.err.println("[Fatal] problem encountered in " + testInfoString);
                    System.err.println("[TestFailed] "+ testInfoString);
                    return;
                }
            }
            System.out.println("[Test Passed] " + testInfoString);
        }
    }
}
