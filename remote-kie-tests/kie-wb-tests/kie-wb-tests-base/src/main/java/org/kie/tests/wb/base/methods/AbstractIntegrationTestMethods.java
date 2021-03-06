package org.kie.tests.wb.base.methods;

import static org.junit.Assert.*;
import static org.kie.tests.wb.base.methods.TestConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jbpm.process.audit.AuditLogService;
import org.jbpm.process.audit.CommandBasedAuditLogService;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.services.task.commands.GetTaskCommand;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.services.client.api.RemoteRestRuntimeFactory;
import org.kie.services.client.api.RemoteRuntimeEngineFactory;
import org.kie.services.client.api.RestRequestHelper;
import org.kie.services.client.api.command.RemoteRuntimeEngine;
import org.kie.tests.wb.base.test.objects.MyType;
import org.kie.tests.wb.base.test.objects.Person;
import org.kie.tests.wb.base.test.objects.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractIntegrationTestMethods {

    protected static Logger logger = LoggerFactory.getLogger(AbstractIntegrationTestMethods.class);
   
    protected final static int MAX_TRIES = 3;
    
    protected long findTaskId(long procInstId, List<TaskSummary> taskSumList) { 
        long taskId = -1;
        for( TaskSummary task : taskSumList ) { 
            if( task.getProcessInstanceId() == procInstId ) {
                taskId = task.getId();
            }
        }
        assertNotEquals("Could not determine taskId!", -1, taskId);
        return taskId;
    }
    
    protected TaskSummary findTaskSummary(long procInstId, List<TaskSummary> taskSumList) { 
        for( TaskSummary task : taskSumList ) { 
            if( task.getProcessInstanceId() == procInstId ) {
                return task;
            }
        }
        fail( "Unable to find task summary for process instance " + procInstId); 
        return null;
    }
 
    /**
     * Shared tests
     */
    
    protected void testExtraJaxbClassSerialization(RemoteRuntimeEngine engine) {
        
        /**
         * MyType
         */
        testParamSerialization(engine, new MyType("variable", 29));
        
        /**
         * Float
         */
        testParamSerialization(engine, new Float(23.01));
        
        /**
         * Float []
         */
        testParamSerialization(engine, new Float [] { 39.391f });
    }
    
    protected void testParamSerialization(RemoteRuntimeEngine  engine, Object param) { 
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("myobject", param);
        KieSession ksession = engine.getKieSession();
        logger.info("Sending start-process-request");
        ProcessInstance procInst = ksession.startProcess(OBJECT_VARIABLE_PROCESS_ID, parameters);
        assertNotNull( "No process instance returned!", procInst);
        long procInstId = procInst.getId();
        
        /**
         * Check that MyType was correctly deserialized on server side
         */
        List<VariableInstanceLog> varLogList = engine.getAuditLogService().findVariableInstancesByName("type", false);
        VariableInstanceLog thisProcInstVarLog = null;
        for( VariableInstanceLog varLog : varLogList ) {
            if( varLog.getProcessInstanceId() == procInstId ) { 
                thisProcInstVarLog = varLog;
            }
        }
        assertNotNull( "No VariableInstanceLog found!", thisProcInstVarLog );
        assertEquals( "type", thisProcInstVarLog.getVariableId() );
        assertEquals( "De/serialization of Kjar type did not work.", param.getClass().getName(), thisProcInstVarLog.getValue() );
        
        // Double check for BZ-1085267
        varLogList = engine.getAuditLogService().findVariableInstances(procInstId, "type");
        assertNotNull("No variable log list retrieved!", varLogList);
        assertTrue("Variable log list is empty!", varLogList.size() > 0);
    }
    
    public static void runRuleTaskProcess(KieSession ksession, AuditLogService auditLogService) { 
        // Setup facts
        Person person = new Person("guest", "Dluhoslav Chudobny");
        person.setAge(25); // >= 18
        Request request = new Request("1");
        request.setPersonId("guest");
        request.setAmount(500); // < 1000
        
        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("request", request);
        params.put("person", person);
       
        // Start process
        ProcessInstance pi = ksession.startProcess(TestConstants.RULE_TASK_PROCESS_ID, params);
        assertNotNull( "No Process instance returned!", pi);
        ksession.fireAllRules();
        
        // Check
//        assertEquals("Poor customer", ((Request)ksession.getObject(factHandle)).getInvalidReason());
        assertNull(ksession.getProcessInstance(pi.getId()));
        
        List<VariableInstanceLog> varLogs = auditLogService.findVariableInstancesByName("requestReason", false);
        for( VariableInstanceLog varLog : varLogs ) { 
            if( varLog.getProcessInstanceId() == pi.getId() ) { 
                assertEquals( "Poor customer", varLog.getValue() );
            }
        }
    }
   
    public void runHumanTaskGroupIdTest(RuntimeEngine krisRuntimeEngine, RuntimeEngine johnRuntimeEngine, RuntimeEngine maryRuntimeEngine) {

        KieSession ksession = krisRuntimeEngine.getKieSession();

        // start a new process instance
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "krisv");
        params.put("reason", "Yearly performance evaluation");
        ProcessInstance processInstance = ksession.startProcess(EVALUTAION_PROCESS_ID, params);
        assertNotNull( "Null process instance!", processInstance);
        long procInstId = processInstance.getId();
        System.out.println("Process started ...");

        // complete Self Evaluation
        {
            String user = "krisv";
            TaskService taskService = krisRuntimeEngine.getTaskService();
            List<TaskSummary> tasks = taskService.getTasksAssignedAsPotentialOwner(user, "en-UK");
            TaskSummary task = getProcessInstanceTask(tasks, procInstId);
            assertNotNull("Unable to find " + user + "'s task", task);
            System.out.println("'" + user + "' completing task " + task.getName() + ": " + task.getDescription());
            taskService.start(task.getId(), user);
            Map<String, Object> results = new HashMap<String, Object>();
            results.put("performance", "exceeding");
            taskService.complete(task.getId(), user, results);
        }

        // john from HR
        { 
            String user = "john";
            TaskService taskService = johnRuntimeEngine.getTaskService();
            List<TaskSummary> tasks = taskService.getTasksAssignedAsPotentialOwner(user, "en-UK");
            TaskSummary task = getProcessInstanceTask(tasks, procInstId);
            assertNotNull("Unable to find " + user + "'s task", task);
            System.out.println("'john' completing task " + task.getName() + ": " + task.getDescription());
            taskService.start(task.getId(), user);
            Map<String, Object> results = new HashMap<String, Object>();
            results.put("performance", "acceptable");
            taskService.complete(task.getId(), user, results);
        }

        // mary from PM
        {
            String user = "mary";
            TaskService taskService = maryRuntimeEngine.getTaskService();
            List<TaskSummary> tasks = taskService.getTasksAssignedAsPotentialOwner(user, "en-UK");
            TaskSummary task = getProcessInstanceTask(tasks, procInstId);
            assertNotNull("Unable to find " + user + "'s task", task);
            System.out.println("'" + user + "' completing task " + task.getName() + ": " + task.getDescription());
            taskService.start(task.getId(), user);
            Map<String, Object> results = new HashMap<String, Object>();
            results.put("performance", "outstanding");
            taskService.complete(task.getId(), user,  results);
        }

        //  assertProcessInstanceCompleted(processInstance.getId(), ksession);
        System.out.println("Process instance completed");
    }

    private TaskSummary getProcessInstanceTask(List<TaskSummary> tasks, long procInstId) { 
        TaskSummary result = null;
        for( TaskSummary task : tasks ) { 
            if( task.getProcessInstanceId() == procInstId ) { 
                result = task;
                break;
            }
         }
        return result;
    }

}
