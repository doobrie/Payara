/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.concurrent.runtime;

import com.sun.enterprise.config.serverbeans.Application;
import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.security.integration.AppServSecurityContext;
import com.sun.enterprise.transaction.api.JavaEETransactionManager;
import com.sun.enterprise.util.Utility;
import org.glassfish.api.invocation.ComponentInvocation;
import org.glassfish.api.invocation.InvocationManager;
import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.internal.deployment.Deployment;

import javax.enterprise.concurrent.ContextService;
import javax.transaction.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContextSetupProviderImpl implements ContextSetupProvider {

    private transient AppServSecurityContext securityContext;
    private transient InvocationManager invocationManager;
    private transient Deployment deployment;
    private transient Applications applications;
    private transient JavaEETransactionManager transactionManager;

    static final long serialVersionUID = -1095988075917755802L;

    static enum CONTEXT_TYPE {CLASSLOADING, SECURITY, NAMING, WORKAREA}

    private boolean classloading, security, naming, workArea;


    public ContextSetupProviderImpl(InvocationManager invocationManager,
                                    AppServSecurityContext securityContext,
                                    Deployment deployment,
                                    Applications applications,
                                    JavaEETransactionManager transactionManager,
                                    CONTEXT_TYPE... contextTypes) {
        this.invocationManager = invocationManager;
        this.securityContext = securityContext;
        this.deployment = deployment;
        this.applications = applications;
        this.transactionManager = transactionManager;
        for (CONTEXT_TYPE contextType: contextTypes) {
            switch(contextType) {
                case CLASSLOADING:
                    classloading = true;
                    break;
                case SECURITY:
                    security = true;
                    break;
                case NAMING:
                    naming = true;
                    break;
                case WORKAREA:;
                    workArea = true;
                    break;
            }
        }
    }

    @Override
    public ContextHandle saveContext(ContextService contextService) {
        return saveContext(contextService, null);
    }

    @Override
    public ContextHandle saveContext(ContextService contextService, Map<String, String> contextObjectProperties) {
        // Capture the current thread context
        ClassLoader contextClassloader = null;
        AppServSecurityContext currentSecurityContext = null;
        ComponentInvocation savedInvocation = null;
        if (classloading) {
            contextClassloader = Utility.getClassLoader();
        }
        if (security) {
            currentSecurityContext = securityContext.getCurrentSecurityContext();
        }
        ComponentInvocation currentInvocation = invocationManager.getCurrentInvocation();
        if (currentInvocation != null) {
            savedInvocation = createComponentInvocation(currentInvocation);
        }
        // TODO - support workarea propagation
        return new InvocationContext(savedInvocation, contextClassloader, currentSecurityContext);
    }

    @Override
    public ContextHandle setup(ContextHandle contextHandle) throws IllegalStateException {
        if (! (contextHandle instanceof InvocationContext)) {
            // TODO: log a warning message saying that we got passed an unknown ContextHandle
            return null;
        }
        InvocationContext handle = (InvocationContext) contextHandle;
        String appName = null;

        if (handle.getInvocation() != null) {
            appName = handle.getInvocation().getAppName();
        }
        // Check whether the application component submitting the task is still running. Throw IllegalStateException if not.
        if (!isApplicationEnabled(appName)) {
            throw new IllegalStateException("Module " + appName + " is disabled");
        }

        ClassLoader resetClassLoader = null;
        AppServSecurityContext resetSecurityContext = null;
        if (handle.getContextClassLoader() != null) {
            resetClassLoader = Utility.setContextClassLoader(handle.getContextClassLoader());
        }
        if (handle.getSecurityContext() != null) {
            resetSecurityContext = securityContext.getCurrentSecurityContext();
            securityContext.setCurrentSecurityContext(handle.getSecurityContext());
        }
        if (handle.getInvocation() != null) {
            ComponentInvocation invocation = handle.getInvocation();
            // Each invocation needs a ResourceTableKey that returns a unique hashCode for TransactionManager
            invocation.setResourceTableKey(new PairKey(invocation.getInstance(), Thread.currentThread()));
            invocationManager.preInvoke(invocation);
        }
        // Ensure that there is no existing transaction in the current thread
        if (transactionManager != null) {
            transactionManager.clearThreadTx();
        }
        return new InvocationContext(handle.getInvocation(), resetClassLoader, resetSecurityContext);
    }

    @Override
    public void reset(ContextHandle contextHandle) {
        if (! (contextHandle instanceof InvocationContext)) {
            // TODO: log a warning message saying that we got passed an unknown ContextHandle
            return;
        }
        InvocationContext handle = (InvocationContext) contextHandle;
        if (handle.getContextClassLoader() != null) {
            Utility.setContextClassLoader(handle.getContextClassLoader());
        }
        if (handle.getSecurityContext() != null) {
            securityContext.setCurrentSecurityContext(handle.getSecurityContext());
        }
        if (handle.getInvocation() != null) {
            invocationManager.postInvoke(((InvocationContext)contextHandle).getInvocation());
        }
        if (transactionManager != null) {
            // clean up after user if a transaction is still active
            // This is not required by the Concurrency spec
            Transaction transaction = transactionManager.getCurrentTransaction();
            if (transaction != null) {
                try {
                    int status = transaction.getStatus();
                    if (status == Status.STATUS_ACTIVE) {
                        transactionManager.commit();
                    } else if (status == Status.STATUS_MARKED_ROLLBACK) {
                        transactionManager.rollback();
                    }
                } catch (Exception ex) {
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, ex.toString());
                }
            }
          transactionManager.clearThreadTx();
        }
    }

    private boolean isApplicationEnabled(String appId) {
        if (appId != null) {
            Application app = applications.getApplication(appId);
            if (app != null)
                return deployment.isAppEnabled(app);
        }
        return false;
    }

    private ComponentInvocation createComponentInvocation(ComponentInvocation currInv) {
        ComponentInvocation newInv = new ComponentInvocation(
                currInv.getComponentId(),
                ComponentInvocation.ComponentInvocationType.SERVLET_INVOCATION,
                currInv.getContainer(),
                currInv.getAppName(),
                currInv.getModuleName()
        );
        newInv.instance = currInv.getInstance();
        if (naming) {
            newInv.setJNDIEnvironment(currInv.getJNDIEnvironment());
        }
        return newInv;
    }

    private void readObject(java.io.ObjectInputStream in) {
        //TODO- re-initialize these fields
        securityContext = null;
        invocationManager = null;
        deployment = null;
        applications = null;
        transactionManager = null;
    }

    private static class PairKey {
        private Object instance = null;
        private Thread thread = null;
        int hCode = 0;

        private PairKey(Object inst, Thread thr) {
            instance = inst;
            thread = thr;
            if (inst != null) {
                hCode = 7 * inst.hashCode();
            }
            if (thr != null) {
                hCode += thr.hashCode();
            }
        }

        @Override
        public int hashCode() {
            return hCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            boolean eq = false;
            if (obj != null && obj instanceof PairKey) {
                PairKey p = (PairKey)obj;
                if (instance != null) {
                    eq = (instance.equals(p.instance));
                } else {
                    eq = (p.instance == null);
                }

                if (eq) {
                    if (thread != null) {
                        eq = (thread.equals(p.thread));
                    } else {
                        eq = (p.thread == null);
                    }
                }
            }
            return eq;
        }
    }
}
