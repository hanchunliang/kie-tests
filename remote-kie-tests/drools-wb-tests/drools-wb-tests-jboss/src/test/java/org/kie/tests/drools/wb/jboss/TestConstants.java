package org.kie.tests.drools.wb.jboss;

import java.util.Properties;

public class TestConstants {
    
    public static final String USER = "admin";
    public static final String PASSWORD = "admin";
    public static final String MARY_USER = "mary";
    public static final String MARY_PASSWORD = "mary123@";
    public static final String JOHN_USER = "john";
    public static final String JOHN_PASSWORD = "john123@";
    
    public final static String projectVersion;
    static { 
        Properties testProps = new Properties();
        try {
            testProps.load(TestConstants.class.getResourceAsStream("/test.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize projectVersion property: " + e.getMessage(), e);
        }
        projectVersion = testProps.getProperty("project.version");
    }
    
}
