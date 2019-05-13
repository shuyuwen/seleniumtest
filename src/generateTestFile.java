import javax.xml.*;
import javax.xml.transform.stream.*;
import javax.xml.validation.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.List;

public class generateTestFile {
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

    public static void main(String[] args) {
       try {
            String m_szDBURL = "jdbc:oracle:thin:@bej301445.cn.oracle.com:1521/pdbrobotjulie.sgtdbcluster.cn";
            String m_szDBUser = "yshu";
            String m_szDBPass = "syw1029";

            DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());
            Connection m_conn = DriverManager.getConnection (m_szDBURL,m_szDBUser, m_szDBPass);
            m_conn.setAutoCommit(false);

            Statement m_Stmt = m_conn.createStatement();
            ResultSet m_preRs = m_Stmt.executeQuery("select step from db_auto_testcase where id=1") ;
            m_preRs.next();
            String tests = "Select product, version, id,testname,step from db_auto_testcase where  id=2 or id=999999 or  '"+m_preRs.getString("step")+"' like '%'||testname||'%' order by id  ";

            ResultSet m_Rs = m_Stmt.executeQuery(tests);

            while (m_Rs.next())
            {
                java.sql.Clob m_Clob = m_Rs.getClob("step") ;
                String testname = m_Rs.getString("product")+m_Rs.getString("version")+"_"+m_Rs.getString("testname")+".xml" ;
                System.out.println(testname) ;

                FileWriter fileWriter = new FileWriter(testname) ;
                PrintWriter printWriter = new PrintWriter(fileWriter);

                String step = ClobToString(m_Clob) ;
                //System.out.println("...............................") ;
                //System.out.println(step) ;
                int pos = step.indexOf('<') ;
                while (pos != -1) {
                    int pos_end = step.indexOf('>', pos+1) ;
                    //System.out.println(pos+"  "+pos_end) ;
                    System.out.println(step.substring(pos, pos_end-pos+1)) ;
 printWriter.printf("%s\n", step.substring(pos, pos_end-pos+1)) ;
                    step = step.substring(pos_end+1) ;
                    pos = step.indexOf('<') ;
                    if (pos > 0) {
                        step = step.substring(pos) ;
                        pos = 0 ;
                    }
                    //System.out.println(pos+"  "+step.length()) ;
                }
                printWriter.close() ;
            }
            m_Rs.close();
            m_Stmt.close();
        } catch (Exception ce) {
            System.out.println("[Error] Something wrong " + ce.toString());
            System.exit(0);
        }
    }
}
