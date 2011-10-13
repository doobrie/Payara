/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.security.cli;





import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.ActionReport;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.component.PerLookup;
import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.config.serverbeans.AuthRealm;
import com.sun.enterprise.config.serverbeans.Configs;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.SecurityService;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.security.auth.realm.RealmsManager;
import com.sun.enterprise.util.SystemPropertyConstants;
import org.glassfish.api.admin.*;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;


/**
 * List Auth Realms Command
 * Usage: list-auth-realms [--terse=false] [--echo=false] [--interactive=true] 
 *        [--host localhost] [--port 4848|4849] [--secure | -s] 
 *        [--user admin_user] [--passwordfile file_name] [target(Default server)]
 *
 * @author Nandini Ektare
 */

@Service(name="list-auth-realms")
@Scoped(PerLookup.class)
@CommandLock(CommandLock.LockType.NONE)
@I18n("list.auth.realm")
@ExecuteOn({RuntimeType.DAS})
@TargetType({CommandTarget.DAS,CommandTarget.STANDALONE_INSTANCE,
CommandTarget.CLUSTER, CommandTarget.CONFIG,CommandTarget.CLUSTERED_INSTANCE})
@RestEndpoints({
    @RestEndpoint(configBean=SecurityService.class,
        opType=RestEndpoint.OpType.GET, 
        path="list-auth-realms", 
        description="List Auth Realms")
})
public class ListAuthRealm implements AdminCommand {
    
    final private static LocalStringManagerImpl localStrings = 
        new LocalStringManagerImpl(ListAuthRealm.class);    

    @Param(name = "target", primary=true, optional = true, defaultValue =
    SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME)
    private String target;

    @Inject(name = ServerEnvironment.DEFAULT_INSTANCE_NAME)
    private Config config;

    @Inject
    private Configs configs;

    @Inject
    private Domain domain;
    @Inject
    private RealmsManager realmsManager;

    /**
     * Executes the command with the command parameters passed as Properties
     * where the keys are the paramter names and the values the parameter values
     *
     * @param context information
     */
    public void execute(AdminCommandContext context) {
        
        final ActionReport report = context.getActionReport();

        Config tmp = null;
        try {
            tmp = configs.getConfigByName(target);
        } catch (Exception ex) {
        }

        if (tmp != null) {
            config = tmp;
        }
        Server targetServer = domain.getServerNamed(target);
        if (targetServer!=null) {
            config = domain.getConfigNamed(targetServer.getConfigRef());
        }
        com.sun.enterprise.config.serverbeans.Cluster cluster = domain.getClusterNamed(target);
        if (cluster!=null) {
            config = domain.getConfigNamed(cluster.getConfigRef());
        }
        final SecurityService securityService = config.getSecurityService();

//        Enumeration realms = Realm.getRealmNames();
//
//        if ( realms == null || !realms.hasMoreElements()) {
//            //Create realms
//            realmsManager.createRealms(securityService);
//        }

        for (AuthRealm authRealm : securityService.getAuthRealm()) {
            ActionReport.MessagePart part = report.getTopMessagePart().addChild();
            part.setMessage(authRealm.getName());
        }
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
