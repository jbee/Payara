/*
 * Copyright (c) [2019-2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
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
package fish.payara.nucleus.requesttracing.store.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import fish.payara.notification.requesttracing.RequestTrace;
import fish.payara.notification.requesttracing.RequestTraceSpan;

/**
 * Tests the correct behaviour of the {@link ReservoirTraceStorageStrategy}.
 *  
 * @author Jan Bernitt
 */
public class ReservoirTraceStorageStrategyTest extends AbstractStorageStrategyTest {

    private List<RequestTrace> traces;

    public ReservoirTraceStorageStrategyTest() {
        super(new ReservoirTraceStorageStrategy(1L));
    }

    @Before
    public void populateTraces() {
        traces = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            RequestTrace trace = createTrace();
            trace.getTraceSpans().getFirst().setEventName(String.valueOf(i));
            traces.add(trace);
        }
    }

    @Test
    public void test_random_chance_of_trace_removal() {
        final String errorMessage = "Incorrect trace removed, trace to be removed isn't random.";
        assertEquals(errorMessage, "5", strategy.getTraceForRemoval(traces, 0, null).getTraceSpans().getFirst().getEventName());
        assertEquals(errorMessage, "8", strategy.getTraceForRemoval(traces, 0, null).getTraceSpans().getFirst().getEventName());
        assertEquals(errorMessage, "7", strategy.getTraceForRemoval(traces, 0, null).getTraceSpans().getFirst().getEventName());
        assertEquals(errorMessage, "3", strategy.getTraceForRemoval(traces, 0, null).getTraceSpans().getFirst().getEventName());
        assertEquals(errorMessage, "4", strategy.getTraceForRemoval(traces, 0, null).getTraceSpans().getFirst().getEventName());
    }

}
