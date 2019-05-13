package seletest;

import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.builder.fluent.*;
import org.apache.commons.configuration2.ex.*;
import org.apache.commons.configuration2.tree.*;
import org.xml.sax.*;

import javax.xml.*;
import javax.xml.transform.stream.*;
import javax.xml.validation.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class ConfigManager {
    private Configurations configs = null;
    private XMLConfiguration config = null;
    private String basePath = ConfigManager.class.getProtectionDomain().getCodeSource().getLocation().getPath().toString() ;


    public static String getBasePath() {
        return instance.basePath;
    }

    private String configPath = basePath + "testng.xml" ;
    //private String schemaPath = basePath + "test_schema.xsl" ;

    private static ConfigManager instance = new ConfigManager();

    /**
     * The method for accessing all configs. <br>
     * configFieldName are names of sub tags under the root "&lt;config&gt;" tag
     *
     * @param configFieldName the name of config field e.g. webdriver, etc.
     * @return HierarchicalConfiguration&lt;ImmutableNode&gt;
     */
    public static HierarchicalConfiguration<ImmutableNode> getConfigsByName(String configFieldName) {
        return instance.config.configurationAt(configFieldName);
    }

    // Change CLOB to String
    private static String ClobToString(Clob p_Clob) throws SQLException, IOException {

        String  reString;
        Reader  is = p_Clob.getCharacterStream();
        BufferedReader br = new BufferedReader(is);
        String s = br.readLine();
        StringBuilder sb = new StringBuilder();
        while (s != null)
        {
            sb.append(s);
            s = br.readLine();
        }
        reString = sb.toString();
        return reString;
    }

    private ConfigManager() {
        System.out.println("..........configManager constructor..........");
        this.configs = new Configurations();
        String test_file="";
        File file = new File(configPath);
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance() ;

        // if specify testfile parameter in testng.xml, read the testfile name
        try{
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(file);

            NodeList nodeList = document.getElementsByTagName("parameter");
            for(int x=0,size= nodeList.getLength(); x<size; x++) {
                String name=nodeList.item(x).getAttributes().getNamedItem("name").getNodeValue();
                name=name.trim();
                name=name.replaceAll("\n", "");
                System.out.println(name);
                if( name.equals("testfile") ) {
                    test_file=nodeList.item(x).getAttributes().getNamedItem("value").getNodeValue();
                    System.out.println(test_file);
                }
            }
        }catch(Exception e){
            System.out.println(e.toString());
        }

        // create configManager from the specified testfile
        if(test_file!=""){
            System.out.println("..........configManager constructor(by testfile)..........");
            this.configs = new Configurations();
            File temp = new File(test_file);
            try{
                this.config = configs.xml(temp);
            }catch(Exception e){
                System.out.println(e.toString());
            }
        }
        // if no testfile specified in testng.xml, generate testfile from database
        else{
            try {
                String m_szDBURL = "jdbc:oracle:thin:@bej301445.cn.oracle.com:1521/pdbrobotjulie.sgtdbcluster.cn";
                String m_szDBUser = "yshu";
                String m_szDBPass = "syw1029";

                DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());
                Connection m_conn = DriverManager.getConnection (m_szDBURL,m_szDBUser, m_szDBPass);
                m_conn.setAutoCommit(false);

                File temp = File.createTempFile("_temp", ".xml");
                temp.deleteOnExit();
                OutputStreamWriter m_OutputWriter = new OutputStreamWriter(new FileOutputStream(temp),"UTF-8");

                Statement m_Stmt = m_conn.createStatement();
                ResultSet m_preRs = m_Stmt.executeQuery("select step from db_auto_testcase where testname='test_to_run'") ;
                m_preRs.next();
                String tests = "Select id,testname,step from db_auto_testcase where  id=1 or id=2 or id=999999 or '"+m_preRs.getString("step")+"' like '%'||testname||'%'  order by id  ";

                 ResultSet m_Rs = m_Stmt.executeQuery(tests);
                 String      m_XMLContent  = "";

                 while (m_Rs.next())
                 {
                     java.sql.Clob m_Clob = m_Rs.getClob("step") ;
                     m_XMLContent = m_XMLContent+ClobToString(m_Clob);
                 }
                 m_Rs.close();
                 m_Stmt.close();
                 m_OutputWriter.write(m_XMLContent);
                 m_OutputWriter.close();

                 //System.out.println(temp);

                 this.config = configs.xml(temp);
            } catch (ConfigurationException ce) {
                System.out.println("[Error] Something wrong reading configuration file");
                System.out.println("Try to find file " + ce.toString());
                System.out
                    .println("Please ensure the name of config file is \"test_list.xml\" and it's well constructed");
                ce.printStackTrace();
                System.exit(0);
            }
            catch(SQLException se) {
                System.out.println("[Error] SQL error:"+se.toString());
            }
            catch (IOException ie) {
                System.out.println("[Error] IO error");
            }
        }
    }
}


