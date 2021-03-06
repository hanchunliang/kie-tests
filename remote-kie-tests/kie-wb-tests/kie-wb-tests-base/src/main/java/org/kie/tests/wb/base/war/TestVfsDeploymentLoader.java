package org.kie.tests.wb.base.war;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.jbpm.console.ng.bd.api.VFSDeploymentUnit;
import org.jbpm.console.ng.bd.api.Vfs;
import org.kie.internal.deployment.DeploymentService;
import org.kie.internal.deployment.DeploymentUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Startup
public class TestVfsDeploymentLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(TestVfsDeploymentLoader.class);

    @Inject
    @Vfs
    private DeploymentService deploymentService;
    
    @PostConstruct
    public void init() { 
        DeploymentUnit deploymentUnit = new VFSDeploymentUnit("test", "", "src/test/resources/repo/test/");
        logger.info("Initializing the '" + deploymentUnit.getIdentifier() + "' domain.");
        deploymentService.deploy(deploymentUnit);
    }
    
}
