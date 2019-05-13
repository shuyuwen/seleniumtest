package seletest;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.tree.*;
import org.apache.commons.exec.*;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.*;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.support.ui.*;

import java.lang.reflect.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.Date;
import java.util.regex.*;

import org.openqa.selenium.JavascriptExecutor ;

public class AutoDriver {
    /**
     * Default wait time for any selenium wait until, after which a exception will be thrown.
     */
    private static final int WAIT_TIME = 20;

    private WebDriver driver;
    private String WindowHandler;

    /**
     * Provide access to other classes if they want to perform any actions which are not defined in AutoDriver
     *
     * @return WebDriver instance
     */
    public WebDriver getDriver() {
        return driver;
    }

    /**
     * Constructor, creates a new Remote/Local WebDriver instance based on param.
     *
     * @param isRunOnCloud whether to create a RemoteDriver or a LocalDriver
     */

    public AutoDriver() {
        HierarchicalConfiguration<ImmutableNode> driverConfig;
        driverConfig = ConfigManager.getConfigsByName("webdriver");
        String remote_webdriver = driverConfig.getString("remote") ;
        String remoteURL = driverConfig.getString("remoteURL") ;
        String myProxy = driverConfig.getString("Proxy");
        System.out.println(remote_webdriver+"  "+remoteURL);

        if (remote_webdriver.equals("true") ){
            DesiredCapabilities dc = DesiredCapabilities.firefox();

            // Read key value pairs from config files
            //HierarchicalConfiguration<ImmutableNode> driverConfig = ConfigManager.getConfigsByName("webdriver");
            List<String> specNames = driverConfig.getList(String.class, "specs.spec[@name]");
            List<String> specValues = driverConfig.getList(String.class, "specs.spec[@value]");
            assert specNames.size() == specValues.size();

            for (int i = 0; i < specNames.size(); ++i) {
                        Object value = null;
                        String valueString = specValues.get(i);
                        if ("true".equals(valueString) || "false".equals(valueString)) {
                            value = Boolean.parseBoolean(valueString);
                        } else {
                            value = valueString;
                        }
                dc.setCapability(specNames.get(i), value);
            }

            try {
                //URL weburl = new URL(remoteURL) ;//"http://gat.us.oracle.com:8888/wd/hub") ;
                driver = new RemoteWebDriver(new URL(remoteURL),dc);
                //driver = new RemoteWebDriver(new URL("http://slc09fkn.us.oracle.com:4444/wd/hub"),dc);
                } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        } else {
            if(myProxy!=""){
                DesiredCapabilities capabilities= new DesiredCapabilities();
                capabilities.setCapability(CapabilityType.PROXY, new org.openqa.selenium.Proxy().setHttpProxy(myProxy));
                this.driver = new FirefoxDriver(capabilities);
            }
            this.driver.manage().window().maximize();
        }

//        Might need global wait for element in the future
//        this.driver.manage().timeouts().implicitlyWait(500, TimeUnit.MILLISECONDS);
    }

    /**
     * Dispatch the operation execution to more specific methods using reflection.
     * The method names are in the form executeXXX, where "XXX" is the operation name specified in config file.
     *
     * @param operation The xml node describing an operation
     *                  This node must has attribute "name"
     */
    public void executeOperation(HierarchicalConfiguration<ImmutableNode> operation) throws InvocationTargetException{
        try {
            Method exeOp = this.getClass().getDeclaredMethod("execute" + operation.getString("[@name]"),
                    HierarchicalConfiguration.class);
            exeOp.setAccessible(true);
            exeOp.invoke(this, operation);
        } catch (NoSuchMethodException nme) {
            System.out.println("[Error] Invalid operation type: \"" + operation.getString(
                    "[@name]") + "\" specified in config xml");
            System.out.println(nme.getMessage());
            System.exit(0);
        } catch (IllegalAccessException e) {
            System.err.println("[Fatal] This exception branch should never be reached!");
            e.printStackTrace();
            System.exit(0);
        } catch (InvocationTargetException e) {
            System.err.println("[Fatal] In executing operation " + operation.getString("[@name]"));
            //e.printStackTrace();
            //System.out.println(e.getCause().toString());
            String temp=e.getCause().toString();
            int firstline=temp.indexOf('\n');
            System.out.println(temp.substring(0,firstline));
            throw e;
        }
    }


    /* open URL */
    private void executeOpenURL(HierarchicalConfiguration<ImmutableNode> operation) {
        String url = operation.getString("[@url]");
        this.driver.get(url); // open login page
    }


    /* display information */
    private void executeDisplay(HierarchicalConfiguration<ImmutableNode> operation) {
        String info = operation.getString("[@information]") ;
        System.out.println("........"+info+"........") ;
    }


    /**
     * Simulate a keyboard action to an input box
     *
     * @param operation the same object passed from executeOperation
     *                  must have attribute "value"
     */
    private void executeKeyboardInput(HierarchicalConfiguration<ImmutableNode> operation) {
        String inputString = operation.getString("[@value]");

        assert inputString != null;
        System.out.println("KeyboardInput:"+inputString);
        if (operation.getString("[@dynamic-time-stamp]") != null) {
            inputString = inputString + new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss").format(new Date());
        }
        if (operation.getString("[@relative-path]") != null) {
            inputString = ConfigManager.getBasePath() + inputString;
        }
        WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);
        WebElement inputBox = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath(operation.getString("[@xpath]"))));


        inputBox.click();
        inputBox.clear();
        inputBox.sendKeys(inputString);
        //System.out.println("Double Check:"+inputBox.getAttribute("value")) ;
        if (!inputBox.getAttribute("value").equals(inputString)) {
                System.out.println("not match, input again");
                inputBox.click();
            inputBox.clear();
            inputBox.sendKeys(inputString);
        }
    }

    /**
     * Continue to click action on an element specified in the xpath attribute of xml node until another element appears
     *
     * @param operation the same object passed from executeOperation
     *                  must have attribute "xpath"
     */
    private void executeClickUntil(HierarchicalConfiguration<ImmutableNode> operation) {
        String xpath = operation.getString("[@xpath]") ;
        String another_element = operation.getString("[@value]") ;
        assert xpath != null;
        By byXpath = By.xpath(another_element) ;
        By loc = By.xpath(xpath);

        while (this.driver.findElements(byXpath).isEmpty())
            executeClickbyLoc(loc);
    }


    /*
     * click the element if its attribute has a specific value
     */
    private void executeClickifHasValue(HierarchicalConfiguration<ImmutableNode> operation) {
        String xpath = operation.getString("[@xpath]") ;
        String attr = operation.getString("[@attr]");
        String has_value = operation.getString("[@value]") ;
        System.out.println(xpath+" "+attr+" "+has_value);
        assert xpath != null;
        By loc = By.xpath(xpath) ;
        WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);

        wait.until(ExpectedConditions.presenceOfElementLocated(loc));

        System.out.println("attr value:"+ this.driver.findElement(By.xpath(xpath)).getAttribute(attr)) ;

        if (this.driver.findElement(loc).getAttribute(attr).equals(has_value) ) {
                System.out.println("ClickIf :"+xpath+" has value of "+ attr );
                executeClickbyLoc(loc) ;
        }
        else
                System.out.println("ClickIfHasValue :"+xpath+" has no value of "+ attr );
    }

    /*
     * click the element if its attribute don't have a specific value
     */
    private void executeClickifNoValue(HierarchicalConfiguration<ImmutableNode> operation) {
        String xpath = operation.getString("[@xpath]") ;
        String attr = operation.getString("[@attr]");
        String has_value = operation.getString("[@value]") ;
        System.out.println("Click if "+ xpath+" has no value of "+attr+" "+has_value);
        assert xpath != null;
        By loc = By.xpath(xpath) ;
        WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);
        wait.until(ExpectedConditions.presenceOfElementLocated(loc));

        if (this.driver.findElement(loc).getAttribute(attr)== null)
             executeClickbyLoc(loc) ;
    }


    /*
     * click the element if it exists
     */
    private void executeClickifExist(HierarchicalConfiguration<ImmutableNode> operation) {
        String xpath = operation.getString("[@xpath]") ;

        System.out.println("Click if "+ xpath+" exists ");
        assert xpath != null;

        By loc = By.xpath(xpath);
        if (!this.driver.findElements(loc).isEmpty())
             executeClickbyLoc(loc) ;
    }
    /**
     * Simulate a click action on an element specified in the xpath attribute of xml node
     *
     * @param operation the same object passed from executeOperation
     *                  must have attribute "xpath"
     */
    private void executeClick(HierarchicalConfiguration<ImmutableNode> operation) {
        String xpath = operation.getString("[@xpath]");
        String link = operation.getString("[@link]");
        String css = operation.getString("[@css]");
        By loc=null;

        if (xpath!=null) {
                System.out.println("Click:"+xpath);
                loc = By.xpath(xpath) ;
        }
        else if (link != null) {
                System.out.println("Click:"+link);
                loc = By.partialLinkText(link) ;
        }
        else if (css != null) {
                System.out.println("Click:"+link);
                loc = By.cssSelector(css) ;
        }

        executeClickbyLoc(loc);
    }

    private void executeClickbyLoc(By loc) {
        assert loc != null;
        WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);

        wait.until(ExpectedConditions.presenceOfElementLocated(loc));
        wait.until(ExpectedConditions.visibilityOfElementLocated(loc));
        wait.until(ExpectedConditions.elementToBeClickable(loc));

        for(int i=1; i<4; i++){
             try {
                this.driver.findElement(loc).click();
                break ;
            }catch (Exception e) {
                 if(i!=3){
                     System.out.println(e.getMessage());
                     System.out.println("Element "+loc.toString()+"not Clickable");
                     wait.until(ExpectedConditions.elementToBeClickable(loc));
                 }
                 else {
                     WebElement element = driver.findElement(loc);
                     JavascriptExecutor js=(JavascriptExecutor) driver;
                     js.executeScript("arguments[0].click();",element);
                 }
            }
        }
    }

    /**
     * Simulate a dropdown select action on an element specified in the xpath attribute of xml node.
     *
     * @param operation the same object passed from executeOperation
     *                  must have attribute "xpath" and "select-index"
     */
    private void executeSelectDropDown(HierarchicalConfiguration<ImmutableNode> operation) {
        System.out.println("SelectDropDown:"+operation.getString("[@xpath]"));
        WebElement selectElement = new WebDriverWait(this.driver, WAIT_TIME).until(
                ExpectedConditions.visibilityOfElementLocated(By.xpath(operation.getString("[@xpath]"))));
        Select dropdown = new Select(selectElement);

        if (operation.containsKey("[@select-index]")) {
           By loc = By.xpath(operation.getString("[@xpath]")+"/option["+operation.getInt("[@select-index]")+"]") ;
           WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);

        wait.until(ExpectedConditions.presenceOfElementLocated(loc));
        wait.until(ExpectedConditions.visibilityOfElementLocated(loc));
        wait.until(ExpectedConditions.elementToBeClickable(loc));
            dropdown.selectByIndex(operation.getInt("[@select-index]"));
        }
        else if (operation.containsKey("[@select-text]")) {
            By loc = By.xpath(operation.getString("[@xpath]")+"//option[text()='"+operation.getString("[@select-text]")+"']") ;
            WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);

            wait.until(ExpectedConditions.presenceOfElementLocated(loc));
            wait.until(ExpectedConditions.visibilityOfElementLocated(loc));
            wait.until(ExpectedConditions.elementToBeClickable(loc));
            dropdown.selectByVisibleText(operation.getString("[@select-text]"));
        }
        else
                System.out.println("Nothing found");
    }

    /* handle pop-up window */
    private void executeSelectPopupWindow(HierarchicalConfiguration<ImmutableNode> operation) {
        this.WindowHandler = this.driver.getWindowHandle(); // Store your parent window
        System.out.println("WindowHandler:"+this.WindowHandler) ;
        String subWindowHandler = null;

        WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);
        Set<String> handles = this.driver.getWindowHandles(); // get all window handles


        System.out.println("total windows:"+handles.size());
        Iterator<String> iterator = handles.iterator();
        while (iterator.hasNext()){
                subWindowHandler = iterator.next();
                this.driver.switchTo().window(subWindowHandler);
                System.out.println("sub window:"+subWindowHandler);
                System.out.println("sub window title:"+this.driver.getTitle());
                System.out.println("title from xml:"+operation.getString("[@xpath]"));
            if (this.driver.getTitle() == operation.getString("[@xpath]")) {
                this.driver.switchTo().window(subWindowHandler);
                    System.out.println("swith to subwindow:"+subWindowHandler) ;
                        break ;
            };
        }
    }

    /* switch to parent window */
    private void executeBacktoParentWindow(HierarchicalConfiguration<ImmutableNode> operation) {
        Set<String> handles = this.driver.getWindowHandles(); // get all window handles
        //System.out.println("total window:"+handles.size());
        this.driver.switchTo().window(this.WindowHandler);
        System.out.println("Now we back to pararen window"+this.WindowHandler+"  "+this.driver.getWindowHandle());
    }

    /* handle frame */
    private void executeSelectFrame(HierarchicalConfiguration<ImmutableNode> operation) {
        try{
            TimeUnit.SECONDS.sleep(2);
        }catch(InterruptedException  ex){

        }
        String new_frame = operation.getString("[@xpath]");
        System.out.println("switch to new frame:"+ new_frame);
        this.WindowHandler = this.driver.getWindowHandle();
        if (new_frame == "")
            this.driver.switchTo().frame(0);
        else{

            int convertedInt=Integer.parseInt(new_frame);
            WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(convertedInt));

            // do{
            //  System.out.println(driver.findElements(By.tagName("iframe")).size());
            // }
            // while( driver.findElements(By.tagName("iframe")).size()<convertedInt+1);
            // System.out.println("total frame "+ driver.findElements(By.tagName("iframe")).size());

             //this.driver.switchTo().frame(new_frame);
            // this.driver.switchTo().frame(convertedInt);
        }
    }




    /* input string in richEdit */
    private void executeRicheditInput(HierarchicalConfiguration<ImmutableNode> operation) {
        WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME);
        WebElement richEdit = wait.until(
        ExpectedConditions.presenceOfElementLocated(By.xpath(operation.getString("[@xpath]"))));
        String inputString= operation.getString("[@value]");
        assert inputString !=null;
        System.out.println("KeyboardInput: "+ inputString);
        // richEdit.clear();
        // richEdit.sendKeys(inputString);
        JavascriptExecutor jsExecutor=(JavascriptExecutor) driver;
        jsExecutor.executeScript("document.querySelector('span[cm-text]').innerHTML="+"'"+inputString+"'"+";");
    }

    /**
     * Simulate a checkbox select.
     *
     * @param operation the same object passed from executeOperation
     *                  must have attribute "xpath" and "select"
     */
    private void executeSelectCheckBox(HierarchicalConfiguration<ImmutableNode> operation) {
        boolean boxState = this.driver.findElement(
                By.xpath(operation.getString("[@xpath]"))).isSelected();
        boolean expectedState = operation.getBoolean("[@select]");
        if (boxState != expectedState) {
            executeClick(operation);
        }
    }

    /**
     * Sometimes there is modal closing time delay for some ui element to be overlapped by the modal element. <br>
     * This function will make the browser wait until the modal element disappear.
     *
     * @param operation the same object passed from executeOperation
     *                  must have attribute "xpath"
     */
    private void executeWaitInvisible(HierarchicalConfiguration<ImmutableNode> operation) {
        new WebDriverWait(this.driver, WAIT_TIME)
                .until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(operation.getString("[@xpath]"))));

    }
    public enum WaitCondition {APPEAR, DISAPPAER}

    public void executeWaitConditionRepeatedly(String xpath, String refreshXpath, WaitCondition waitCondition) {
        while (true) {
            // refresh
            System.out.println("Refreshing...");
            if (refreshXpath != null) {
                executeClickbyLoc(By.xpath(refreshXpath)); // if specified, click certain element on page to refresh
            } else {
                this.driver.navigate().refresh();
            }

            System.out.println("Wait repeatedly");
            try {
                Thread.sleep(
                        ConfigManager.getConfigsByName("wait-refresh-interval")
                                .getInt("") * 1000); // Sleep 2 min, can not wait too long. If too long the browser will ask to login again
            } catch (InterruptedException e) {
                // e.printStackTrace();
                String temp=e.getCause().toString();
                int firstline=temp.indexOf('\n');
                System.out.println(temp.substring(0,firstline));
                // throw e;
            }

            List<WebElement> elements = this.driver.findElements(By.xpath(xpath));
            System.out.println("Finding " + elements.size() + " element(s): " + xpath);

            if (waitCondition == WaitCondition.APPEAR && elements.size() > 0) break;
            else if (waitCondition == WaitCondition.DISAPPAER && elements.size() == 0) break;
        }
    }

    /**
     * There is need to wait for all other tests to finish until we can start the next test case
     * So there is need to wait until a certain no test running message appear
     *
     * @param operation the same object passed from executeOperation
     */
    private void executeWaitAppearRepeatedly(HierarchicalConfiguration<ImmutableNode> operation) {
        String refreshXpath = operation.getString("[@customize-refresh-xpath]");
        String xpath = operation.getString("[@xpath]");
        executeWaitConditionRepeatedly(xpath, refreshXpath, WaitCondition.APPEAR);
    }

    private void executeWaitforAppear(HierarchicalConfiguration<ImmutableNode> operation) {

          String xpath = operation.getString("[@xpath]");
          System.out.println("Wait for "+xpath+" appearing.") ;

          assert xpath != null;
          WebDriverWait wait = new WebDriverWait(this.driver, WAIT_TIME*3);
          By byXpath = By.xpath(xpath);
          wait.until(ExpectedConditions.presenceOfElementLocated(byXpath));
          wait.until(ExpectedConditions.visibilityOfElementLocated(byXpath));
         // wait.until(ExpectedConditions.elementToBeClickable(byXpath));
          System.out.println(xpath+" appears!") ;
    }

    /**
     * This method provide a way to wait until those effects take place.
     *
     * @param operation the same object passed from executeOperation
     */
    private void executeWaitDisappearRepeatedly(HierarchicalConfiguration<ImmutableNode> operation) {
        String refreshXpath = operation.getString("[@customize-refresh-xpath]");
        String xpath = operation.getString("[@xpath]");
        executeWaitConditionRepeatedly(xpath, refreshXpath, WaitCondition.DISAPPAER);
    }

    /**
     * There is need to locate an element when another element exist in the same row in a table
     * The row index relationship could be found on @id pattern
     *
     * @param operation the same object passed from executeOperation
     * @deprecated This method is no longer used in the xml config, and will no longer be supported in future version
     */
    @Deprecated
    private void executeClickIfAnotherElementExist(HierarchicalConfiguration<ImmutableNode> operation) {
        HierarchicalConfiguration<ImmutableNode> elementConfig = operation.configurationAt("element");
        // get the element attr string to extract row index
        String elementAttr = this.driver.findElement(By.xpath(elementConfig.getString("[@xpath]")))
                .getAttribute(elementConfig.getString("[@extract-attribute]"));
        Pattern pattern = Pattern.compile(elementConfig.getString("[@attribute-regex]"));
        Matcher matcher = pattern.matcher(elementAttr);
        if (matcher.find()) {
            System.out.println(matcher.group(1));
            String filler = matcher.group(1); // row index extracted
            String xpath = operation.getString("[@xpath]")
                    .replace("?", filler); // dynamically generate xpath to match certain element in that row
            executeClickbyLoc(By.xpath(xpath));
        } else {
            // TODO error handling
            System.err.println("[Error] No matching found!");
        }
    }
}


