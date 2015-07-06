package org.nightscout.lasso;

import org.junit.Test;
import org.nightscout.lasso.test.RobolectricTestBase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class NightscoutMonitorTest extends RobolectricTestBase {


    @Test
    public void urgentAgeShouldBeLargerThanWarningAge() {
        assertThat(NightscoutMonitor.MISSED_DATA_URGENT_AGE.isLongerThan(NightscoutMonitor.MISSED_DATA_WARNING_AGE), is(true));
    }

}
